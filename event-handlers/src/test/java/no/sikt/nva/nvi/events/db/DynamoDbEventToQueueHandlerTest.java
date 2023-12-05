package no.sikt.nva.nvi.events.db;

import static no.unit.nva.commons.json.JsonUtils.dynamoObjectMapper;
import static no.unit.nva.testutils.RandomDataGenerator.randomElement;
import static no.unit.nva.testutils.RandomDataGenerator.randomString;
import static nva.commons.core.attempt.Try.attempt;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;
import com.amazonaws.services.lambda.runtime.Context;
import com.amazonaws.services.lambda.runtime.events.DynamodbEvent;
import com.amazonaws.services.lambda.runtime.events.DynamodbEvent.DynamodbStreamRecord;
import com.amazonaws.services.lambda.runtime.events.models.dynamodb.AttributeValue;
import com.amazonaws.services.lambda.runtime.events.models.dynamodb.OperationType;
import com.amazonaws.services.lambda.runtime.events.models.dynamodb.StreamRecord;
import java.util.List;
import java.util.Map;
import java.util.stream.IntStream;
import no.sikt.nva.nvi.events.evaluator.FakeSqsClient;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import software.amazon.awssdk.services.sqs.model.SendMessageBatchRequestEntry;

public class DynamoDbEventToQueueHandlerTest {

    private DynamoDbEventToQueueHandler handler;
    private FakeSqsClient sqsClient;

    @BeforeEach
    void init() {
        sqsClient = new FakeSqsClient();
        handler = new DynamoDbEventToQueueHandler(sqsClient);
    }

    @Test
    void shouldQueueSQSEventWhenReceivingDynamoDbEvent() {
        var dynamoDbEvent = randomEventWithNumberOfDynamoRecords(1);
        handler.handleRequest(dynamoDbEvent, mock(Context.class));
        var expectedMessages = mapToMessageBodies(dynamoDbEvent);
        var actualMessages = extractBatchEntryMessageBodiesAtIndex(0);
        assertEquals(expectedMessages, actualMessages);
    }

    @Test
    void shouldSendMessageBatchWithSize10() {
        var dynamoDbEvent = randomEventWithNumberOfDynamoRecords(11);
        handler.handleRequest(dynamoDbEvent, mock(Context.class));
        var batchOneMessages = extractBatchEntryMessageBodiesAtIndex(0);
        var batchTwoMessages = extractBatchEntryMessageBodiesAtIndex(1);
        assertEquals(10, batchOneMessages.size());
        assertEquals(1, batchTwoMessages.size());
    }

    private static String mapToString(DynamodbStreamRecord record) {
        return attempt(() -> dynamoObjectMapper.writeValueAsString(record)).orElseThrow();
    }

    private List<String> extractBatchEntryMessageBodiesAtIndex(int index) {
        return sqsClient.getSentBatches()
                   .get(index)
                   .entries()
                   .stream()
                   .map(SendMessageBatchRequestEntry::messageBody)
                   .toList();
    }

    private List<String> mapToMessageBodies(DynamodbEvent dynamoDbEvent) {
        return dynamoDbEvent.getRecords()
                   .stream()
                   .map(DynamoDbEventToQueueHandlerTest::mapToString)
                   .toList();
    }

    private DynamodbEvent randomEventWithNumberOfDynamoRecords(int numberOfRecords) {
        var event = new DynamodbEvent();
        var records = IntStream.range(0, numberOfRecords)
                          .mapToObj(index -> randomDynamoRecord())
                          .toList();
        event.setRecords(records);
        return event;
    }

    private DynamodbEvent.DynamodbStreamRecord randomDynamoRecord() {
        var streamRecord = new DynamodbStreamRecord();
        streamRecord.setEventName(randomElement(OperationType.values()));
        streamRecord.setEventID(randomString());
        streamRecord.setAwsRegion(randomString());
        streamRecord.setDynamodb(randomPayload());
        streamRecord.setEventSource(randomString());
        streamRecord.setEventVersion(randomString());
        return streamRecord;
    }

    private StreamRecord randomPayload() {
        var streamRecord = new StreamRecord();
        streamRecord.setOldImage(randomDynamoPayload());
        streamRecord.setNewImage(randomDynamoPayload());
        return streamRecord;
    }

    private Map<String, AttributeValue> randomDynamoPayload() {
        var value = new AttributeValue(randomString());
        return Map.of(randomString(), value);
    }
}
