package lab.payments.payment_session.infrastructure;

import java.util.Map;
import lab.common.JsonDocumentCodec;
import lab.payments.payment_session.domain.PaymentSessionEvent;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class PaymentSessionDocumentMapper {
    private final JsonDocumentCodec json;

    public Map<String, Object> toEventDocument(PaymentSessionEvent event) {
        return json.toDocument(PaymentSessionDocuments.EventDocument.from(event));
    }

    public PaymentSessionEvent toEvent(Map<String, Object> document) {
        return json.fromDocument(document, PaymentSessionDocuments.EventDocument.class).toDomain();
    }
}
