package no.sikt.nva.nvi.events.db;

import static java.util.Objects.nonNull;
import static no.unit.nva.commons.json.JsonUtils.dtoObjectMapper;
import static nva.commons.core.attempt.Try.attempt;
import com.amazonaws.services.lambda.runtime.events.DynamodbEvent.DynamodbStreamRecord;
import com.fasterxml.jackson.core.JsonProcessingException;
import java.util.Map;
import java.util.Map.Entry;
import java.util.stream.Collector;
import java.util.stream.Collectors;
import software.amazon.awssdk.services.dynamodb.model.AttributeValue;

public class DynamoDbUtils {

    protected static Map<String, AttributeValue> getImage(DynamodbStreamRecord record) {
        var image = nonNull(record.getDynamodb().getNewImage())
                        ? record.getDynamodb().getNewImage()
                        : record.getDynamodb().getOldImage();
        return mapToDynamoDbAttributeValue(image);
    }

    private static Map<String, AttributeValue> mapToDynamoDbAttributeValue(
        Map<String, com.amazonaws.services.lambda.runtime.events.models.dynamodb.AttributeValue> image) {
        return image.entrySet()
                   .stream()
                   .collect(toDynamoDbMap());
    }

    private static Collector<Entry<String,
                                      com.amazonaws.services.lambda.runtime.events.models.dynamodb.AttributeValue>, ?
                                , Map<String, AttributeValue>> toDynamoDbMap() {
        return Collectors.toMap(Entry::getKey,
                                attributeValue -> attempt(
                                    () -> mapToDynamoDbValue(attributeValue.getValue())).orElseThrow());
    }

    private static AttributeValue mapToDynamoDbValue(
        com.amazonaws.services.lambda.runtime.events.models.dynamodb.AttributeValue value)
        throws JsonProcessingException {
        var json = writeAsString(value);
        return dtoObjectMapper.readValue(json, AttributeValue.serializableBuilderClass()).build();
    }

    private static String writeAsString(
        com.amazonaws.services.lambda.runtime.events.models.dynamodb.AttributeValue attributeValue)
        throws JsonProcessingException {
        return dtoObjectMapper.writeValueAsString(attributeValue);
    }
}
