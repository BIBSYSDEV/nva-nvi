package no.sikt.nva.nvi.events.db;

import static org.junit.jupiter.api.Assertions.assertEquals;
import com.amazonaws.services.lambda.runtime.events.DynamodbEvent.DynamodbStreamRecord;
import com.amazonaws.services.lambda.runtime.events.models.dynamodb.StreamRecord;
import java.util.Map;
import org.junit.jupiter.api.Test;
import software.amazon.awssdk.services.dynamodb.model.AttributeValue;

class DynamoDbUtilsTest {

    public static final String SOME_FIELD_NAME = "fieldWithNullValue";

    @Test
    void shouldParseNullValuesWhenGettingImage() {
        var streamRecordWithNullValue = setUpStreamRecordWithFieldWithNullValue();
        var attributeValueMap = DynamoDbUtils.getImage(streamRecordWithNullValue);
        var expectedAttributeValue = AttributeValue.builder().nul(true).build();
        assertEquals(expectedAttributeValue, attributeValueMap.get(SOME_FIELD_NAME));
    }

    private static DynamodbStreamRecord setUpStreamRecordWithFieldWithNullValue() {
        return (DynamodbStreamRecord) new DynamodbStreamRecord()
                                          .withDynamodb(new StreamRecord().withNewImage(
                                              Map.of(SOME_FIELD_NAME,
                                                     new com.amazonaws.services.lambda.runtime.events.models.dynamodb.AttributeValue().withNULL(
                                                         true))));
    }
}