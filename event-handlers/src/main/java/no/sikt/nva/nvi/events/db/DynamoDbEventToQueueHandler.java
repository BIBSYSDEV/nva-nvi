package no.sikt.nva.nvi.events.db;

import static no.sikt.nva.nvi.common.utils.DynamoDbUtils.getImage;
import static no.unit.nva.commons.json.JsonUtils.dtoObjectMapper;
import static nva.commons.core.attempt.Try.attempt;

import com.amazonaws.services.lambda.runtime.Context;
import com.amazonaws.services.lambda.runtime.RequestHandler;
import com.amazonaws.services.lambda.runtime.events.DynamodbEvent;
import com.amazonaws.services.lambda.runtime.events.DynamodbEvent.DynamodbStreamRecord;
import com.amazonaws.services.lambda.runtime.events.models.dynamodb.AttributeValue;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.IntStream;
import java.util.stream.Stream;
import no.sikt.nva.nvi.common.db.CandidateDao;
import no.sikt.nva.nvi.common.db.Dao;
import no.sikt.nva.nvi.common.db.DynamoEntryWithRangeKey;
import no.sikt.nva.nvi.common.queue.DataEntryType;
import no.sikt.nva.nvi.common.queue.DynamoDbChangeMessage;
import no.sikt.nva.nvi.common.queue.NviQueueClient;
import no.sikt.nva.nvi.common.queue.QueueClient;
import no.sikt.nva.nvi.common.queue.QueueMessage;
import nva.commons.core.Environment;
import nva.commons.core.JacocoGenerated;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import software.amazon.awssdk.core.exception.SdkException;
import software.amazon.awssdk.services.dynamodb.model.OperationType;

public class DynamoDbEventToQueueHandler implements RequestHandler<DynamodbEvent, Void> {

  private static final Logger LOGGER = LoggerFactory.getLogger(DynamoDbEventToQueueHandler.class);
  private static final int BATCH_SIZE = 10;
  private static final String DB_EVENTS_QUEUE_URL = "DB_EVENTS_QUEUE_URL";
  private static final String DLQ_URL = "INDEX_DLQ";
  private static final String FAILURE_MESSAGE = "Failure while sending database events to queue";
  private static final String FAILED_MESSAGES_MESSAGE = "Failed messages: {}";
  private static final String SKIPPING_EVENT_MESSAGE =
      "Skipping event with operation type {} for dao type {}";
  private static final String INFO_MESSAGE = "Sent {} messages to queue. Failures: {}";
  private static final String IDENTIFIER_FIELD = "identifier";
  private static final String TYPE_FIELD = "type";
  public final String dlqUrl;
  private final QueueClient queueClient;
  private final String queueUrl;

  @JacocoGenerated
  public DynamoDbEventToQueueHandler() {
    this(new NviQueueClient(), new Environment());
  }

  public DynamoDbEventToQueueHandler(QueueClient queueClient, Environment environment) {
    this.queueClient = queueClient;
    this.queueUrl = environment.readEnv(DB_EVENTS_QUEUE_URL);
    this.dlqUrl = environment.readEnv(DLQ_URL);
  }

  @Override
  public Void handleRequest(DynamodbEvent input, Context context) {
    var messages = mapToUpdateMessages(input);
    splitIntoBatches(messages).forEach(this::sendBatch);
    return null;
  }

  private static List<DynamoDbChangeMessage> mapToUpdateMessages(DynamodbEvent input) {
    return input.getRecords().stream()
        .map(DynamoDbEventToQueueHandler::mapToUpdateMessage)
        .filter(Objects::nonNull)
        .distinct()
        .toList();
  }

  private static DynamoDbChangeMessage mapToUpdateMessage(DynamodbStreamRecord streamRecord) {
    var operationType = OperationType.fromValue(streamRecord.getEventName());
    var entryType = getEntryType(streamRecord);
    if (entryType.shouldBeProcessedForIndexing()) {
      var recordIdentifier = UUID.fromString(extractField(streamRecord, IDENTIFIER_FIELD));
      var dbChangeMessage = new DynamoDbChangeMessage(recordIdentifier, entryType, operationType);
      dbChangeMessage.validate();
      return dbChangeMessage;
    }
    LOGGER.info(SKIPPING_EVENT_MESSAGE, operationType, entryType);
    return null;
  }

  private static DataEntryType getEntryType(DynamodbStreamRecord streamRecord) {
    var image = getImage(streamRecord);
    var dao = DynamoEntryWithRangeKey.parseAttributeValuesMap(image, Dao.class);

    if (dao instanceof CandidateDao candidateDao) {
      var isApplicable = candidateDao.candidate().applicable();
      return isApplicable ? DataEntryType.CANDIDATE : DataEntryType.NON_CANDIDATE;
    }

    return DataEntryType.parse(extractField(streamRecord, TYPE_FIELD));
  }

  private static String extractField(DynamodbStreamRecord streamRecord, String field) {
    var image =
        Optional.ofNullable(streamRecord.getDynamodb().getOldImage())
            .orElse(streamRecord.getDynamodb().getNewImage());
    return Optional.ofNullable(image.get(field)).map(AttributeValue::getS).orElse(null);
  }

  private static String writeAsJsonString(DynamoDbChangeMessage updateMessage) {
    return attempt(() -> dtoObjectMapper.writeValueAsString(updateMessage)).orElseThrow();
  }

  private void sendToDlq(DynamoDbChangeMessage message, Exception exception) {
    var dlqMessage =
        QueueMessage.builder()
            .withBody(writeAsJsonString(message))
            .withErrorContext(exception)
            .withCandidateIdentifier(message.candidateIdentifier())
            .build();
    queueClient.sendMessage(dlqMessage, dlqUrl);
  }

  private void sendBatch(List<DynamoDbChangeMessage> messages) {
    var messageBodies =
        messages.stream().map(DynamoDbEventToQueueHandler::writeAsJsonString).toList();
    try {
      var response = queueClient.sendMessageBatch(messageBodies, queueUrl);
      LOGGER.info(INFO_MESSAGE, messages.size(), response.failed().size());
    } catch (SdkException exception) {
      LOGGER.error(FAILURE_MESSAGE, exception);
      LOGGER.error(FAILED_MESSAGES_MESSAGE, messages);
      messages.forEach(message -> sendToDlq(message, exception));
      throw exception;
    }
  }

  private Stream<List<DynamoDbChangeMessage>> splitIntoBatches(
      List<DynamoDbChangeMessage> messages) {
    var count = messages.size();
    return IntStream.range(0, (count + BATCH_SIZE - 1) / BATCH_SIZE)
        .mapToObj(i -> messages.subList(i * BATCH_SIZE, Math.min((i + 1) * BATCH_SIZE, count)));
  }
}
