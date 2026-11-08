package lab.common;

public class PaymentPersistenceFailure extends RuntimeException {
    public PaymentPersistenceFailure(String message, Throwable cause) {
        super(message, cause);
    }

    public PaymentPersistenceFailure(String message) {
        super(message);
    }
}
