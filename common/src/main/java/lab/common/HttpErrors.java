package lab.common;

import java.util.UUID;
import org.springframework.http.*;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.MissingRequestHeaderException;
import org.springframework.web.bind.annotation.*;
import software.amazon.awssdk.core.exception.SdkException;

@RestControllerAdvice
public class HttpErrors {
    @ExceptionHandler(PaymentDomainException.class)
    public ResponseEntity<ProblemDetail> domain(PaymentDomainException e) {
        return error(e.httpStatus(), e.code(), e.getMessage());
    }

    @ExceptionHandler({
        IllegalArgumentException.class,
        HttpMessageNotReadableException.class,
        MissingRequestHeaderException.class
    })
    public ResponseEntity<ProblemDetail> invalid(Exception e) {
        return error(400, "INVALID_REQUEST", "Malformed request or missing required header");
    }

    @ExceptionHandler(SdkException.class)
    public ResponseEntity<ProblemDetail> storage(SdkException e) {
        return error(
                503,
                "STORAGE_UNAVAILABLE",
                "Admission or completion is uncertain; retry using the same Idempotency-Key");
    }

    public static ResponseEntity<ProblemDetail> error(int status, String code, String message) {
        var detail = ProblemDetail.forStatusAndDetail(HttpStatusCode.valueOf(status), message);
        detail.setProperty("code", code);
        detail.setProperty("correlationId", UUID.randomUUID().toString());
        return ResponseEntity.status(status).body(detail);
    }
}
