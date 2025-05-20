package no.sikt.nva.nvi.events.db;

import static no.sikt.nva.nvi.common.utils.DynamoDbUtils.extractIdFromRecord;
import static no.sikt.nva.nvi.common.utils.DynamoDbUtils.getImage;
import static no.sikt.nva.nvi.common.utils.ExceptionUtils.getStackTrace;
import static no.unit.nva.commons.json.JsonUtils.dtoObjectMapper;
import static nva.commons.core.attempt.Try.attempt;

import com.amazonaws.services.lambda.runtime.Context;
import com.amazonaws.services.lambda.runtime.RequestHandler;
import com.amazonaws.services.lambda.runtime.events.DynamodbEvent.DynamodbStreamRecord;
import com.amazonaws.services.lambda.runtime.events.SQSEvent;
import com.amazonaws.services.lambda.runtime.events.SQSEvent.SQSMessage;
import java.util.Objects;
import no.sikt.nva.nvi.common.db.ApprovalStatusDao;
import no.sikt.nva.nvi.common.db.CandidateDao;
import no.sikt.nva.nvi.common.db.Dao;
import no.sikt.nva.nvi.common.db.DynamoEntryWithRangeKey;
import no.sikt.nva.nvi.common.notification.NotificationClient;
import no.sikt.nva.nvi.common.notification.NviNotificationClient;
import no.sikt.nva.nvi.common.notification.NviPublishMessageResponse;
import no.sikt.nva.nvi.common.queue.NviCandidateUpdatedMessage;
import no.sikt.nva.nvi.common.queue.NviQueueClient;
import no.sikt.nva.nvi.common.queue.QueueClient;
import nva.commons.core.Environment;
import nva.commons.core.JacocoGenerated;
import nva.commons.core.attempt.Failure;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import software.amazon.awssdk.services.dynamodb.model.OperationType;

public class DataEntryUpdateHandler implements RequestHandler<SQSEvent, Void> {

  private static final Logger LOGGER = LoggerFactory.getLogger(DataEntryUpdateHandler.class);
  private static final String PUBLISHED_MESSAGE = "Published message with id: {} to topic {}";
  private static final String FAILED_TO_PUBLISH_MESSAGE = "Failed to publish message for record {}";
  private static final String FAILED_TO_PARSE_EVENT_MESSAGE =
      "Failed to map body to DynamodbStreamRecord: {}";
  private static final String SKIPPING_EVENT_MESSAGE =
      "Skipping event with operation type {} for dao type {}";
  private static final String ERROR_MESSAGE = "Error message: {}";
  private static final String DLQ_MESSAGE = "Failed to process record %s. Exception: %s ";
  private static final String INDEX_DLQ = "INDEX_DLQ";
  private final NotificationClient<NviPublishMessageResponse> snsClient;
  private final Environment environment;
  private final QueueClient queueClient;
  private final String dlqUrl;

  @JacocoGenerated
  public DataEntryUpdateHandler() {
    this(new NviNotificationClient(), new Environment(), new NviQueueClient());
  }

  public DataEntryUpdateHandler(
      NotificationClient<NviPublishMessageResponse> snsClient,
      Environment environment,
      QueueClient queueClient) {
    this.snsClient = snsClient;
    this.environment = environment;
    this.queueClient = queueClient;
    this.dlqUrl = environment.readEnv(INDEX_DLQ);
  }

  private static boolean isNotCandidateOrApproval(Dao dao) {
    return !(dao instanceof CandidateDao || dao instanceof ApprovalStatusDao);
  }

  private static boolean isUnknownOperationType(OperationType operationType) {
    return OperationType.UNKNOWN_TO_SDK_VERSION.equals(operationType);
  }

  private static void logFailure(String message, String body, Exception exception) {
    LOGGER.error(message, body);
    LOGGER.error(ERROR_MESSAGE, getStackTrace(exception));
  }

  @Override
  public Void handleRequest(SQSEvent input, Context context) {
    input.getRecords().stream()
        .map(SQSMessage::getBody)
        .map(attempt(NviCandidateUpdatedMessage::from))
        .filter(Objects::nonNull)
        .forEach(this::publishToTopic);
    return null;
  }

  private void publishToTopic(NviCandidateUpdatedMessage updateMessage) {
    attempt(() -> publishMessage(updateMessage))
        .orElse(
            failure -> {
              handleFailure(failure, updateMessage);
              return null;
            });
  }

  private NviPublishMessageResponse publishMessage(NviCandidateUpdatedMessage message)
      throws Exception {
    var operationType = message.operationType();
    var dao = message.entryType();
    // FIXME
//    if (isNotCandidateOrApproval(dao) || isUnknownOperationType(operationType)) {
//      LOGGER.info(SKIPPING_EVENT_MESSAGE, operationType, dao.getClass());
//      return null;
//    }
    var topic = new DataEntryUpdateTopicProvider(environment).getTopic(message);
    var response = snsClient.publish(writeAsString(message), topic);
    LOGGER.info(PUBLISHED_MESSAGE, response.messageId(), topic);
    return response;
  }

  private NviPublishMessageResponse extractDaoAndPublish(DynamodbStreamRecord streamRecord)
      throws Exception {
    var operationType = OperationType.fromValue(streamRecord.getEventName());
    var dao = extractDao(streamRecord);
    if (isNotCandidateOrApproval(dao) || isUnknownOperationType(operationType)) {
      LOGGER.info(SKIPPING_EVENT_MESSAGE, operationType, dao.getClass());
      return null;
    }
    return publish(streamRecord, getTopic(operationType, dao));
  }

  private String getTopic(OperationType operationType, Dao dao) {
    return new DataEntryUpdateTopicProvider(environment).getTopic(operationType, dao);
  }

  private NviPublishMessageResponse publish(DynamodbStreamRecord streamRecord, String topic) {
    var response = snsClient.publish(writeAsString(streamRecord), topic);
    LOGGER.info(PUBLISHED_MESSAGE, response.messageId(), topic);
    return response;
  }

  private Dao extractDao(DynamodbStreamRecord streamRecord) throws Exception {
    var image = getImage(streamRecord);
    return attempt(() -> DynamoEntryWithRangeKey.parseAttributeValuesMap(image, Dao.class))
        .orElseThrow(
            daoFailure -> {
              LOGGER.error("Failed to parse image: {}", image.toString());
              return daoFailure.getException();
            });
  }

  private String writeAsString(NviCandidateUpdatedMessage message) {
    return attempt(() -> dtoObjectMapper.writeValueAsString(message)).orElseThrow();
  }

  private NviCandidateUpdatedMessage mapToUpdateMessage(String body) {
    return attempt(() -> dtoObjectMapper.readValue(body, NviCandidateUpdatedMessage.class))
               .orElse(
                   failure -> {
                     handleFailure(failure, body);
                     return null;
                   });
  }

  private void handleFailure(Failure<?> failure, String body) {
    logFailure(FAILED_TO_PARSE_EVENT_MESSAGE, body, failure.getException());
    sendToDlq(body, failure.getException());
  }

  private void handleFailure(Failure<?> failure, DynamodbStreamRecord record) {
    logFailure(FAILED_TO_PUBLISH_MESSAGE, record.toString(), failure.getException());
    sendToDlq(record, failure.getException());
  }

  private void sendToDlq(DynamodbStreamRecord record, Exception exception) {
    var message = String.format(DLQ_MESSAGE, record.toString(), getStackTrace(exception));
    extractIdFromRecord(record)
        .ifPresentOrElse(
            id -> queueClient.sendMessage(message, dlqUrl, id),
            () -> queueClient.sendMessage(message, dlqUrl));
  }

  private void sendToDlq(String body, Exception exception) {
    var message = String.format(DLQ_MESSAGE, body, getStackTrace(exception));
    queueClient.sendMessage(message, dlqUrl);
  }
}
