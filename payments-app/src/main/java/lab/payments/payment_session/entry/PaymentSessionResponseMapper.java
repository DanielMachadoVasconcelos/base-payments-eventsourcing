package lab.payments.payment_session.entry;

import lab.payments.configuration.PaymentMappingConfiguration;
import lab.payments.payment_session.application.PaymentSessionView;
import lab.payments.payment_session.domain.Purchase;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

@Mapper(config = PaymentMappingConfiguration.class)
public interface PaymentSessionResponseMapper {
    PaymentSessionResponse toResponse(PaymentSessionView session);

    @Mapping(target = "merchantReference", defaultValue = "")
    PurchaseResponse toPurchaseResponse(Purchase purchase);

    default ResponseEntity<PaymentSessionResponse> created(PaymentSessionView session) {
        return response(session, HttpStatus.CREATED);
    }

    default ResponseEntity<PaymentSessionResponse> ok(PaymentSessionView session) {
        return response(session, HttpStatus.OK);
    }

    private ResponseEntity<PaymentSessionResponse> response(
            PaymentSessionView session, HttpStatus status) {
        return ResponseEntity.status(status)
                .eTag(Long.toString(session.version()))
                .body(toResponse(session));
    }
}
