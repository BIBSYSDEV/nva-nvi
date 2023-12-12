package no.sikt.nva.nvi.test;

import static no.unit.nva.commons.json.JsonUtils.dynamoObjectMapper;
import static no.unit.nva.testutils.RandomDataGenerator.randomElement;
import static no.unit.nva.testutils.RandomDataGenerator.randomString;
import static nva.commons.core.attempt.Try.attempt;
import com.amazonaws.services.lambda.runtime.events.DynamodbEvent;
import com.amazonaws.services.lambda.runtime.events.DynamodbEvent.DynamodbStreamRecord;
import com.amazonaws.services.lambda.runtime.events.models.dynamodb.AttributeValue;
import com.amazonaws.services.lambda.runtime.events.models.dynamodb.OperationType;
import com.amazonaws.services.lambda.runtime.events.models.dynamodb.StreamRecord;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.IntStream;

public final class DynamoDbTestUtils {

    public static final String IDENTIFIER = "identifier";

    private DynamoDbTestUtils() {
    }

    public static DynamodbEvent eventWithCandidateIdentifier(UUID candidateIdentifier, OperationType operationType) {
        var dynamoDbEvent = new DynamodbEvent();
        var dynamoDbRecord = dynamoRecordWithIdentifier(candidateIdentifier, operationType);
        dynamoDbEvent.setRecords(List.of(dynamoDbRecord));
        return dynamoDbEvent;
    }

    public static DynamodbEvent randomEventWithNumberOfDynamoRecords(int numberOfRecords) {
        var event = new DynamodbEvent();
        var records = IntStream.range(0, numberOfRecords)
                          .mapToObj(index -> dynamoRecordWithIdentifier(UUID.randomUUID(),
                                                                        randomElement(OperationType.values())))
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

    private static DynamodbStreamRecord dynamoRecordWithIdentifier(UUID candidateIdentifier,
                                                                   OperationType operationType) {
        var streamRecord = new DynamodbStreamRecord();
        streamRecord.setEventName(operationType);
        streamRecord.setEventID(randomString());
        streamRecord.setAwsRegion(randomString());
        streamRecord.setDynamodb(payloadWithIdentifier(candidateIdentifier));
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

    private static Map<String, AttributeValue> randomDynamoPayload(UUID candidateIdentifier) {
        var value = new AttributeValue(candidateIdentifier.toString());
        return Map.of(IDENTIFIER, value);
    }
}
