package lab.common;

import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.ObjectMapper;

/** Uses the ObjectMapper configured and supplied by Spring Boot. */
@Component
@RequiredArgsConstructor
public class JsonDocumentCodec {
    private static final TypeReference<Map<String, Object>> DOCUMENT_TYPE =
            new TypeReference<>() {};
    private final ObjectMapper objectMapper;

    public Map<String, Object> toDocument(Object value) {
        return objectMapper.convertValue(value, DOCUMENT_TYPE);
    }

    public <T> T fromDocument(Map<String, Object> document, Class<T> type) {
        return objectMapper.convertValue(Dynamo.clean(document), type);
    }

    public String serialize(Object document) {
        try {
            return objectMapper.writeValueAsString(document);
        } catch (RuntimeException exception) {
            throw new PaymentPersistenceFailure(
                    "Could not serialize a payment document", exception);
        }
    }

    public Map<String, Object> deserializeObject(String document) {
        try {
            return objectMapper.readValue(document, DOCUMENT_TYPE);
        } catch (RuntimeException exception) {
            throw new PaymentPersistenceFailure(
                    "Could not deserialize a payment document", exception);
        }
    }
}
