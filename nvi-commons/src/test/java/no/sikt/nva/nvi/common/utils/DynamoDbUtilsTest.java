package no.sikt.nva.nvi.common.utils;

import static no.sikt.nva.nvi.test.DynamoDbTestUtils.getAttributeValueMap;
import static no.unit.nva.commons.json.JsonUtils.dynamoObjectMapper;
import static org.junit.jupiter.api.Assertions.assertEquals;
import com.amazonaws.services.lambda.runtime.events.DynamodbEvent.DynamodbStreamRecord;
import com.amazonaws.services.lambda.runtime.events.models.dynamodb.StreamRecord;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import software.amazon.awssdk.services.dynamodb.model.AttributeValue;

class DynamoDbUtilsTest {

    public static final String SOME_FIELD_NAME = "fieldWithNullValue";
    public static final String IDENTIFIER = "identifier";

    @Test
    void shouldParseNullValuesWhenGettingImage() {
        var streamRecordWithNullValue = setUpStreamRecordWithFieldWithNullValue();
        var attributeValueMap = DynamoDbUtils.getImage(streamRecordWithNullValue);
        var expectedAttributeValue = AttributeValue.builder().nul(true).build();
        assertEquals(expectedAttributeValue, attributeValueMap.get(SOME_FIELD_NAME));
    }

    @Test
    void shouldParseImageToAttributeValueMap() {
        var expectedAttributeValueMap = randomAttributeValueMap();
        var streamRecord = dynamodbRecordWithStreamRecord(
            new StreamRecord().withNewImage(getAttributeValueMap(expectedAttributeValueMap)));
        var actualAttributeValueMap = DynamoDbUtils.getImage(streamRecord);
        assertEquals(expectedAttributeValueMap, actualAttributeValueMap);
    }

    @Test
    void shouldExtractIdentifierFromNewImageWhenNewImageIsPresent() {
        var expectedIdentifier = UUID.randomUUID();
        var streamRecordWithNewImage = dynamodbRecordWithStreamRecord(
            streamRecordWithOldAndNewImage(expectedIdentifier));
        var actualIdentifier = DynamoDbUtils.extractIdFromRecord(streamRecordWithNewImage);
        assertEquals(expectedIdentifier, actualIdentifier.orElse(null));
    }

    @Test
    void shouldExtractIdentifierFromOldImageWhenNewImageIsNotPresent() {
        var expectedIdentifier = UUID.randomUUID();
        var streamRecordWithOldImage = dynamodbRecordWithStreamRecord(streamRecordWithOnlyOldImage(expectedIdentifier));
        var actualIdentifier = DynamoDbUtils.extractIdFromRecord(streamRecordWithOldImage);
        assertEquals(expectedIdentifier, actualIdentifier.orElse(null));
    }

    @Test
    void shouldSerializeWithoutLossOfData() throws Exception {
        var originalStreamRecord = dynamodbRecordWithStreamRecord(streamRecordWithOldAndNewImage(UUID.randomUUID()));
        var streamRecordAsString = dynamoObjectMapper.writeValueAsString(originalStreamRecord);
        var regeneratedStreamRecord = DynamoDbUtils.toDynamodbStreamRecord(streamRecordAsString);
        assertEquals(originalStreamRecord, regeneratedStreamRecord);
    }

    private static Map<String, AttributeValue> randomAttributeValueMap() {
        return Map.of("someMapField", AttributeValue.builder()
                                          .m(Map.of("someField", AttributeValue.builder()
                                                                     .s("someValue")
                                                                     .build()))
                                          .build());
    }

    private static DynamodbStreamRecord dynamodbRecordWithStreamRecord(StreamRecord streamRecord) {
        return (DynamodbStreamRecord) new DynamodbStreamRecord().withDynamodb(
            streamRecord);
    }

    private static StreamRecord streamRecordWithOldAndNewImage(UUID newImageIdentifier) {
        return new StreamRecord().withNewImage(
                Map.of(IDENTIFIER,
                       new com.amazonaws.services.lambda.runtime.events.models.dynamodb.AttributeValue().withS(
                           newImageIdentifier.toString())))
                   .withOldImage(
                       Map.of(IDENTIFIER,
                              new com.amazonaws.services.lambda.runtime.events.models.dynamodb.AttributeValue().withS(
                                  UUID.randomUUID().toString())));
    }

    private static StreamRecord streamRecordWithOnlyOldImage(UUID identifier) {
        return new StreamRecord().withOldImage(
            Map.of(IDENTIFIER,
                   new com.amazonaws.services.lambda.runtime.events.models.dynamodb.AttributeValue().withS(
                       identifier.toString())));
    }

    private static DynamodbStreamRecord setUpStreamRecordWithFieldWithNullValue() {
        return dynamodbRecordWithStreamRecord(new StreamRecord().withNewImage(
            Map.of(SOME_FIELD_NAME,
                   new com.amazonaws.services.lambda.runtime.events.models.dynamodb.AttributeValue().withNULL(true))));
    }
}