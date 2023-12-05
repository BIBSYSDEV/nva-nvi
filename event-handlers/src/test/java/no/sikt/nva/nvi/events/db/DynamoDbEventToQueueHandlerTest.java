package no.sikt.nva.nvi.events.db;

import static no.unit.nva.commons.json.JsonUtils.dynamoObjectMapper;
import static no.unit.nva.testutils.RandomDataGenerator.randomElement;
import static no.unit.nva.testutils.RandomDataGenerator.randomString;
import static nva.commons.core.attempt.Try.attempt;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
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
import nva.commons.core.Environment;
import nva.commons.logutils.LogUtils;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import software.amazon.awssdk.services.sqs.model.SendMessageBatchRequestEntry;
import software.amazon.awssdk.services.sqs.model.SqsException;

public class DynamoDbEventToQueueHandlerTest {

    public static final Context CONTEXT = mock(Context.class);
    private DynamoDbEventToQueueHandler handler;
    private FakeSqsClient sqsClient;

    @BeforeEach
    void init() {
        sqsClient = new FakeSqsClient();
        handler = new DynamoDbEventToQueueHandler(sqsClient, new Environment());
    }

    @Test
    void shouldLogErrorAndThrowExceptionIfSendingBatchFails() {
        var dynamoDbEvent = randomEventWithNumberOfDynamoRecords(1);
        var failingSqsClient = mock(FakeSqsClient.class);
        when(failingSqsClient.sendMessageBatch(any(), any())).thenThrow(SqsException.class);
        var handler = new DynamoDbEventToQueueHandler(failingSqsClient, new Environment());
        var appender = LogUtils.getTestingAppender(DynamoDbEventToQueueHandler.class);
        assertThrows(RuntimeException.class, () -> handler.handleRequest(dynamoDbEvent, CONTEXT));
        assertThat(appender.getMessages(), containsString("Failure"));
    }

    @Test
    void shouldQueueSqsMessageWhenReceivingDynamoDbEvent() {
        var dynamoDbEvent = randomEventWithNumberOfDynamoRecords(1);
        handler.handleRequest(dynamoDbEvent, CONTEXT);
        var expectedMessages = mapToMessageBodies(dynamoDbEvent);
        var actualMessages = extractBatchEntryMessageBodiesAtIndex(0);
        assertEquals(expectedMessages, actualMessages);
    }

    @Test
    void shouldSendMessageBatchWithSize10() {
        var dynamoDbEvent = randomEventWithNumberOfDynamoRecords(11);
        handler.handleRequest(dynamoDbEvent, CONTEXT);
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
