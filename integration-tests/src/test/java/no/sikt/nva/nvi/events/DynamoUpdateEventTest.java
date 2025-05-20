package no.sikt.nva.nvi.events;

import static java.util.UUID.randomUUID;
import static no.sikt.nva.nvi.common.DynamoDbTestUtils.eventWithCandidate;
import static no.sikt.nva.nvi.common.DynamoDbTestUtils.randomEventWithNumberOfDynamoRecords;
import static no.sikt.nva.nvi.common.EnvironmentFixtures.getDataEntryUpdateHandlerEnvironment;
import static no.sikt.nva.nvi.common.EnvironmentFixtures.getDynamoDbEventToQueueHandlerEnvironment;
import static no.sikt.nva.nvi.common.db.DbApprovalStatusFixtures.randomApproval;
import static no.sikt.nva.nvi.common.db.DbCandidateFixtures.randomCandidate;
import static no.unit.nva.testutils.RandomDataGenerator.objectMapper;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.params.provider.Arguments.argumentSet;
import static org.mockito.Mockito.mock;

import com.amazonaws.services.lambda.runtime.Context;
import com.amazonaws.services.lambda.runtime.events.DynamodbEvent;
import com.amazonaws.services.lambda.runtime.events.SQSEvent;
import com.amazonaws.services.lambda.runtime.events.SQSEvent.SQSMessage;
import com.fasterxml.jackson.core.JsonProcessingException;
import java.util.List;
import java.util.UUID;
import java.util.stream.Stream;
import no.sikt.nva.nvi.common.db.ApprovalStatusDao;
import no.sikt.nva.nvi.common.db.CandidateDao;
import no.sikt.nva.nvi.common.queue.DataEntryType;
import no.sikt.nva.nvi.common.queue.FakeSqsClient;
import no.sikt.nva.nvi.common.queue.NviCandidateUpdatedMessage;
import no.sikt.nva.nvi.events.db.DataEntryUpdateHandler;
import no.sikt.nva.nvi.events.db.DynamoDbEventToQueueHandler;
import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import software.amazon.awssdk.services.dynamodb.model.OperationType;
import software.amazon.awssdk.services.sqs.model.SendMessageBatchRequest;
import software.amazon.awssdk.services.sqs.model.SendMessageBatchRequestEntry;

class DynamoUpdateEventTest {

  public static final Context DYNAMO_DB_EVENT_HANDLER_CONTEXT = mock(Context.class);
  public static final Context DATA_ENTRY_UPDATE_HANDLER_CONTEXT = mock(Context.class);
  public static final String DLQ_URL = "IndexDlq";
  private DynamoDbEventToQueueHandler dynamoDbEventToQueueHandler;
  private FakeSqsClient dbEventQueueClient;
  private FakeNotificationClient snsClient;
  private DataEntryUpdateHandler dataEntryUpdateHandler;

  private void handleDynamoDbEvent(DynamodbEvent dynamoDbEvent) {
    dynamoDbEventToQueueHandler.handleRequest(dynamoDbEvent, DYNAMO_DB_EVENT_HANDLER_CONTEXT);
  }

  @BeforeEach
  void init() {
    dbEventQueueClient = new FakeSqsClient();
    snsClient = new FakeNotificationClient();
    dynamoDbEventToQueueHandler =
        new DynamoDbEventToQueueHandler(
            dbEventQueueClient, getDynamoDbEventToQueueHandlerEnvironment());
    dataEntryUpdateHandler =
        new DataEntryUpdateHandler(
            snsClient, getDataEntryUpdateHandlerEnvironment(), dbEventQueueClient);
  }

  @Test
  void shouldSendMessageBatchWithSize10() {
    var dynamoDbEvent = randomEventWithNumberOfDynamoRecords(11);
    dynamoDbEventToQueueHandler.handleRequest(dynamoDbEvent, DYNAMO_DB_EVENT_HANDLER_CONTEXT);

    dbEventQueueClient.getSentBatches().stream()
        .map(DynamoUpdateEventTest::mapDbQueueEntryToSqsEvent)
        .forEach(
            updateEvent ->
                dataEntryUpdateHandler.handleRequest(
                    updateEvent, DATA_ENTRY_UPDATE_HANDLER_CONTEXT));

    var publishedMessages = snsClient.getPublishedMessages();
    assertEquals(-1, snsClient.getPublishedMessages().size());
  }

  @ParameterizedTest
  @MethodSource("dbUpdateEventProvider")
  void shouldNotFail(DynamodbEvent dynamoDbEvent, NviCandidateUpdatedMessage expectedMessage) {
    dynamoDbEventToQueueHandler.handleRequest(dynamoDbEvent, DYNAMO_DB_EVENT_HANDLER_CONTEXT);

    dbEventQueueClient.getSentBatches().stream()
        .map(DynamoUpdateEventTest::mapDbQueueEntryToSqsEvent)
        .forEach(
            updateEvent ->
                dataEntryUpdateHandler.handleRequest(
                    updateEvent, DATA_ENTRY_UPDATE_HANDLER_CONTEXT));

    var publishedMessages = snsClient.getPublishedMessages();
    assertEquals(-1, snsClient.getPublishedMessages().size());

    //    var updateEvent = dbEventQueueClient.getSentBatches().getFirst();
    //    dataEntryUpdateHandler.handleRequest(updateEvent, DATA_ENTRY_UPDATE_HANDLER_CONTEXT);
    //    var actualMessages = extractUpdateMessagesAtIndex(0);
    //    Assertions.assertThat(actualMessages).isEqualTo(List.of(expectedMessage));
  }

  private static SQSEvent mapDbQueueEntryToSqsEvent(SendMessageBatchRequest messageBatch) {
    var event = new SQSEvent();
    var messages =
        messageBatch.entries().stream()
            .map(DynamoUpdateEventTest::mapDbQueueEntryToSqsMessage)
            .toList();
    event.setRecords(messages);
    return event;
  }

  private static SQSMessage mapDbQueueEntryToSqsMessage(SendMessageBatchRequestEntry entry) {
    var message = new SQSMessage();
    message.setBody(entry.messageBody());
    return message;
  }

  @ParameterizedTest
  @MethodSource("dbUpdateEventProvider")
  void shouldMapDbEventToUpdateMessage(
      DynamodbEvent dynamoDbEvent, NviCandidateUpdatedMessage expectedMessage) {
    dynamoDbEventToQueueHandler.handleRequest(dynamoDbEvent, DYNAMO_DB_EVENT_HANDLER_CONTEXT);
    var actualMessages = extractUpdateMessagesAtIndex(0);
    Assertions.assertThat(actualMessages).isEqualTo(List.of(expectedMessage));
  }

  private static Stream<Arguments> dbUpdateEventProvider() {
    var identifier = randomUUID();
    return Stream.of(
        argumentSet(
            "Create candidate",
            createCandidateEvent(identifier, OperationType.INSERT),
            new NviCandidateUpdatedMessage(
                identifier, new DataEntryType("CANDIDATE"), OperationType.INSERT)),
        argumentSet(
            "Update candidate",
            createCandidateEvent(identifier, OperationType.MODIFY),
            new NviCandidateUpdatedMessage(
                identifier, new DataEntryType("CANDIDATE"), OperationType.MODIFY)),
        argumentSet(
            "Delete candidate",
            createCandidateEvent(identifier, OperationType.REMOVE),
            new NviCandidateUpdatedMessage(
                identifier, new DataEntryType("CANDIDATE"), OperationType.REMOVE)),
        argumentSet(
            "Create approval",
            createApprovalStatusEvent(identifier, OperationType.INSERT),
            new NviCandidateUpdatedMessage(
                identifier, new DataEntryType("APPROVAL_STATUS"), OperationType.INSERT)),
        argumentSet(
            "Update approval",
            createApprovalStatusEvent(identifier, OperationType.MODIFY),
            new NviCandidateUpdatedMessage(
                identifier, new DataEntryType("APPROVAL_STATUS"), OperationType.MODIFY)),
        argumentSet(
            "Delete approval",
            createApprovalStatusEvent(identifier, OperationType.REMOVE),
            new NviCandidateUpdatedMessage(
                identifier, new DataEntryType("APPROVAL_STATUS"), OperationType.REMOVE)));
  }

  private static DynamodbEvent createCandidateEvent(UUID identifier, OperationType operationType) {
    var dbCandidate = randomCandidate();
    var dao = CandidateDao.builder().identifier(identifier).candidate(dbCandidate);
    var oldImage = dao.version(randomUUID().toString()).build();
    var newImage = dao.version(randomUUID().toString()).build();
    return eventWithCandidate(oldImage, newImage, operationType);
  }

  private static DynamodbEvent createApprovalStatusEvent(
      UUID identifier, OperationType operationType) {
    var data = randomApproval();
    var dao = ApprovalStatusDao.builder().identifier(identifier).approvalStatus(data);
    var oldImage = dao.version(randomUUID().toString()).build();
    var newImage = dao.version(randomUUID().toString()).build();
    return eventWithCandidate(oldImage, newImage, operationType);
  }

  private List<String> extractBatchEntryMessageBodiesAtIndex(int index) {
    return dbEventQueueClient.getSentBatches().get(index).entries().stream()
        .map(SendMessageBatchRequestEntry::messageBody)
        .toList();
  }

  private List<NviCandidateUpdatedMessage> extractUpdateMessagesAtIndex(int index) {
    var messages = extractBatchEntryMessageBodiesAtIndex(index);
    return messages.stream().map(this::mapToUpdateMessage).toList();
  }

  private NviCandidateUpdatedMessage mapToUpdateMessage(String body) {
    try {
      return objectMapper.readValue(body, NviCandidateUpdatedMessage.class);
    } catch (JsonProcessingException e) {
      var message =
          String.format("Failed to map message body to NviCandidateUpdatedMessage: %s", body);
      throw new RuntimeException(message);
    }
  }
}
