package lab.payments.payment_session.infrastructure;

import static org.assertj.core.api.Assertions.assertThat;

import lab.common.StoredDocument;
import org.junit.jupiter.api.Test;
import software.amazon.awssdk.enhanced.dynamodb.TableSchema;
import software.amazon.awssdk.services.dynamodb.model.AttributeValue;

class StoredDocumentMappingTest {
    @Test
    void givenLombokAccessorsWhenEnhancedClientBuildsItsSchemaThenKeyMappingsRemainIntact() {
        // Given
        var document = new StoredDocument();
        document.setPk("merchant-session");
        document.setSk("HEAD");
        document.setData("{\"version\":1}");
        var schema = TableSchema.fromBean(StoredDocument.class);

        var attributes = schema.itemToMap(document, true);
        var restoredDocument = schema.mapToItem(attributes);

        // Then
        assertThat(schema.tableMetadata().primaryPartitionKey()).isEqualTo("PK");
        assertThat(schema.tableMetadata().primarySortKey()).contains("SK");
        assertThat(attributes.get("PK")).isEqualTo(AttributeValue.fromS("merchant-session"));
        assertThat(restoredDocument.getPk()).isEqualTo(document.getPk());
        assertThat(restoredDocument.getSk()).isEqualTo(document.getSk());
        assertThat(restoredDocument.getData()).isEqualTo(document.getData());
    }
}
