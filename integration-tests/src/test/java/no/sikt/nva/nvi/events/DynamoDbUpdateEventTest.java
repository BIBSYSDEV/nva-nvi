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
import static no.sikt.nva.nvi.common.db.NoteDaoFixtures.randomNoteDao;
import static no.sikt.nva.nvi.common.model.NviPeriodFixtures.openPeriod;
import static no.unit.nva.testutils.RandomDataGenerator.randomString;
import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.params.provider.Arguments.argumentSet;

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
import no.sikt.nva.nvi.common.queue.DynamoDbChangeMessage;
import no.sikt.nva.nvi.common.queue.FakeSqsClient;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.MethodSource;
import software.amazon.awssdk.services.dynamodb.model.OperationType;
import software.amazon.awssdk.services.sns.model.PublishRequest;

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

    var sqsEvents = sharedQueueClient.getSentBatches();
    assertThat(sqsEvents)
        .hasSize(2)
        .extracting(batch -> batch.entries().size())
        .containsSequence(10, 5);
  }

  @ParameterizedTest
  @MethodSource("candidateEventProvider")
  void shouldPassCandidateEventsToCorrectSnsTopic(
      DynamodbEvent dynamoDbEvent, DynamoDbChangeMessage expectedMessage, String expectedTopic) {
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
      DynamodbEvent dynamoDbEvent, DynamoDbChangeMessage expectedMessage, String expectedTopic) {
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
    assertThat(getDbEventMessages()).isEmpty();
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
    sharedQueueClient.disableDestinationQueue(EnvironmentFixtures.DB_EVENTS_QUEUE_URL.getValue());

    assertThrows(RuntimeException.class, () -> processDynamoEvent(dynamoDbEvent));

    assertThat(getDlqMessages())
        .hasSize(1)
        .extracting(SQSMessage::getBody)
        .allMatch(message -> message.contains(candidateIdentifier.toString()));
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
    var queuedEvents = getDbEventMessages();
    var sqsMessage = new SQSEvent();
    sqsMessage.setRecords(queuedEvents);
    dataEntryUpdateHandlerContext.handleEvent(sqsMessage);
  }

  private List<SQSMessage> getDlqMessages() {
    return sharedQueueClient.getAllSentSqsEvents(EnvironmentFixtures.INDEX_DLQ.getValue());
  }

  private List<SQSMessage> getDbEventMessages() {
    return sharedQueueClient.getAllSentSqsEvents(
        EnvironmentFixtures.DB_EVENTS_QUEUE_URL.getValue());
  }

  private static Predicate<PublishRequest> hasTopic(String expectedTopic) {
    return actualMessage -> expectedTopic.equals(actualMessage.topicArn());
  }

  private static Predicate<PublishRequest> hasMessageBody(DynamoDbChangeMessage expectedMessage) {
    try {
      var expectedJsonBody = expectedMessage.toJsonString();
      return actualMessage -> expectedJsonBody.equals(actualMessage.message());
    } catch (JsonProcessingException e) {
      throw new RuntimeException(e);
    }
  }

  private static Stream<Arguments> candidateEventProvider() {
    var identifier = randomUUID();
    return Stream.of(
        argumentSet(
            "Create candidate",
            createCandidateEvent(identifier, OperationType.INSERT, true),
            new DynamoDbChangeMessage(identifier, DataEntryType.CANDIDATE, OperationType.INSERT),
            EnvironmentFixtures.TOPIC_CANDIDATE_INSERT.getValue()),
        argumentSet(
            "Update applicable candidate",
            createCandidateEvent(identifier, OperationType.MODIFY, true),
            new DynamoDbChangeMessage(identifier, DataEntryType.CANDIDATE, OperationType.MODIFY),
            EnvironmentFixtures.TOPIC_CANDIDATE_APPLICABLE_UPDATE.getValue()),
        argumentSet(
            "Update candidate to non-applicable",
            createCandidateEvent(identifier, OperationType.MODIFY, false),
            new DynamoDbChangeMessage(
                identifier, DataEntryType.NON_CANDIDATE, OperationType.MODIFY),
            EnvironmentFixtures.TOPIC_CANDIDATE_NOT_APPLICABLE_UPDATE.getValue()),
        argumentSet(
            "Delete candidate",
            createCandidateEvent(identifier, OperationType.REMOVE, true),
            new DynamoDbChangeMessage(identifier, DataEntryType.CANDIDATE, OperationType.REMOVE),
            EnvironmentFixtures.TOPIC_CANDIDATE_REMOVE.getValue()));
  }

  private static Stream<Arguments> approvalEventProvider() {
    var identifier = randomUUID();
    return Stream.of(
        argumentSet(
            "Create approval",
            createApprovalStatusEvent(identifier, OperationType.INSERT),
            new DynamoDbChangeMessage(
                identifier, DataEntryType.APPROVAL_STATUS, OperationType.INSERT),
            EnvironmentFixtures.TOPIC_APPROVAL_INSERT.getValue()),
        argumentSet(
            "Update approval",
            createApprovalStatusEvent(identifier, OperationType.MODIFY),
            new DynamoDbChangeMessage(
                identifier, DataEntryType.APPROVAL_STATUS, OperationType.MODIFY),
            EnvironmentFixtures.TOPIC_APPROVAL_UPDATE.getValue()),
        argumentSet(
            "Delete approval",
            createApprovalStatusEvent(identifier, OperationType.REMOVE),
            new DynamoDbChangeMessage(
                identifier, DataEntryType.APPROVAL_STATUS, OperationType.REMOVE),
            EnvironmentFixtures.TOPIC_APPROVAL_REMOVE.getValue()));
  }

  public static Stream<Arguments> otherDaoInsertEventProvider() {
    var randomUniquenessEntry = new CandidateUniquenessEntryDao(randomUUID().toString());
    return Stream.of(
        argumentSet(
            "Create period", eventWithDao(null, openPeriod().toDao(), OperationType.INSERT)),
        argumentSet("Create note", eventWithDao(null, randomNoteDao(), OperationType.INSERT)),
        argumentSet(
            "Update note", eventWithDao(randomNoteDao(), randomNoteDao(), OperationType.MODIFY)),
        argumentSet(
            "Create uniqueness entry",
            eventWithDao(null, randomUniquenessEntry, OperationType.INSERT)));
  }

  private SQSMessage createValidSqsMessageForCandidate() {
    var candidateIdentifier = randomUUID();
    var dynamoDbEvent = createCandidateEvent(candidateIdentifier, OperationType.INSERT, true);
    dynamoDbEventHandlerContext.handleEvent(dynamoDbEvent);

    return getDbEventMessages().getFirst();
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
