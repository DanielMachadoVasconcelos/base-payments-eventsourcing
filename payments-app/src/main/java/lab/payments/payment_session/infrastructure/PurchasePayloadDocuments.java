package lab.payments.payment_session.infrastructure;

import java.util.Map;
import java.util.Objects;
import lab.payments.payment_session.domain.Purchase;
import lombok.Builder;

final class PurchasePayloadDocuments {
    private PurchasePayloadDocuments() {}

    record PurchaseDocument(
            long amountMinor,
            String currency,
            String merchantReference,
            Map<String, Object> attributes) {
        static PurchaseDocument from(Purchase purchase) {
            return new PurchaseDocument(
                    purchase.amountMinor(),
                    purchase.currency(),
                    Objects.toString(purchase.merchantReference(), ""),
                    purchase.attributes());
        }

        Purchase toDomain() {
            return new Purchase(amountMinor, currency, merchantReference, attributes);
        }
    }

    @Builder
    record Manifest(String status, String payloadId, int totalChunks, int totalBytes, String hash) {
        static Manifest from(PurchasePayloadUpload upload) {
            return builder()
                    .status("UPLOADING")
                    .payloadId(upload.address().payloadId())
                    .totalChunks(upload.totalChunks())
                    .totalBytes(upload.content().length)
                    .hash(upload.checksum())
                    .build();
        }
    }

    record Chunk(String chunk, int index) {}
}
