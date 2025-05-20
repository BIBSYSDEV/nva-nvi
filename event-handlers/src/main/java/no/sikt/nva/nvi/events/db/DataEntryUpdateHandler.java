package no.sikt.nva.nvi.events.db;

import static no.sikt.nva.nvi.common.utils.ExceptionUtils.getStackTrace;

import com.amazonaws.services.lambda.runtime.Context;
import com.amazonaws.services.lambda.runtime.RequestHandler;
import com.amazonaws.services.lambda.runtime.events.SQSEvent;
import com.amazonaws.services.lambda.runtime.events.SQSEvent.SQSMessage;
import com.fasterxml.jackson.core.JsonProcessingException;
import no.sikt.nva.nvi.common.notification.NotificationClient;
import no.sikt.nva.nvi.common.notification.NviNotificationClient;
import no.sikt.nva.nvi.common.notification.NviPublishMessageResponse;
import no.sikt.nva.nvi.common.queue.NviCandidateUpdatedMessage;
import no.sikt.nva.nvi.common.queue.NviQueueClient;
import no.sikt.nva.nvi.common.queue.QueueClient;
import nva.commons.core.Environment;
import nva.commons.core.JacocoGenerated;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import software.amazon.awssdk.services.dynamodb.model.OperationType;

public class DataEntryUpdateHandler implements RequestHandler<SQSEvent, Void> {

  private static final Logger LOGGER = LoggerFactory.getLogger(DataEntryUpdateHandler.class);
  private static final String PUBLISHED_MESSAGE = "Published message with id: {} to topic {}";
  private static final String SKIPPING_EVENT_MESSAGE =
      "Skipping event with operation type {} for dao type {}";
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

  @Override
  public Void handleRequest(SQSEvent input, Context context) {
    input.getRecords().stream().map(SQSMessage::getBody).forEach(this::processSqsMessage);
    return null;
  }

  private void processSqsMessage(String body) {
    try {
      var updateMessage = NviCandidateUpdatedMessage.from(body);
      publishUpdateMessage(updateMessage);
    } catch (Exception e) {
      sendToDlq(body, e);
    }
  }

  private void publishUpdateMessage(NviCandidateUpdatedMessage updateMessage)
      throws JsonProcessingException {
    var operationType = updateMessage.operationType();
    var entryType = updateMessage.entryType();
    if (isUnknownOperationType(operationType)) {
      LOGGER.info(SKIPPING_EVENT_MESSAGE, operationType, entryType);
    } else {
      var topic = new DataEntryUpdateTopicProvider(environment).getTopic(updateMessage);
      var messageString = updateMessage.toJsonString();
      var response = snsClient.publish(messageString, topic);
      LOGGER.info(PUBLISHED_MESSAGE, response.messageId(), topic);
    }
  }

  private static boolean isUnknownOperationType(OperationType operationType) {
    return OperationType.UNKNOWN_TO_SDK_VERSION.equals(operationType);
  }

  private void sendToDlq(String body, Exception exception) {
    var message = String.format(DLQ_MESSAGE, body, getStackTrace(exception));
    LOGGER.error(message, body);
    queueClient.sendMessage(message, dlqUrl);
  }
}
