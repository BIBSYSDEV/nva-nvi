package no.sikt.nva.nvi.events.db;

import static no.unit.nva.testutils.RandomDataGenerator.objectMapper;
import static no.unit.nva.testutils.RandomDataGenerator.randomElement;
import static no.unit.nva.testutils.RandomDataGenerator.randomString;
import static nva.commons.core.attempt.Try.attempt;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.is;
import static org.mockito.Mockito.mock;
import com.amazonaws.services.lambda.runtime.Context;
import com.amazonaws.services.lambda.runtime.events.DynamodbEvent;
import com.amazonaws.services.lambda.runtime.events.DynamodbEvent.DynamodbStreamRecord;
import com.amazonaws.services.lambda.runtime.events.SQSEvent;
import com.amazonaws.services.lambda.runtime.events.SQSEvent.SQSMessage;
import com.amazonaws.services.lambda.runtime.events.models.dynamodb.AttributeValue;
import com.amazonaws.services.lambda.runtime.events.models.dynamodb.OperationType;
import com.amazonaws.services.lambda.runtime.events.models.dynamodb.StreamRecord;
import java.util.Map;
import java.util.stream.IntStream;
import no.sikt.nva.nvi.events.evaluator.FakeSqsClient;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

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
        var expectedSqsEvent = createSqsEvent(dynamoDbEvent);
        var actualSqsEvent = sqsClient.getSentBatches().get(0);
        assertThat(actualSqsEvent, is(equalTo(expectedSqsEvent)));
    }

    private static String mapToString(DynamodbStreamRecord record) {
        return attempt(() -> objectMapper.writeValueAsString(record)).orElseThrow();
    }

    private SQSEvent createSqsEvent(DynamodbEvent dynamoDbEvent) {
        var event = new SQSEvent();
        event.setRecords(dynamoDbEvent.getRecords()
                             .stream()
                             .map(this::mapToSqsMessage)
                             .toList());
        return event;
    }

    private SQSMessage mapToSqsMessage(DynamodbStreamRecord record) {
        var message = new SQSMessage();
        message.setBody(mapToString(record));
        return message;
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
