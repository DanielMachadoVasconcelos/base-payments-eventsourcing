package lab.payments.payment_session.infrastructure;

import static lab.common.Json.*;
import static lab.payments.payment_session.infrastructure.PurchasePayloadDocuments.*;

import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.Base64;
import java.util.List;
import java.util.Map;
import lab.common.*;
import lab.payments.payment_session.application.PurchasePayloadStore;
import lab.payments.payment_session.domain.*;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

@Repository
@RequiredArgsConstructor
public class DynamoDbPurchasePayloadStore implements PurchasePayloadStore {
    private final Dynamo dynamo;
    private final JsonDocumentCodec json;

    @Override
    public String store(MerchantId merchantId, Purchase purchase) {
        byte[] content =
                json.serialize(PurchaseDocument.from(purchase)).getBytes(StandardCharsets.UTF_8);
        var upload = PurchasePayloadUpload.start(merchantId, content);
        createManifest(upload);
        for (int index = 0; index < upload.totalChunks(); index++) {
            storeChunk(upload, index);
        }
        markUploadComplete(upload.address().partitionKey());
        return upload.address().payloadId();
    }

    @Override
    public Purchase load(MerchantId merchantId, String payloadId) {
        var address = new PurchasePayloadUpload.Address(merchantId, payloadId);
        var manifest = loadCompleteManifest(address.partitionKey());
        var chunks = dynamo.query(address.partitionKey(), "CHUNK#");
        String document = reconstructPayload(chunks, manifest);
        return json.fromDocument(json.deserializeObject(document), PurchaseDocument.class)
                .toDomain();
    }

    private void createManifest(PurchasePayloadUpload upload) {
        dynamo.client.putItem(
                request ->
                        request.tableName(dynamo.table)
                                .item(
                                        dynamo.item(
                                                upload.address().partitionKey(),
                                                "MANIFEST",
                                                json.toDocument(Manifest.from(upload)))));
    }

    private void storeChunk(PurchasePayloadUpload upload, int index) {
        int start = index * PurchasePayloadUpload.CHUNK_BYTES;
        int end = Math.min(upload.content().length, start + PurchasePayloadUpload.CHUNK_BYTES);
        String encoded =
                Base64.getEncoder()
                        .encodeToString(Arrays.copyOfRange(upload.content(), start, end));
        var chunk = new Chunk(encoded, index);
        dynamo.client.putItem(
                request ->
                        request.tableName(dynamo.table)
                                .item(
                                        dynamo.item(
                                                upload.address().partitionKey(),
                                                String.format("CHUNK#%010d", index),
                                                json.toDocument(chunk))));
    }

    private void markUploadComplete(String partitionKey) {
        dynamo.client.updateItem(
                request ->
                        request.tableName(dynamo.table)
                                .key(Dynamo.key(partitionKey, "MANIFEST"))
                                .updateExpression("SET #status = :complete")
                                .conditionExpression("#status = :uploading")
                                .expressionAttributeNames(Map.of("#status", "status"))
                                .expressionAttributeValues(
                                        Map.of(
                                                ":complete",
                                                Dynamo.stringAttribute("COMPLETE"),
                                                ":uploading",
                                                Dynamo.stringAttribute("UPLOADING"))));
    }

    private Manifest loadCompleteManifest(String partitionKey) {
        var document = dynamo.get(partitionKey, "MANIFEST");
        if (document == null || !"COMPLETE".equals(stringValue(document, "status"))) {
            throw new PaymentNotFoundException(
                    "PURCHASE_PAYLOAD_NOT_AVAILABLE",
                    "The complete purchase payload is not available");
        }
        return json.fromDocument(document, Manifest.class);
    }

    private String reconstructPayload(List<Map<String, Object>> chunks, Manifest manifest) {
        if (chunks.size() != manifest.totalChunks()) {
            throw new PaymentPersistenceFailure("Purchase payload is incomplete");
        }
        var bytes = new ByteArrayOutputStream();
        chunks.forEach(
                chunk -> bytes.writeBytes(Base64.getDecoder().decode(stringValue(chunk, "chunk"))));
        String document = bytes.toString(StandardCharsets.UTF_8);
        if (!sha256(document).equals(manifest.hash())) {
            throw new PaymentPersistenceFailure("Purchase payload checksum does not match");
        }
        return document;
    }
}
