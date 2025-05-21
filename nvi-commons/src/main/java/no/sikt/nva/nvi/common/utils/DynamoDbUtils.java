package no.sikt.nva.nvi.common.utils;

import static java.util.Collections.emptyList;
import static java.util.Objects.nonNull;
import static no.unit.nva.commons.json.JsonUtils.dtoObjectMapper;
import static nva.commons.core.attempt.Try.attempt;

import com.amazonaws.services.lambda.runtime.events.DynamodbEvent.DynamodbStreamRecord;
import com.fasterxml.jackson.core.JsonProcessingException;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Collector;
import java.util.stream.Collectors;
import no.sikt.nva.nvi.common.db.Dao;
import software.amazon.awssdk.services.dynamodb.model.AttributeValue;

public final class DynamoDbUtils {

  private static final String IDENTIFIER_FIELD = "identifier";

  private DynamoDbUtils() {}

  public static Optional<UUID> extractIdFromRecord(DynamodbStreamRecord streamRecord) {
    return attempt(() -> UUID.fromString(extractField(streamRecord, IDENTIFIER_FIELD)))
        .toOptional();
  }

  public static Map<String, AttributeValue> getImage(DynamodbStreamRecord streamRecord) {
    var image =
        nonNull(streamRecord.getDynamodb().getNewImage())
            ? streamRecord.getDynamodb().getNewImage()
            : streamRecord.getDynamodb().getOldImage();
    return mapToDynamoDbAttributeValue(image);
  }

  public static DynamodbStreamRecord toDynamodbStreamRecord(String body)
      throws JsonProcessingException {
    return dtoObjectMapper.readValue(body, DynamodbStreamRecord.class);
  }

  public static String extractField(DynamodbStreamRecord streamRecord, String field) {
    return Optional.ofNullable(streamRecord.getDynamodb().getNewImage())
        .orElse(streamRecord.getDynamodb().getOldImage())
        .get(field)
        .getS();
  }

  public static String extractField(Dao oldImage, Dao newImage, String field) {
    return Optional.ofNullable(newImage).orElse(oldImage).toDynamoFormat().get(field).s();
  }

  private static Map<String, AttributeValue> mapToDynamoDbAttributeValue(
      Map<String, com.amazonaws.services.lambda.runtime.events.models.dynamodb.AttributeValue>
          image) {
    return image.entrySet().stream().collect(toDynamoDbMap());
  }

  private static Collector<
          Entry<
              String, com.amazonaws.services.lambda.runtime.events.models.dynamodb.AttributeValue>,
          ?,
          Map<String, AttributeValue>>
      toDynamoDbMap() {
    return Collectors.toMap(
        Entry::getKey,
        attributeValue ->
            attempt(() -> mapToDynamoDbValue(attributeValue.getValue())).orElseThrow());
  }

  private static AttributeValue mapToDynamoDbValue(
      com.amazonaws.services.lambda.runtime.events.models.dynamodb.AttributeValue value)
      throws JsonProcessingException {
    if (isNullValue(value)) {
      return AttributeValue.builder().nul(true).build();
    }
    if (containsAttributeValueMap(value)) {
      return mapEachAttributeValueToDynamoDbValue(value);
    }
    if (nonNull(value.getL())) {
      return mapListAttributeToDynamoDbValue(value);
    }
    var json = writeAsString(value);
    return dtoObjectMapper.readValue(json, AttributeValue.serializableBuilderClass()).build();
  }

  private static AttributeValue mapEachAttributeValueToDynamoDbValue(
      com.amazonaws.services.lambda.runtime.events.models.dynamodb.AttributeValue value) {
    var attributeValueMap =
        value.getM().entrySet().stream()
            .collect(
                Collectors.toMap(
                    Entry::getKey,
                    entry -> attempt(() -> mapToDynamoDbValue(entry.getValue())).orElseThrow()));
    return AttributeValue.builder().m(attributeValueMap).build();
  }

  private static AttributeValue mapListAttributeToDynamoDbValue(
      com.amazonaws.services.lambda.runtime.events.models.dynamodb.AttributeValue value) {
    if (value.getL().isEmpty()) {
      return AttributeValue.builder().l(emptyList()).build();
    }
    var converted =
        value.getL().stream()
            .map(item -> attempt(() -> mapToDynamoDbValue(item)).orElseThrow())
            .toList();
    return AttributeValue.builder().l(converted).build();
  }

  private static boolean containsAttributeValueMap(
      com.amazonaws.services.lambda.runtime.events.models.dynamodb.AttributeValue value) {
    return nonNull(value.getM());
  }

  private static boolean isNullValue(
      com.amazonaws.services.lambda.runtime.events.models.dynamodb.AttributeValue value) {
    return nonNull(value.isNULL()) && value.isNULL();
  }

  private static String writeAsString(
      com.amazonaws.services.lambda.runtime.events.models.dynamodb.AttributeValue attributeValue)
      throws JsonProcessingException {
    return dtoObjectMapper.writeValueAsString(attributeValue);
  }
}
