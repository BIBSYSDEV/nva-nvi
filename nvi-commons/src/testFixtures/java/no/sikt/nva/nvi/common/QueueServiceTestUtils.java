package no.sikt.nva.nvi.common;

import static no.sikt.nva.nvi.common.DynamoDbTestUtils.dynamoDbEventWithEmptyPayload;
import static no.sikt.nva.nvi.common.DynamoDbTestUtils.mapToString;
import static no.sikt.nva.nvi.common.utils.DynamoDbUtils.extractField;

import com.amazonaws.services.lambda.runtime.events.DynamodbEvent.DynamodbStreamRecord;
import com.amazonaws.services.lambda.runtime.events.SQSEvent;
import com.amazonaws.services.lambda.runtime.events.SQSEvent.SQSMessage;
import java.util.Collection;
import java.util.List;
import java.util.UUID;
import java.util.stream.Stream;
import no.sikt.nva.nvi.common.db.CandidateDao;
import no.sikt.nva.nvi.common.db.Dao;
import no.sikt.nva.nvi.common.queue.DataEntryType;
import no.sikt.nva.nvi.common.queue.DynamoDbChangeMessage;
import software.amazon.awssdk.services.dynamodb.model.OperationType;
import software.amazon.awssdk.services.sqs.model.SendMessageBatchRequest;
import software.amazon.awssdk.services.sqs.model.SendMessageRequest;

public final class QueueServiceTestUtils {
  private static final String IDENTIFIER_FIELD = "identifier";
  private static final String TYPE_FIELD = "type";

  private QueueServiceTestUtils() {}

  public static SQSEvent from(SendMessageBatchRequest messageBatch) {
    var sqsEvent = new SQSEvent();
    var messages =
        messageBatch.entries().stream()
            .map(
                entry -> {
                  var message = new SQSMessage();
                  message.setBody(entry.messageBody());
                  return message;
                })
            .toList();
    sqsEvent.setRecords(messages);
    return sqsEvent;
  }

  public static SQSEvent from(SendMessageRequest request) {
    var event = new SQSEvent();
    var message = new SQSMessage();
    message.setBody(request.messageBody());
    event.setRecords(List.of(message));
    return event;
  }

  public static SQSEvent createEvent(SQSMessage... sqsMessages) {
    var sqsEvent = new SQSEvent();
    sqsEvent.setRecords(List.of(sqsMessages));
    return sqsEvent;
  }

  public static SQSEvent createEvent(Collection<DynamoDbChangeMessage> updateMessages) {
    var sqsEvent = new SQSEvent();
    var messages = updateMessages.stream().map(QueueServiceTestUtils::createMessage).toList();
    sqsEvent.setRecords(messages);
    return sqsEvent;
  }

  public static SQSEvent createEvent(DynamoDbChangeMessage... updateMessages) {
    return createEvent(List.of(updateMessages));
  }

  public static SQSEvent createEvent(UUID... candidateIdentifiers) {
    var dbMessages =
        Stream.of(candidateIdentifiers).map(QueueServiceTestUtils::createDbChangeMessage).toList();
    return createEvent(dbMessages);
  }

  public static SQSEvent createEvent(Dao oldImage, Dao newImage, OperationType operationType) {
    var dbMessage = createDbChangeMessage(oldImage, newImage, operationType);
    return createEvent(List.of(dbMessage));
  }

  public static SQSEvent createEvent(DynamodbStreamRecord streamRecord) {
    var dbMessage = createDbChangeMessage(streamRecord);
    return createEvent(dbMessage);
  }

  public static SQSEvent createEventWithOneInvalidRecord(UUID candidateIdentifier) {
    var validSqsMessage = createMessage(createDbChangeMessage(candidateIdentifier));
    return createEvent(validSqsMessage, invalidSqsMessage());
  }

  public static SQSEvent createEventWithOneInvalidRecord(CandidateDao dao) {
    var validSqsMessage = createMessage(createDbChangeMessage(null, dao, OperationType.INSERT));
    return createEvent(validSqsMessage, invalidSqsMessage());
  }

  public static SQSEvent createEventWithOneRecordMissingIdentifier(CandidateDao candidate) {
    var validSqsMessage =
        createMessage(createDbChangeMessage(null, candidate, OperationType.INSERT));
    return createEvent(validSqsMessage, messageWithoutIdentifier());
  }

  public static SQSEvent createEventWithOnlyOneRecordMissingIdentifier() {
    return createEvent(messageWithoutIdentifier());
  }

  public static SQSMessage invalidSqsMessage() {
    var message = new SQSMessage();
    message.setBody("invalid indexDocument");
    return message;
  }

  private static SQSMessage createMessage(DynamoDbChangeMessage body) {
    var message = new SQSMessage();
    try {
      message.setBody(body.toJsonString());
    } catch (Exception e) {
      throw new RuntimeException(e);
    }
    return message;
  }

  private static SQSMessage messageWithoutIdentifier() {
    var message = new SQSMessage();
    message.setBody(generateSingleDynamoDbEventRecordWithEmptyPayload());
    return message;
  }

  private static String generateSingleDynamoDbEventRecordWithEmptyPayload() {
    return mapToString(dynamoDbEventWithEmptyPayload().getRecords().getFirst());
  }

  private static DynamoDbChangeMessage createDbChangeMessage(UUID candidateIdentifier) {
    return new DynamoDbChangeMessage(
        candidateIdentifier, DataEntryType.CANDIDATE, OperationType.MODIFY);
  }

  private static DynamoDbChangeMessage createDbChangeMessage(
      Dao oldImage, Dao newImage, OperationType operationType) {
    var entryType = extractField(oldImage, newImage, TYPE_FIELD);
    var identifier = extractField(oldImage, newImage, IDENTIFIER_FIELD);
    return new DynamoDbChangeMessage(
        UUID.fromString(identifier), DataEntryType.parse(entryType), operationType);
  }

  private static DynamoDbChangeMessage createDbChangeMessage(DynamodbStreamRecord streamRecord) {
    var entryType = extractField(streamRecord, TYPE_FIELD);
    var identifier = extractField(streamRecord, IDENTIFIER_FIELD);
    return new DynamoDbChangeMessage(
        UUID.fromString(identifier), DataEntryType.parse(entryType), OperationType.MODIFY);
  }
}
