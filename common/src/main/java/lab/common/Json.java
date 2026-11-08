package lab.common;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.TreeMap;
import java.util.stream.Collectors;

/** Helpers for infrastructure documents. Domain state uses typed records. */
public final class Json {
    private Json() {}

    public static Map<String, Object> copyDocument(Map<String, Object> document) {
        return new LinkedHashMap<>(document);
    }

    public static String stringValue(Map<String, Object> document, String key) {
        return Objects.toString(document.get(key), "");
    }

    public static long longValue(Map<String, Object> document, String key) {
        return ((Number) document.getOrDefault(key, 0L)).longValue();
    }

    public static boolean booleanValue(Map<String, Object> document, String key) {
        return Boolean.TRUE.equals(document.get(key));
    }

    @SuppressWarnings("unchecked")
    public static Map<String, Object> objectValue(Map<String, Object> document, String key) {
        return (Map<String, Object>) document.getOrDefault(key, Map.of());
    }

    @SuppressWarnings("unchecked")
    public static List<Map<String, Object>> objectList(Map<String, Object> document, String key) {
        return (List<Map<String, Object>>) document.getOrDefault(key, List.of());
    }

    public static String fingerprint(Object value) {
        return sha256(canonicalText(canonical(value)));
    }

    public static String sha256(String value) {
        try {
            byte[] digest =
                    MessageDigest.getInstance("SHA-256")
                            .digest(value.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest);
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException(exception);
        }
    }

    public static Object canonical(Object value) {
        if (value instanceof Map<?, ?> document) {
            var sortedDocument = new TreeMap<String, Object>();
            document.forEach(
                    (key, entryValue) -> sortedDocument.put(key.toString(), canonical(entryValue)));
            return sortedDocument;
        }
        if (value instanceof List<?> values) {
            return values.stream().map(Json::canonical).toList();
        }
        if (value instanceof Number number) {
            return new BigDecimal(number.toString()).stripTrailingZeros();
        }
        return value;
    }

    private static String canonicalText(Object value) {
        if (value instanceof Map<?, ?> document) {
            return document.entrySet().stream()
                    .map(entry -> entry.getKey() + ":" + canonicalText(entry.getValue()))
                    .collect(Collectors.joining(",", "{", "}"));
        }
        if (value instanceof List<?> values) {
            return values.stream()
                    .map(Json::canonicalText)
                    .collect(Collectors.joining(",", "[", "]"));
        }
        return String.valueOf(value);
    }
}
