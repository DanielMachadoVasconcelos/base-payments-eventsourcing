package lab.payments.payment_session.entry;

import lab.common.ApiValues;
import lab.payments.payment_session.application.ChangePurchaseService;
import lab.payments.payment_session.application.CreatePaymentSessionService;
import lab.payments.payment_session.application.GetPaymentSessionService;
import lab.payments.payment_session.application.SessionWriteContext;
import lab.payments.payment_session.domain.MerchantId;
import lab.payments.payment_session.domain.PaymentSessionId;
import lab.payments.payment_session.domain.PaymentSessionIdentity;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/v1/sessions")
@RequiredArgsConstructor
public class PaymentSessionController {
    private final PaymentSessionRequestMapper requestMapper;
    private final PaymentSessionResponseMapper responseMapper;
    private final CreatePaymentSessionService createPaymentSessionService;
    private final ChangePurchaseService changePurchaseService;
    private final GetPaymentSessionService getPaymentSessionService;

    @PostMapping
    public ResponseEntity<PaymentSessionResponse> create(
            @RequestHeader("X-Merchant-Id") String merchantId,
            @RequestHeader("Idempotency-Key") String idempotencyKey,
            @RequestBody CreatePaymentSessionRequest request) {
        var command = requestMapper.create(merchantId, idempotencyKey, request);
        var session = createPaymentSessionService.create(command);
        return responseMapper.created(session);
    }

    @GetMapping("/{sessionId}")
    public ResponseEntity<PaymentSessionResponse> get(
            @RequestHeader("X-Merchant-Id") String merchantId, @PathVariable String sessionId) {
        var session =
                getPaymentSessionService.get(
                        new PaymentSessionIdentity(
                                new MerchantId(ApiValues.merchant(merchantId)),
                                new PaymentSessionId(sessionId)));
        return responseMapper.ok(session);
    }

    // Explicit HTTP binding: headers, route identity and request body are distinct sources.
    @SuppressWarnings("checkstyle:ParameterNumber")
    @PutMapping("/{sessionId}")
    public ResponseEntity<PaymentSessionResponse> change(
            @RequestHeader("X-Merchant-Id") String merchantId,
            @PathVariable String sessionId,
            @RequestHeader("Idempotency-Key") String idempotencyKey,
            @RequestHeader("If-Match") String expectedVersion,
            @RequestBody ChangePurchaseRequest request) {
        var identity =
                new PaymentSessionIdentity(
                        new MerchantId(ApiValues.merchant(merchantId)),
                        new PaymentSessionId(sessionId));
        var context =
                new SessionWriteContext(
                        identity,
                        ApiValues.idempotency(idempotencyKey),
                        ApiValues.expectedVersion(expectedVersion));
        var command = requestMapper.change(context, request);
        var session = changePurchaseService.change(command);
        return responseMapper.ok(session);
    }
}
