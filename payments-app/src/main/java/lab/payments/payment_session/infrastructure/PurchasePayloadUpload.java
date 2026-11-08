package lab.payments.payment_session.infrastructure;

import static lab.common.Json.sha256;

import java.nio.charset.StandardCharsets;
import java.util.UUID;
import lab.payments.payment_session.domain.MerchantId;

/** Request-local upload descriptor; its bytes are never stored on a singleton component. */
record PurchasePayloadUpload(Address address, byte[] content, String checksum) {
    static final int CHUNK_BYTES = 200 * 1024;

    record Address(MerchantId merchantId, String payloadId) {
        String partitionKey() {
            return "M#" + merchantId.value() + "#PAYLOAD#" + payloadId;
        }
    }

    static PurchasePayloadUpload start(MerchantId merchantId, byte[] content) {
        return new PurchasePayloadUpload(
                new Address(merchantId, UUID.randomUUID().toString()),
                content,
                sha256(new String(content, StandardCharsets.UTF_8)));
    }

    int totalChunks() {
        return content.length == 0 ? 1 : (content.length - 1) / CHUNK_BYTES + 1;
    }
}
