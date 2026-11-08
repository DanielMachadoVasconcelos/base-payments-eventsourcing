package lab.common;

public abstract class PaymentDomainException extends RuntimeException {
    private final int httpStatus;
    private final String code;

    protected PaymentDomainException(int httpStatus, String code, String message) {
        super(message);
        this.httpStatus = httpStatus;
        this.code = code;
    }

    public int httpStatus() {
        return httpStatus;
    }

    public String code() {
        return code;
    }
}
