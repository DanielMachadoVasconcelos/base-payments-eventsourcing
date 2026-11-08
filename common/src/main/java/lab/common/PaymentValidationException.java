package lab.common;

public class PaymentValidationException extends PaymentDomainException {
    public PaymentValidationException(String code, String message) {
        super(400, code, message);
    }
}
