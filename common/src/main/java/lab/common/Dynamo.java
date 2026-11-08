package lab.common;

import static lab.common.Json.*;

import jakarta.annotation.PreDestroy;
import java.net.URI;
import java.time.Duration;
import java.util.*;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import software.amazon.awssdk.auth.credentials.*;
import software.amazon.awssdk.enhanced.dynamodb.*;
import software.amazon.awssdk.http.urlconnection.UrlConnectionHttpClient;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.dynamodb.DynamoDbClient;
import software.amazon.awssdk.services.dynamodb.model.*;

@Component
public class Dynamo {
    public final DynamoDbClient client;
    public final DynamoDbEnhancedClient enhanced;
    public final String table;
    private final JsonDocumentCodec json;
    private static final Set<String> NUMERIC =
            Set.of("version", "leaseEpoch", "leaseUntil", "attempts", "nextAt");
    private static final Set<String> STRINGS =
            Set.of("state", "status", "phase", "leaseOwner", "ownerSession", "lifecycle");

    public Dynamo(
            @Value("${lab.endpoint}") String endpoint,
            @Value("${lab.table}") String table,
            JsonDocumentCodec json) {
        URI uri = URI.create(endpoint);
        if (!"http".equals(uri.getScheme()) || uri.getHost().endsWith("amazonaws.com"))
            throw new PaymentPersistenceFailure(
                    "This lab requires an explicit local HTTP DynamoDB endpoint");
        client =
                DynamoDbClient.builder()
                        .endpointOverride(uri)
                        .region(Region.US_EAST_1)
                        .credentialsProvider(
                                StaticCredentialsProvider.create(
                                        AwsBasicCredentials.create("local", "local")))
                        .httpClientBuilder(
                                UrlConnectionHttpClient.builder()
                                        .connectionTimeout(Duration.ofSeconds(2))
                                        .socketTimeout(Duration.ofSeconds(5)))
                        .overrideConfiguration(
                                configuration ->
                                        configuration.apiCallTimeout(Duration.ofSeconds(12)))
                        .build();
        enhanced = DynamoDbEnhancedClient.builder().dynamoDbClient(client).build();
        this.table = table;
        this.json = json;
        provision(table);
    }

    public static AttributeValue stringAttribute(String v) {
        return AttributeValue.builder().s(v).build();
    }

    public static AttributeValue numberAttribute(long v) {
        return AttributeValue.builder().n(Long.toString(v)).build();
    }

    public static Map<String, AttributeValue> key(String partitionKey, String sortKey) {
        return Map.of("PK", stringAttribute(partitionKey), "SK", stringAttribute(sortKey));
    }

    public void provision(String name) {
        try {
            client.createTable(
                    builder ->
                            builder.tableName(name)
                                    .billingMode(BillingMode.PAY_PER_REQUEST)
                                    .keySchema(
                                            KeySchemaElement.builder()
                                                    .attributeName("PK")
                                                    .keyType(KeyType.HASH)
                                                    .build(),
                                            KeySchemaElement.builder()
                                                    .attributeName("SK")
                                                    .keyType(KeyType.RANGE)
                                                    .build())
                                    .attributeDefinitions(
                                            AttributeDefinition.builder()
                                                    .attributeName("PK")
                                                    .attributeType(ScalarAttributeType.S)
                                                    .build(),
                                            AttributeDefinition.builder()
                                                    .attributeName("SK")
                                                    .attributeType(ScalarAttributeType.S)
                                                    .build(),
                                            AttributeDefinition.builder()
                                                    .attributeName("workPK")
                                                    .attributeType(ScalarAttributeType.S)
                                                    .build(),
                                            AttributeDefinition.builder()
                                                    .attributeName("workSK")
                                                    .attributeType(ScalarAttributeType.S)
                                                    .build())
                                    .globalSecondaryIndexes(
                                            GlobalSecondaryIndex.builder()
                                                    .indexName("work")
                                                    .keySchema(
                                                            KeySchemaElement.builder()
                                                                    .attributeName("workPK")
                                                                    .keyType(KeyType.HASH)
                                                                    .build(),
                                                            KeySchemaElement.builder()
                                                                    .attributeName("workSK")
                                                                    .keyType(KeyType.RANGE)
                                                                    .build())
                                                    .projection(
                                                            Projection.builder()
                                                                    .projectionType(
                                                                            ProjectionType
                                                                                    .KEYS_ONLY)
                                                                    .build())
                                                    .build()));
        } catch (ResourceInUseException ignored) {
            /* Verify rather than replace an existing table. */
        }
        client.waiter().waitUntilTableExists(builder -> builder.tableName(name));
        var description = client.describeTable(builder -> builder.tableName(name)).table();
        if (description.keySchema().stream()
                        .noneMatch(
                                k -> k.attributeName().equals("PK") && k.keyType() == KeyType.HASH)
                || description.keySchema().stream()
                        .noneMatch(
                                k -> k.attributeName().equals("SK") && k.keyType() == KeyType.RANGE)
                || description.globalSecondaryIndexes().stream()
                        .noneMatch(i -> i.indexName().equals("work")))
            throw new IllegalStateException("Incompatible existing DynamoDB schema: " + name);
    }

    public Map<String, AttributeValue> item(
            String partitionKey, String sortKey, Map<String, Object> data) {
        var result = new HashMap<>(key(partitionKey, sortKey));
        result.put("data", stringAttribute(json.serialize(data)));
        NUMERIC.forEach(
                k -> {
                    if (data.containsKey(k)) result.put(k, numberAttribute(longValue(data, k)));
                });
        STRINGS.forEach(
                k -> {
                    if (data.containsKey(k)) result.put(k, stringAttribute(stringValue(data, k)));
                });
        if (data.containsKey("workType")) {
            int bucket = Integer.parseInt(sha256(partitionKey).substring(0, 2), 16) % 16;
            result.put(
                    "workPK",
                    stringAttribute("WORK#" + stringValue(data, "workType") + "#" + bucket));
            result.put(
                    "workSK",
                    stringAttribute(
                            String.format(
                                    "%020d#%s#%s",
                                    longValue(data, "nextAt"), partitionKey, sortKey)));
        }
        return result;
    }

    private Map<String, Object> decode(Map<String, AttributeValue> item) {
        if (item == null || item.isEmpty()) return null;
        var doc = json.deserializeObject(item.get("data").s());
        NUMERIC.forEach(
                k -> {
                    if (item.containsKey(k)) doc.put(k, Long.parseLong(item.get(k).n()));
                });
        STRINGS.forEach(
                k -> {
                    if (item.containsKey(k)) doc.put(k, item.get(k).s());
                });
        doc.put("_pk", item.get("PK").s());
        doc.put("_sk", item.get("SK").s());
        return doc;
    }

    public Map<String, Object> get(String partitionKey, String sortKey) {
        return get(table, partitionKey, sortKey);
    }

    public Map<String, Object> get(String name, String partitionKey, String sortKey) {
        return decode(
                client.getItem(
                                builder ->
                                        builder.tableName(name)
                                                .key(key(partitionKey, sortKey))
                                                .consistentRead(true))
                        .item());
    }

    public Map<String, Object> typed(String partitionKey, String sortKey) {
        var value =
                enhanced.table(table, TableSchema.fromBean(StoredDocument.class))
                        .getItem(
                                builder ->
                                        builder.key(
                                                        Key.builder()
                                                                .partitionValue(partitionKey)
                                                                .sortValue(sortKey)
                                                                .build())
                                                .consistentRead(true));
        return value == null ? null : json.deserializeObject(value.getData());
    }

    public List<Map<String, Object>> query(String partitionKey, String prefix) {
        return query(table, partitionKey, prefix);
    }

    public List<Map<String, Object>> query(String name, String partitionKey, String prefix) {
        var result = new ArrayList<Map<String, Object>>();
        client.queryPaginator(
                        builder ->
                                builder.tableName(name)
                                        .consistentRead(true)
                                        .keyConditionExpression(
                                                "PK = :pk AND begins_with(SK, :prefix)")
                                        .expressionAttributeValues(
                                                Map.of(
                                                        ":pk",
                                                        stringAttribute(partitionKey),
                                                        ":prefix",
                                                        stringAttribute(prefix))))
                .items()
                .forEach(i -> result.add(decode(i)));
        return result;
    }

    public List<Map<String, Object>> due(String type, long now) {
        var result = new ArrayList<Map<String, Object>>();
        for (int bucket = 0; bucket < 16; bucket++) {
            String workPk = "WORK#" + type + "#" + bucket;
            client.queryPaginator(
                            builder ->
                                    builder.tableName(table)
                                            .indexName("work")
                                            .limit(25)
                                            .keyConditionExpression(
                                                    "workPK = :pk AND workSK <= :end")
                                            .expressionAttributeValues(
                                                    Map.of(
                                                            ":pk",
                                                            stringAttribute(workPk),
                                                            ":end",
                                                            stringAttribute(
                                                                    String.format(
                                                                            "%020d#\uffff", now)))))
                    .items()
                    .forEach(
                            i -> {
                                var current = get(i.get("PK").s(), i.get("SK").s());
                                if (current != null) result.add(current);
                            });
        }
        return result;
    }

    public TransactWriteItem put(Put request) {
        return TransactWriteItem.builder().put(request).build();
    }

    public static Map<String, Object> clean(Map<String, Object> data) {
        var result = new LinkedHashMap<>(data);
        result.remove("_pk");
        result.remove("_sk");
        return result;
    }

    public void transaction(List<TransactWriteItem> actions, String token) {
        if (actions.size() > 100)
            throw new PaymentValidationException("TRANSACTION_SIZE", "Too many DynamoDB actions");
        int bytes = 0;
        var keys = new HashSet<String>();
        for (var action : actions) {
            if (action.put() != null) {
                var i = action.put().item();
                String key = i.get("PK").s() + "\n" + i.get("SK").s();
                if (!keys.add(key))
                    throw new IllegalStateException("Duplicate transaction item: " + key);
                int size =
                        i.entrySet().stream()
                                .mapToInt(
                                        e ->
                                                e.getKey()
                                                                .getBytes(
                                                                        java.nio.charset
                                                                                .StandardCharsets
                                                                                .UTF_8)
                                                                .length
                                                        + (e.getValue().s() == null
                                                                ? 32
                                                                : e.getValue()
                                                                        .s()
                                                                        .getBytes(
                                                                                java.nio.charset
                                                                                        .StandardCharsets
                                                                                        .UTF_8)
                                                                        .length))
                                .sum();
                if (size > 400 * 1024)
                    throw new PaymentValidationException(
                            "ITEM_SIZE", "DynamoDB item exceeds 400 KiB");
                bytes += size;
            }
        }
        if (bytes > 4 * 1024 * 1024)
            throw new PaymentValidationException(
                    "TRANSACTION_SIZE", "DynamoDB transaction exceeds 4 MiB");
        client.transactWriteItems(
                builder -> builder.transactItems(actions).clientRequestToken(token));
    }

    @PreDestroy
    public void close() {
        client.close();
    }
}
