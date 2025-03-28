package no.sikt.nva.nvi.events.db;

import static no.sikt.nva.nvi.common.utils.DynamoDbUtils.extractIdFromRecord;
import static no.sikt.nva.nvi.common.utils.ExceptionUtils.getStackTrace;
import static no.unit.nva.commons.json.JsonUtils.dtoObjectMapper;
import static nva.commons.core.attempt.Try.attempt;

import com.amazonaws.services.lambda.runtime.Context;
import com.amazonaws.services.lambda.runtime.RequestHandler;
import com.amazonaws.services.lambda.runtime.events.DynamodbEvent;
import com.amazonaws.services.lambda.runtime.events.DynamodbEvent.DynamodbStreamRecord;
import java.util.List;
import java.util.stream.IntStream;
import java.util.stream.Stream;
import no.sikt.nva.nvi.common.queue.NviQueueClient;
import no.sikt.nva.nvi.common.queue.QueueClient;
import nva.commons.core.Environment;
import nva.commons.core.JacocoGenerated;
import nva.commons.core.attempt.Failure;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class DynamoDbEventToQueueHandler implements RequestHandler<DynamodbEvent, Void> {

  private static final Logger LOGGER = LoggerFactory.getLogger(DynamoDbEventToQueueHandler.class);
  private static final int BATCH_SIZE = 10;
  private static final String DB_EVENTS_QUEUE_URL = "DB_EVENTS_QUEUE_URL";
  private static final String DLQ_URL = "INDEX_DLQ";
  private static final String DLQ_MESSAGE = "Failed to process record %s. Exception: %s ";
  private static final String FAILURE_MESSAGE = "Failure while sending database events to queue";
  private static final String FAILED_RECORDS_MESSAGE = "Failed records: {}";
  private static final String INFO_MESSAGE = "Sent {} messages to queue. Failures: {}";
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

  private static List<String> mapToJsonStrings(List<DynamodbStreamRecord> records) {
    return records.stream().map(DynamoDbEventToQueueHandler::writeAsJsonString).toList();
  }

  private static String writeAsJsonString(DynamodbStreamRecord record) {
    return attempt(() -> dtoObjectMapper.writeValueAsString(record)).orElseThrow();
  }

  private void splitIntoBatchesAndSend(DynamodbEvent input) {
    splitIntoBatches(input.getRecords())
        .map(DynamoDbEventToQueueHandler::mapToJsonStrings)
        .forEach(this::sendBatch);
  }

  private RuntimeException handleFailure(
      Failure<Object> failure, List<DynamodbStreamRecord> records) {
    LOGGER.error(FAILURE_MESSAGE, failure.getException());
    LOGGER.error(
        FAILED_RECORDS_MESSAGE, records.stream().map(DynamodbStreamRecord::toString).toList());
    records.forEach(record -> sendToDlq(record, failure.getException()));
    return new RuntimeException(failure.getException());
  }

  private void sendToDlq(DynamodbStreamRecord record, Exception exception) {
    var message = String.format(DLQ_MESSAGE, record.toString(), getStackTrace(exception));
    extractIdFromRecord(record)
        .ifPresentOrElse(
            id -> queueClient.sendMessage(message, dlqUrl, id),
            () -> queueClient.sendMessage(message, dlqUrl));
  }

  private void sendBatch(List<String> messages) {
    var response = queueClient.sendMessageBatch(messages, queueUrl);
    LOGGER.info(INFO_MESSAGE, messages.size(), response.failed().size());
  }

  private Stream<List<DynamodbStreamRecord>> splitIntoBatches(List<DynamodbStreamRecord> records) {
    var count = records.size();
    return IntStream.range(0, (count + BATCH_SIZE - 1) / BATCH_SIZE)
        .mapToObj(i -> records.subList(i * BATCH_SIZE, Math.min((i + 1) * BATCH_SIZE, count)));
  }
}
