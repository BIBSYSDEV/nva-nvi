package no.sikt.nva.nvi.test;

import static no.unit.nva.commons.json.JsonUtils.dynamoObjectMapper;
import static no.unit.nva.testutils.RandomDataGenerator.randomString;
import static nva.commons.core.attempt.Try.attempt;
import com.amazonaws.services.lambda.runtime.events.DynamodbEvent;
import com.amazonaws.services.lambda.runtime.events.DynamodbEvent.DynamodbStreamRecord;
import com.amazonaws.services.lambda.runtime.events.models.dynamodb.AttributeValue;
import com.amazonaws.services.lambda.runtime.events.models.dynamodb.StreamRecord;
import com.fasterxml.jackson.core.JsonProcessingException;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.UUID;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import no.sikt.nva.nvi.common.db.CandidateDao;
import no.sikt.nva.nvi.common.db.Dao;
import no.sikt.nva.nvi.common.db.DynamoEntryWithRangeKey;

public final class DynamoDbTestUtils {

    public static final String IDENTIFIER = "identifier";

    private DynamoDbTestUtils() {
    }

    public static DynamodbEvent eventWithCandidateIdentifier(UUID candidateIdentifier) {
        var dynamoDbEvent = new DynamodbEvent();
        var dynamoDbRecord = dynamoRecordWithIdentifier(payloadWithIdentifier(candidateIdentifier));
        dynamoDbEvent.setRecords(List.of(dynamoDbRecord));
        return dynamoDbEvent;
    }

    public static DynamodbEvent eventWithCandidate(CandidateDao candidate) {
        var dynamoDbEvent = new DynamodbEvent();
        var dynamoDbRecord = dynamoRecordWithIdentifier(payloadWithCandidate(candidate));
        dynamoDbEvent.setRecords(List.of(dynamoDbRecord));
        return dynamoDbEvent;
    }

    public static DynamodbEvent randomDynamoDbEvent() {
        var dynamoDbEvent = new DynamodbEvent();
        var dynamoDbRecord = dynamoRecordWithIdentifier(randomPayload());
        dynamoDbEvent.setRecords(List.of(dynamoDbRecord));
        return dynamoDbEvent;
    }

    public static DynamodbEvent randomEventWithNumberOfDynamoRecords(int numberOfRecords) {
        var event = new DynamodbEvent();
        var records = IntStream.range(0, numberOfRecords)
                          .mapToObj(index -> dynamoRecordWithIdentifier(payloadWithIdentifier(UUID.randomUUID())))
                          .toList();
        event.setRecords(records);
        return event;
    }

    public static List<String> mapToMessageBodies(DynamodbEvent dynamoDbEvent) {
        return dynamoDbEvent.getRecords()
                   .stream()
                   .map(DynamoDbTestUtils::mapToString)
                   .toList();
    }

    public static String mapToString(DynamodbStreamRecord record) {
        return attempt(() -> dynamoObjectMapper.writeValueAsString(record)).orElseThrow();
    }

    private static DynamodbStreamRecord dynamoRecordWithIdentifier(StreamRecord record) {
        var streamRecord = new DynamodbStreamRecord();
        streamRecord.setEventName(randomString());
        streamRecord.setEventID(randomString());
        streamRecord.setAwsRegion(randomString());
        streamRecord.setDynamodb(record);
        streamRecord.setEventSource(randomString());
        streamRecord.setEventVersion(randomString());
        return streamRecord;
    }

    private static StreamRecord payloadWithIdentifier(UUID candidateIdentifier) {
        var streamRecord = new StreamRecord();
        streamRecord.setOldImage(randomDynamoPayload(candidateIdentifier));
        streamRecord.setNewImage(randomDynamoPayload(candidateIdentifier));
        return streamRecord;
    }

    private static StreamRecord payloadWithCandidate(CandidateDao candidate) {
        var attributeValueMap = attempt(() -> toAttributeValueMap(candidate)).orElseThrow();

        return createPayload(attributeValueMap, attributeValueMap);
    }

    private static StreamRecord createPayload(Map<String, AttributeValue> oldImage,
                                              Map<String, AttributeValue> newImage) {
        var streamRecord = new StreamRecord();
        streamRecord.setOldImage(oldImage);
        streamRecord.setNewImage(newImage);
        return streamRecord;
    }

    private static Map<String, AttributeValue> toAttributeValueMap(Dao dao) {
        var dynamoFormat = dao.toDynamoFormat();
        return getAttributeValueMap(dynamoFormat);
    }

    private static Map<String, AttributeValue> getAttributeValueMap(
        Map<String, software.amazon.awssdk.services.dynamodb.model.AttributeValue> value) {
        return value.entrySet()
                   .stream()
                   .collect(Collectors.toMap(Entry::getKey, mapValue -> getAttributeValue(mapValue.getValue())));
    }

    private static AttributeValue getAttributeValue(
        software.amazon.awssdk.services.dynamodb.model.AttributeValue value) {
        return switch (value.type()) {
            case S -> new AttributeValue().withS(value.s());
            case SS -> new AttributeValue().withSS(value.ss());
            case N -> new AttributeValue().withN(value.n());
            case M -> new AttributeValue().withM(getAttributeValueMap(value.m()));
            case L -> new AttributeValue().withL(value.l().stream().map(DynamoDbTestUtils::getAttributeValue).toList());
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
