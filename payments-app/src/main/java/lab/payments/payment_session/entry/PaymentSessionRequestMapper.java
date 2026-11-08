package lab.payments.payment_session.entry;

import lab.payments.configuration.PaymentMappingConfiguration;
import lab.payments.payment_session.application.ChangePurchaseCommand;
import lab.payments.payment_session.application.CreatePaymentSessionCommand;
import lab.payments.payment_session.application.SessionWriteContext;
import lab.payments.payment_session.domain.Purchase;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

@Mapper(config = PaymentMappingConfiguration.class, uses = PaymentSessionInputConversions.class)
public interface PaymentSessionRequestMapper {
    @Mapping(target = "purchase", source = "request")
    @Mapping(
            target = "idempotencyKey",
            source = "idempotencyKey",
            qualifiedByName = "validatedIdempotencyKey")
    CreatePaymentSessionCommand create(
            String merchantId, String idempotencyKey, CreatePaymentSessionRequest request);

    @Mapping(target = "purchase", source = "request")
    ChangePurchaseCommand change(SessionWriteContext context, ChangePurchaseRequest request);

    @Mapping(target = "currency", qualifiedByName = "validatedCurrency")
    @Mapping(target = "merchantReference", defaultValue = "")
    @Mapping(target = "attributes", defaultExpression = "java(java.util.Map.of())")
    Purchase toPurchase(CreatePaymentSessionRequest request);

    @Mapping(target = "currency", qualifiedByName = "validatedCurrency")
    @Mapping(target = "merchantReference", defaultValue = "")
    @Mapping(target = "attributes", defaultExpression = "java(java.util.Map.of())")
    Purchase toPurchase(ChangePurchaseRequest request);
}
