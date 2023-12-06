package no.sikt.nva.nvi.events.db;

import static no.sikt.nva.nvi.test.DynamoDbUtils.mapToMessageBodies;
import static no.sikt.nva.nvi.test.DynamoDbUtils.randomEventWithNumberOfDynamoRecords;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import com.amazonaws.services.lambda.runtime.Context;
import java.util.List;
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

    private List<String> extractBatchEntryMessageBodiesAtIndex(int index) {
        return sqsClient.getSentBatches()
                   .get(index)
                   .entries()
                   .stream()
                   .map(SendMessageBatchRequestEntry::messageBody)
                   .toList();
    }
}
