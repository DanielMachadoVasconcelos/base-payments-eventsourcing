package lab.common;

public class PaymentExpiredException extends PaymentDomainException {
    public PaymentExpiredException(String code, String message) {
        super(410, code, message);
    }
}
