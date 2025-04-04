package no.sikt.nva.nvi.events.db;

import static no.sikt.nva.nvi.common.DynamoDbTestUtils.eventWithCandidateIdentifier;
import static no.sikt.nva.nvi.common.DynamoDbTestUtils.mapToMessageBodies;
import static no.sikt.nva.nvi.common.DynamoDbTestUtils.randomDynamoDbEvent;
import static no.sikt.nva.nvi.common.DynamoDbTestUtils.randomEventWithNumberOfDynamoRecords;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.amazonaws.services.lambda.runtime.Context;
import java.util.List;
import java.util.UUID;
import no.sikt.nva.nvi.common.queue.FakeSqsClient;
import nva.commons.core.Environment;
import nva.commons.logutils.LogUtils;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import software.amazon.awssdk.services.sqs.model.SendMessageBatchRequestEntry;
import software.amazon.awssdk.services.sqs.model.SqsException;

class DynamoDbEventToQueueHandlerTest {

  public static final Context CONTEXT = mock(Context.class);
  public static final String DLQ_URL = "IndexDlq";
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
  void shouldSendMessageToDlqIfSendingBatchFails() {
    var candidateIdentifier = UUID.randomUUID();
    var dynamoDbEvent = eventWithCandidateIdentifier(candidateIdentifier);
    var fakeSqsClient = mock(FakeSqsClient.class);
    when(fakeSqsClient.sendMessageBatch(any(), any())).thenThrow(SqsException.class);
    var handler = new DynamoDbEventToQueueHandler(fakeSqsClient, new Environment());
    assertThrows(RuntimeException.class, () -> handler.handleRequest(dynamoDbEvent, CONTEXT));
    verify(fakeSqsClient, times(1)).sendMessage(anyString(), eq(DLQ_URL), eq(candidateIdentifier));
  }

  @Test
  void
      shouldSendMessageToDlqWithoutCandidateIdentifierIfSendingBatchFailsAndUnableToExtractRecordIdentifier() {
    var dynamoDbEvent = randomDynamoDbEvent();
    var fakeSqsClient = mock(FakeSqsClient.class);
    when(fakeSqsClient.sendMessageBatch(any(), any())).thenThrow(SqsException.class);
    var handler = new DynamoDbEventToQueueHandler(fakeSqsClient, new Environment());
    assertThrows(RuntimeException.class, () -> handler.handleRequest(dynamoDbEvent, CONTEXT));
    verify(fakeSqsClient, times(1)).sendMessage(anyString(), eq(DLQ_URL));
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
    return sqsClient.getSentBatches().get(index).entries().stream()
        .map(SendMessageBatchRequestEntry::messageBody)
        .toList();
  }
}
