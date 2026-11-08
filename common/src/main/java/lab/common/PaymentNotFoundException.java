package lab.common;

public class PaymentNotFoundException extends PaymentDomainException {
    public PaymentNotFoundException(String code, String message) {
        super(404, code, message);
    }
}
