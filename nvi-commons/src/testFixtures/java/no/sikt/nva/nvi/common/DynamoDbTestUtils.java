package no.sikt.nva.nvi.common;

import static java.util.Objects.nonNull;
import static no.sikt.nva.nvi.common.DatabaseConstants.HASH_KEY;
import static no.sikt.nva.nvi.common.DatabaseConstants.SORT_KEY;
import static no.unit.nva.commons.json.JsonUtils.dtoObjectMapper;
import static no.unit.nva.testutils.RandomDataGenerator.randomElement;
import static no.unit.nva.testutils.RandomDataGenerator.randomString;
import static nva.commons.core.attempt.Try.attempt;

import com.amazonaws.services.lambda.runtime.events.DynamodbEvent;
import com.amazonaws.services.lambda.runtime.events.DynamodbEvent.DynamodbStreamRecord;
import com.amazonaws.services.lambda.runtime.events.models.dynamodb.AttributeValue;
import com.amazonaws.services.lambda.runtime.events.models.dynamodb.StreamRecord;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.UUID;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import no.sikt.nva.nvi.common.db.Dao;
import software.amazon.awssdk.enhanced.dynamodb.document.EnhancedDocument;
import software.amazon.awssdk.services.dynamodb.model.OperationType;

public final class DynamoDbTestUtils {

  public static final String IDENTIFIER = "identifier";
  public static final ObjectMapper OBJECT_MAPPER =
      new ObjectMapper().registerModule(new JavaTimeModule());

  private DynamoDbTestUtils() {}

  public static DynamodbEvent eventWithCandidateIdentifier(UUID candidateIdentifier) {
    var dynamoDbEvent = new DynamodbEvent();
    var dynamoDbRecord =
        dynamoRecord(payloadWithIdentifier(candidateIdentifier), randomOperationType());
    dynamoDbEvent.setRecords(List.of(dynamoDbRecord));
    return dynamoDbEvent;
  }

  public static DynamodbEvent eventWithCandidate(
      Dao oldImage, Dao newImage, OperationType operationType) {
    var dynamoDbEvent = new DynamodbEvent();
    var dynamoDbRecord = dynamoRecord(payloadWithCandidate(oldImage, newImage), operationType);
    dynamoDbEvent.setRecords(List.of(dynamoDbRecord));
    return dynamoDbEvent;
  }

  public static DynamodbEvent randomDynamoDbEvent() {
    var dynamoDbEvent = new DynamodbEvent();
    var dynamoDbRecord = dynamoRecord(randomPayload(), randomOperationType());
    dynamoDbEvent.setRecords(List.of(dynamoDbRecord));
    return dynamoDbEvent;
  }

  public static DynamodbEvent dynamoDbEventWithEmptyPayload() {
    var dynamoDbEvent = new DynamodbEvent();
    var dynamoDbRecord = dynamoRecord(new StreamRecord(), randomOperationType());
    dynamoDbEvent.setRecords(List.of(dynamoDbRecord));
    return dynamoDbEvent;
  }

  public static DynamodbEvent randomEventWithNumberOfDynamoRecords(int numberOfRecords) {
    var event = new DynamodbEvent();
    var records =
        IntStream.range(0, numberOfRecords)
            .mapToObj(
                index ->
                    dynamoRecord(payloadWithIdentifier(UUID.randomUUID()), randomOperationType()))
            .toList();
    event.setRecords(records);
    return event;
  }

  public static List<String> mapToMessageBodies(DynamodbEvent dynamoDbEvent) {
    return dynamoDbEvent.getRecords().stream().map(DynamoDbTestUtils::mapToString).toList();
  }

  public static String mapToString(DynamodbStreamRecord record) {
    return attempt(() -> dtoObjectMapper.writeValueAsString(record)).orElseThrow();
  }

  public static Map<String, AttributeValue> getAttributeValueMap(
      Map<String, software.amazon.awssdk.services.dynamodb.model.AttributeValue> value) {
    return value.entrySet().stream()
        .collect(
            Collectors.toMap(Entry::getKey, mapValue -> getAttributeValue(mapValue.getValue())));
  }

  private static OperationType randomOperationType() {
    return randomElement(OperationType.values());
  }

  private static DynamodbStreamRecord dynamoRecord(
      StreamRecord streamRecord, OperationType operationType) {
    var dynamodbStreamRecord = new DynamodbStreamRecord();
    dynamodbStreamRecord.setEventName(operationType.name());
    dynamodbStreamRecord.setEventID(randomString());
    dynamodbStreamRecord.setAwsRegion(randomString());
    dynamodbStreamRecord.setDynamodb(streamRecord);
    dynamodbStreamRecord.setEventSource(randomString());
    dynamodbStreamRecord.setEventVersion(randomString());
    return dynamodbStreamRecord;
  }

  private static StreamRecord payloadWithIdentifier(UUID candidateIdentifier) {
    var streamRecord = new StreamRecord();
    streamRecord.setOldImage(randomDynamoPayload(candidateIdentifier));
    streamRecord.setNewImage(randomDynamoPayload(candidateIdentifier));
    return streamRecord;
  }

  private static StreamRecord payloadWithCandidate(Dao oldImage, Dao newImage) {
    var oldAttributeValueMap =
        nonNull(oldImage) ? attempt(() -> toAttributeValueMap(oldImage)).orElseThrow() : null;
    var newAttributeValueMap =
        nonNull(newImage) ? attempt(() -> toAttributeValueMap(newImage)).orElseThrow() : null;

    return createPayload(oldImage, newImage, oldAttributeValueMap, newAttributeValueMap);
  }

  private static StreamRecord createPayload(
      Dao oldImage,
      Dao newImage,
      Map<String, AttributeValue> oldAttributeValueMap,
      Map<String, AttributeValue> newAttributeValueMap) {
    var streamRecord = new StreamRecord();
    setKeys(oldImage, newImage, streamRecord);
    streamRecord.setOldImage(oldAttributeValueMap);
    streamRecord.setNewImage(newAttributeValueMap);
    return streamRecord;
  }

  private static void setKeys(Dao oldImage, Dao newImage, StreamRecord streamRecord) {
    streamRecord.setKeys(
        Map.of(
            HASH_KEY,
            new AttributeValue(
                nonNull(oldImage) ? oldImage.primaryKeyHashKey() : newImage.primaryKeyHashKey())));
    streamRecord.setKeys(
        Map.of(
            SORT_KEY,
            new AttributeValue(
                nonNull(oldImage)
                    ? oldImage.primaryKeyRangeKey()
                    : newImage.primaryKeyRangeKey())));
  }

  private static Map<String, AttributeValue> toAttributeValueMap(Dao dao) {
    var dynamoFormat = toDynamoFormat(dao);
    return getAttributeValueMap(dynamoFormat);
  }

  private static Map<String, software.amazon.awssdk.services.dynamodb.model.AttributeValue>
      toDynamoFormat(Dao dao) {
    return EnhancedDocument.fromJson(
            attempt(() -> OBJECT_MAPPER.writeValueAsString(dao)).orElseThrow())
        .toMap();
  }

  private static AttributeValue getAttributeValue(
      software.amazon.awssdk.services.dynamodb.model.AttributeValue value) {
    // This is a workaround because I´m not able to create null attributes with the
    // dynamoObjectMapper
    return switch (value.type()) {
      case S -> new AttributeValue().withS(value.s());
      case SS -> new AttributeValue().withSS(value.ss());
      case N -> new AttributeValue().withN(value.n());
      case M -> new AttributeValue().withM(getAttributeValueMap(value.m()));
      case L ->
          new AttributeValue()
              .withL(value.l().stream().map(DynamoDbTestUtils::getAttributeValue).toList());
      case NUL -> new AttributeValue().withNULL(value.nul());
      case BOOL -> new AttributeValue().withBOOL(value.bool());
      default -> throw new IllegalArgumentException("Unknown type: " + value.type());
    };
  }

  private static StreamRecord randomPayload() {
    var streamRecord = new StreamRecord();
    streamRecord.setOldImage(Map.of(IDENTIFIER, new AttributeValue(randomString())));
    return streamRecord;
  }

  private static Map<String, AttributeValue> randomDynamoPayload(UUID candidateIdentifier) {
    var value = new AttributeValue(candidateIdentifier.toString());
    return Map.of(IDENTIFIER, value);
  }
}
