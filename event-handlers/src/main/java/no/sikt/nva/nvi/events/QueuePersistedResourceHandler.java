package no.sikt.nva.nvi.events;

import static java.util.Objects.isNull;

import com.amazonaws.services.lambda.runtime.Context;
import no.sikt.nva.nvi.common.queue.NviQueueClient;
import no.sikt.nva.nvi.common.queue.QueueClient;
import no.sikt.nva.nvi.common.queue.QueueMessage;
import no.sikt.nva.nvi.common.queue.QueueMessageAttributesBuilder;
import no.sikt.nva.nvi.events.model.PersistedResourceMessage;
import no.unit.nva.events.handlers.DestinationsEventBridgeEventHandler;
import no.unit.nva.events.models.AwsEventBridgeDetail;
import no.unit.nva.events.models.AwsEventBridgeEvent;
import no.unit.nva.events.models.EventReference;
import nva.commons.core.Environment;
import nva.commons.core.JacocoGenerated;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class QueuePersistedResourceHandler
    extends DestinationsEventBridgeEventHandler<EventReference, Void> {

  private static final Logger LOGGER = LoggerFactory.getLogger(QueuePersistedResourceHandler.class);
  private static final String QUEUE_PERSISTED_RESOURCE_QUEUE_URL = "PERSISTED_RESOURCE_QUEUE_URL";
  private static final String PERSISTED_RESOURCES_PUBLICATION_FOLDER = "resources";
  private static final String ERROR_MSG = "Invalid EventReference, missing uri: %s";
  private final QueueClient queueClient;
  private final String queueUrl;

  @JacocoGenerated
  public QueuePersistedResourceHandler() {
    this(new NviQueueClient(), new Environment());
  }

  public QueuePersistedResourceHandler(QueueClient queueClient, Environment environment) {
    super(EventReference.class);
    this.queueClient = queueClient;
    this.queueUrl = environment.readEnv(QUEUE_PERSISTED_RESOURCE_QUEUE_URL);
  }

  @Override
  protected Void processInputPayload(
      EventReference input,
      AwsEventBridgeEvent<AwsEventBridgeDetail<EventReference>> event,
      Context context) {
    validateInput(input);
    if (isPublication(input)) {
      queuePersistedResource(input);
    }
    return null;
  }

  private static boolean isPublication(EventReference input) {
    return input.getUri().getPath().contains(PERSISTED_RESOURCES_PUBLICATION_FOLDER);
  }

  private static void validateInput(EventReference input) {
    if (isNull(input.getUri())) {
      LOGGER.error(String.format(ERROR_MSG, input));
      throw new RuntimeException();
    }
  }

  private void queuePersistedResource(EventReference input) {
    LOGGER.info("Adding publication to evaluation queue: {}", input.getUri());
    var messageBody = new PersistedResourceMessage(input.getUri());
    var messageAttributes = QueueMessageAttributesBuilder.fromPublicationBucketUri(input.getUri());
    var message = new QueueMessage(messageBody, messageAttributes);
    queueClient.sendMessage(message, queueUrl);
  }
}
