package no.sikt.nva.nvi.events;

import static java.util.Collections.emptyList;
import static java.util.UUID.randomUUID;
import static no.sikt.nva.nvi.common.DynamoDbTestUtils.createApprovalStatusEvent;
import static no.sikt.nva.nvi.common.DynamoDbTestUtils.createCandidateEvent;
import static no.sikt.nva.nvi.common.DynamoDbTestUtils.createValidCandidateEvent;
import static no.sikt.nva.nvi.common.DynamoDbTestUtils.dynamoRecord;
import static no.sikt.nva.nvi.common.DynamoDbTestUtils.eventWithDao;
import static no.sikt.nva.nvi.common.DynamoDbTestUtils.randomEventWithNumberOfDynamoRecords;
import static no.sikt.nva.nvi.common.QueueServiceTestUtils.invalidSqsMessage;
import static no.sikt.nva.nvi.common.db.CandidateDaoFixtures.randomApplicableCandidateDao;
import static no.sikt.nva.nvi.common.db.DbNviPeriodFixtures.randomPeriodDao;
import static no.sikt.nva.nvi.common.db.NoteDaoFixtures.randomNoteDao;
import static no.unit.nva.testutils.RandomDataGenerator.randomString;
import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.params.provider.Arguments.argumentSet;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import com.amazonaws.services.lambda.runtime.events.DynamodbEvent;
import com.amazonaws.services.lambda.runtime.events.SQSEvent;
import com.amazonaws.services.lambda.runtime.events.SQSEvent.SQSMessage;
import com.amazonaws.services.lambda.runtime.events.models.dynamodb.AttributeValue;
import com.amazonaws.services.lambda.runtime.events.models.dynamodb.StreamRecord;
import com.fasterxml.jackson.core.JsonProcessingException;
import java.util.List;
import java.util.Map;
import java.util.function.Predicate;
import java.util.stream.Stream;
import no.sikt.nva.nvi.common.EnvironmentFixtures;
import no.sikt.nva.nvi.common.db.CandidateUniquenessEntryDao;
import no.sikt.nva.nvi.common.queue.DataEntryType;
import no.sikt.nva.nvi.common.queue.FakeSqsClient;
import no.sikt.nva.nvi.common.queue.NviCandidateUpdatedMessage;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.MethodSource;
import software.amazon.awssdk.services.dynamodb.model.OperationType;
import software.amazon.awssdk.services.sns.model.PublishRequest;
import software.amazon.awssdk.services.sqs.model.SendMessageBatchRequest;
import software.amazon.awssdk.services.sqs.model.SendMessageBatchRequestEntry;
import software.amazon.awssdk.services.sqs.model.SendMessageRequest;
import software.amazon.awssdk.services.sqs.model.SqsException;

/**
 * This class contains integration tests for first stage of the automatic indexing process. It tests
 * that (faked) DynamoDB events are correctly processed and sent to the appropriate SQS queues and
 * SNS topics. Ideally, the recipients of these messages should be included in the test suite too,
 * but this is not done yet.
 *
 * <p>Each handler is wrapped with a managing class to make it easier to expand this test suite in
 * the future.
 */
class DynamoDbUpdateEventTest {
  private FakeSqsClient sharedQueueClient;
  private FakeNotificationClient snsClient;
  private DynamoDbToEventQueueHandlerContext dynamoDbEventHandlerContext;
  private DataEntryUpdateHandlerContext dataEntryUpdateHandlerContext;

  @BeforeEach
  void init() {
    sharedQueueClient = new FakeSqsClient();
    snsClient = new FakeNotificationClient();
    dynamoDbEventHandlerContext = new DynamoDbToEventQueueHandlerContext(sharedQueueClient);
    dataEntryUpdateHandlerContext = new DataEntryUpdateHandlerContext(sharedQueueClient, snsClient);
  }

  @Test
  void shouldSendMessageBatchWithSize10() {
    var dynamoDbEvent = randomEventWithNumberOfDynamoRecords(15);

    dynamoDbEventHandlerContext.handleEvent(dynamoDbEvent);

    var batchOneMessages = extractBatchEntryMessageBodiesAtIndex(0);
    var batchTwoMessages = extractBatchEntryMessageBodiesAtIndex(1);
    assertEquals(10, batchOneMessages.size());
    assertEquals(5, batchTwoMessages.size());
  }

  @ParameterizedTest
  @MethodSource("candidateEventProvider")
  void shouldPassCandidateEventsToCorrectSnsTopic(
      DynamodbEvent dynamoDbEvent,
      NviCandidateUpdatedMessage expectedMessage,
      String expectedTopic) {
    processDynamoEvent(dynamoDbEvent);
    var publishedMessages = snsClient.getPublishedMessages();
    assertThat(publishedMessages)
        .hasSize(1)
        .allMatch(hasMessageBody(expectedMessage))
        .allMatch(hasTopic(expectedTopic));
  }

  @ParameterizedTest
  @MethodSource("approvalEventProvider")
  void shouldPassApprovalEventsToCorrectSnsTopic(
      DynamodbEvent dynamoDbEvent,
      NviCandidateUpdatedMessage expectedMessage,
      String expectedTopic) {
    processDynamoEvent(dynamoDbEvent);
    var publishedMessages = snsClient.getPublishedMessages();
    assertThat(publishedMessages)
        .hasSize(1)
        .allMatch(hasMessageBody(expectedMessage))
        .allMatch(hasTopic(expectedTopic));
  }

  @ParameterizedTest()
  @MethodSource("otherDaoInsertEventProvider")
  void shouldDoNothingWhenReceivingEventWithOtherDaoTypes(DynamodbEvent dynamoDbEvent) {
    processDynamoEvent(dynamoDbEvent);

    assertThat(snsClient.getPublishedMessages()).isEmpty();
    assertThat(getDlqMessages()).isEmpty();
  }

  @ParameterizedTest
  @EnumSource(
      value = OperationType.class,
      names = {"INSERT", "MODIFY", "REMOVE"})
  void shouldHandleAllValidOperationTypes(OperationType operationType) {
    var dynamoDbEvent = createValidCandidateEvent(operationType);
    processDynamoEvent(dynamoDbEvent);
    assertThat(getDlqMessages()).isEmpty();
    assertThat(snsClient.getPublishedMessages()).hasSize(1);
  }

  @Test
  void shouldSendMessageToDlqIfSendingBatchFails() {
    var candidateIdentifier = randomUUID();
    var dynamoDbEvent = createCandidateEvent(candidateIdentifier, OperationType.MODIFY, true);

    // Rig the sharedQueueClient to throw an exception when sending a message to the queue
    sharedQueueClient = spy(FakeSqsClient.class);
    doThrow(SqsException.class).when(sharedQueueClient).sendMessageBatch(any(), any());
    dynamoDbEventHandlerContext = new DynamoDbToEventQueueHandlerContext(sharedQueueClient);
    dataEntryUpdateHandlerContext = new DataEntryUpdateHandlerContext(sharedQueueClient, snsClient);

    assertThrows(RuntimeException.class, () -> processDynamoEvent(dynamoDbEvent));
    assertThat(getDlqMessages()).hasSize(1);
    verify(sharedQueueClient, times(1))
        .sendMessage(
            anyString(), eq(EnvironmentFixtures.INDEX_DLQ.getValue()), eq(candidateIdentifier));
  }

  @Test
  void shouldSendMessageToDlqWhenFailingToExtractIdentifier() {
    var dynamoDbEvent = createDynamoDbEventWithMissingIdentifier();

    assertThrows(RuntimeException.class, () -> processDynamoEvent(dynamoDbEvent));
    assertThat(getDlqMessages()).hasSize(1);
  }

  @Test
  void shouldPublishValidMessagesFromSqsBatchWithInvalidMessages() {
    var validMessage = createValidSqsMessageForCandidate();
    var invalidMessage = invalidSqsMessage();
    var sqsEvent = new SQSEvent();
    sqsEvent.setRecords(List.of(validMessage, invalidMessage));

    dataEntryUpdateHandlerContext.handleEvent(sqsEvent);

    assertEquals(1, snsClient.getPublishedMessages().size());
  }

  @Test
  void shouldSendInvalidMessagesToDlq() {
    var validMessage = createValidSqsMessageForCandidate();
    var invalidMessage = invalidSqsMessage();
    var sqsEvent = new SQSEvent();
    sqsEvent.setRecords(List.of(validMessage, invalidMessage));

    dataEntryUpdateHandlerContext.handleEvent(sqsEvent);

    var dlqMessages = getDlqMessages();
    assertEquals(1, dlqMessages.size());
  }

  @Test
  void shouldProcessEventWithEmptyPointsList() {
    var dynamoDbEvent = createCandidateEventWithEmptyPointsList();
    processDynamoEvent(dynamoDbEvent);
    assertEquals(1, snsClient.getPublishedMessages().size());
  }

  /**
   * This is a wrapper method for the handlers and queues between the originating DynamoDB event and
   * the SNS topics these are published to after processing. This is intended to mimic the actual
   * flow of events in the system.
   *
   * @param dynamoDbEvent An event from a DynamoDB stream containing one or more records, each
   *     record containing the old and/or new image of the DynamoDB item.
   */
  private void processDynamoEvent(DynamodbEvent dynamoDbEvent) {
    dynamoDbEventHandlerContext.handleEvent(dynamoDbEvent);
    var queuedEvents =
        dynamoDbEventHandlerContext.getQueueClient().getSentBatches().stream()
            .map(DynamoDbUpdateEventTest::mapBatchMessageToSqsEvent)
            .toList();

    for (var event : queuedEvents) {
      dataEntryUpdateHandlerContext.handleEvent(event);
    }
  }

  private List<SQSEvent> getMessageBatchesFromQueue(String queueUrl) {
    return sharedQueueClient.getSentBatches().stream()
        .filter(hasBatchDestination(queueUrl))
        .map(DynamoDbUpdateEventTest::mapBatchMessageToSqsEvent)
        .toList();
  }

  private List<SQSEvent> getMessagesFromQueue(String queueUrl) {
    return sharedQueueClient.getSentMessages().stream()
        .filter(hasDestination(queueUrl))
        .map(DynamoDbUpdateEventTest::mapMessageRequestToSqsEvent)
        .toList();
  }

  private List<SQSEvent> getDlqMessages() {
    return getMessagesFromQueue(EnvironmentFixtures.INDEX_DLQ.getValue());
  }

  private static Predicate<SendMessageRequest> hasDestination(String expectedQueue) {
    return actualMessage -> expectedQueue.equals(actualMessage.queueUrl());
  }

  private static Predicate<SendMessageBatchRequest> hasBatchDestination(String expectedQueue) {
    return actualMessage -> expectedQueue.equals(actualMessage.queueUrl());
  }

  private static Predicate<PublishRequest> hasTopic(String expectedTopic) {
    return actualMessage -> expectedTopic.equals(actualMessage.topicArn());
  }

  private static Predicate<PublishRequest> hasMessageBody(
      NviCandidateUpdatedMessage expectedMessage) {
    try {
      var expectedJsonBody = expectedMessage.toJsonString();
      return actualMessage -> expectedJsonBody.equals(actualMessage.message());
    } catch (JsonProcessingException e) {
      throw new RuntimeException(e);
    }
  }

  private static SQSEvent mapBatchMessageToSqsEvent(SendMessageBatchRequest messageBatch) {
    var event = new SQSEvent();
    var messages =
        messageBatch.entries().stream()
            .map(DynamoDbUpdateEventTest::mapBatchMessageEntrytoSqsMessage)
            .toList();
    event.setRecords(messages);
    return event;
  }

  private static SQSMessage mapBatchMessageEntrytoSqsMessage(SendMessageBatchRequestEntry entry) {
    var message = new SQSMessage();
    message.setBody(entry.messageBody());
    return message;
  }

  private static SQSEvent mapMessageRequestToSqsEvent(SendMessageRequest request) {
    var event = new SQSEvent();
    var message = new SQSMessage();
    message.setBody(request.messageBody());
    event.setRecords(List.of(message));
    return event;
  }

  private List<String> extractBatchEntryMessageBodiesAtIndex(int index) {
    return sharedQueueClient.getSentBatches().get(index).entries().stream()
        .map(SendMessageBatchRequestEntry::messageBody)
        .toList();
  }

  private static Stream<Arguments> candidateEventProvider() {
    var identifier = randomUUID();
    return Stream.of(
        argumentSet(
            "Create candidate",
            createCandidateEvent(identifier, OperationType.INSERT, true),
            new NviCandidateUpdatedMessage(
                identifier, DataEntryType.CANDIDATE, OperationType.INSERT),
            EnvironmentFixtures.TOPIC_CANDIDATE_INSERT.getValue()),
        argumentSet(
            "Update applicable candidate",
            createCandidateEvent(identifier, OperationType.MODIFY, true),
            new NviCandidateUpdatedMessage(
                identifier, DataEntryType.CANDIDATE, OperationType.MODIFY),
            EnvironmentFixtures.TOPIC_CANDIDATE_APPLICABLE_UPDATE.getValue()),
        argumentSet(
            "Update candidate to non-applicable",
            createCandidateEvent(identifier, OperationType.MODIFY, false),
            new NviCandidateUpdatedMessage(
                identifier, DataEntryType.NON_CANDIDATE, OperationType.MODIFY),
            EnvironmentFixtures.TOPIC_CANDIDATE_NOT_APPLICABLE_UPDATE.getValue()),
        argumentSet(
            "Delete candidate",
            createCandidateEvent(identifier, OperationType.REMOVE, true),
            new NviCandidateUpdatedMessage(
                identifier, DataEntryType.CANDIDATE, OperationType.REMOVE),
            EnvironmentFixtures.TOPIC_CANDIDATE_REMOVE.getValue()));
  }

  private static Stream<Arguments> approvalEventProvider() {
    var identifier = randomUUID();
    return Stream.of(
        argumentSet(
            "Create approval",
            createApprovalStatusEvent(identifier, OperationType.INSERT),
            new NviCandidateUpdatedMessage(
                identifier, DataEntryType.APPROVAL_STATUS, OperationType.INSERT),
            EnvironmentFixtures.TOPIC_APPROVAL_INSERT.getValue()),
        argumentSet(
            "Update approval",
            createApprovalStatusEvent(identifier, OperationType.MODIFY),
            new NviCandidateUpdatedMessage(
                identifier, DataEntryType.APPROVAL_STATUS, OperationType.MODIFY),
            EnvironmentFixtures.TOPIC_APPROVAL_UPDATE.getValue()),
        argumentSet(
            "Delete approval",
            createApprovalStatusEvent(identifier, OperationType.REMOVE),
            new NviCandidateUpdatedMessage(
                identifier, DataEntryType.APPROVAL_STATUS, OperationType.REMOVE),
            EnvironmentFixtures.TOPIC_APPROVAL_REMOVE.getValue()));
  }

  public static Stream<Arguments> otherDaoInsertEventProvider() {
    var randomUniquenessEntry = new CandidateUniquenessEntryDao(randomUUID().toString());
    return Stream.of(
        argumentSet("Create period", eventWithDao(null, randomPeriodDao(), OperationType.INSERT)),
        argumentSet("Create note", eventWithDao(null, randomNoteDao(), OperationType.INSERT)),
        argumentSet(
            "Create uniqueness entry",
            eventWithDao(null, randomUniquenessEntry, OperationType.INSERT)));
  }

  private SQSMessage createValidSqsMessageForCandidate() {
    var candidateIdentifier = randomUUID();
    var dynamoDbEvent = createCandidateEvent(candidateIdentifier, OperationType.INSERT, true);
    dynamoDbEventHandlerContext.handleEvent(dynamoDbEvent);

    var processedEvents =
        getMessageBatchesFromQueue(EnvironmentFixtures.DB_EVENTS_QUEUE_URL.getValue()).getFirst();
    return processedEvents.getRecords().getFirst();
  }

  public static DynamodbEvent createCandidateEventWithEmptyPointsList() {
    var oldImage = randomApplicableCandidateDao();
    var invalidDbCandidate = oldImage.candidate().copy().points(emptyList()).build();
    var newImage =
        oldImage.copy().version(randomUUID().toString()).candidate(invalidDbCandidate).build();
    return eventWithDao(oldImage, newImage, OperationType.MODIFY);
  }

  public static DynamodbEvent createDynamoDbEventWithMissingIdentifier() {
    var streamRecord = new StreamRecord();
    streamRecord.setOldImage(Map.of(randomString(), new AttributeValue(randomString())));
    var dynamoDbEvent = new DynamodbEvent();
    var dynamoDbRecord = dynamoRecord(streamRecord, OperationType.MODIFY);
    dynamoDbEvent.setRecords(List.of(dynamoDbRecord));
    return dynamoDbEvent;
  }
}
