package no.sikt.nva.nvi.events.db;

import static no.sikt.nva.nvi.common.utils.DynamoDbUtils.extractIdFromRecord;
import static no.sikt.nva.nvi.common.utils.DynamoDbUtils.getImage;
import static no.sikt.nva.nvi.common.utils.ExceptionUtils.getStackTrace;
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
import nva.commons.core.Environment;
import nva.commons.core.JacocoGenerated;
import nva.commons.core.attempt.Failure;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import software.amazon.awssdk.services.dynamodb.model.OperationType;

public class DynamoDbEventToQueueHandler implements RequestHandler<DynamodbEvent, Void> {

  private static final Logger LOGGER = LoggerFactory.getLogger(DynamoDbEventToQueueHandler.class);
  private static final int BATCH_SIZE = 10;
  private static final String DB_EVENTS_QUEUE_URL = "DB_EVENTS_QUEUE_URL";
  private static final String DLQ_URL = "INDEX_DLQ";
  private static final String DLQ_MESSAGE = "Failed to process record %s. Exception: %s ";
  private static final String FAILURE_MESSAGE = "Failure while sending database events to queue";
  private static final String FAILED_RECORDS_MESSAGE = "Failed records: {}";
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
    attempt(
            () -> {
              splitIntoBatchesAndSend(input);
              return null;
            })
        .orElseThrow(failure -> handleFailure(failure, input.getRecords()));
    return null;
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

  private void splitIntoBatchesAndSend(DynamodbEvent input) {
    var messages =
        input.getRecords().stream()
            .map(DynamoDbEventToQueueHandler::mapToUpdateMessage)
            .filter(Objects::nonNull)
            .map(DynamoDbEventToQueueHandler::writeAsJsonString)
            .toList();
    splitIntoBatches(messages).forEach(this::sendBatch);
  }

  private RuntimeException handleFailure(
      Failure<Object> failure, List<DynamodbStreamRecord> records) {
    LOGGER.error(FAILURE_MESSAGE, failure.getException());
    LOGGER.error(
        FAILED_RECORDS_MESSAGE, records.stream().map(DynamodbStreamRecord::toString).toList());
    records.forEach(streamRecord -> sendToDlq(streamRecord, failure.getException()));
    return new RuntimeException(failure.getException());
  }

  private void sendToDlq(DynamodbStreamRecord streamRecord, Exception exception) {
    var message = String.format(DLQ_MESSAGE, streamRecord.toString(), getStackTrace(exception));
    extractIdFromRecord(streamRecord)
        .ifPresentOrElse(
            id -> queueClient.sendMessage(message, dlqUrl, id),
            () -> queueClient.sendMessage(message, dlqUrl));
  }

  private void sendBatch(List<String> messages) {
    var response = queueClient.sendMessageBatch(messages, queueUrl);
    LOGGER.info(INFO_MESSAGE, messages.size(), response.failed().size());
  }

  private Stream<List<String>> splitIntoBatches(List<String> records) {
    var count = records.size();
    return IntStream.range(0, (count + BATCH_SIZE - 1) / BATCH_SIZE)
        .mapToObj(i -> records.subList(i * BATCH_SIZE, Math.min((i + 1) * BATCH_SIZE, count)));
  }
}
