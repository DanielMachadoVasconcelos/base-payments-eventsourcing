package lab.common;

public class PaymentConflictException extends PaymentDomainException {
    public PaymentConflictException(String code, String message) {
        super(409, code, message);
    }
}
