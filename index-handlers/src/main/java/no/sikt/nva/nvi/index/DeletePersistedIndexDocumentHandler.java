package no.sikt.nva.nvi.index;

import static java.util.Objects.isNull;
import static java.util.Objects.nonNull;
import static no.sikt.nva.nvi.common.utils.ExceptionUtils.getStackTrace;
import static nva.commons.core.attempt.Try.attempt;

import com.amazonaws.services.lambda.runtime.Context;
import com.amazonaws.services.lambda.runtime.RequestHandler;
import com.amazonaws.services.lambda.runtime.events.SQSEvent;
import com.amazonaws.services.lambda.runtime.events.SQSEvent.SQSMessage;
import java.util.UUID;
import no.sikt.nva.nvi.common.StorageWriter;
import no.sikt.nva.nvi.common.queue.DynamoDbChangeMessage;
import no.sikt.nva.nvi.common.queue.NviQueueClient;
import no.sikt.nva.nvi.common.queue.QueueClient;
import no.sikt.nva.nvi.common.queue.QueueMessage;
import no.sikt.nva.nvi.index.aws.S3StorageWriter;
import no.sikt.nva.nvi.index.model.document.IndexDocumentWithConsumptionAttributes;
import nva.commons.core.Environment;
import nva.commons.core.JacocoGenerated;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import software.amazon.awssdk.services.s3.model.S3Exception;

public class DeletePersistedIndexDocumentHandler implements RequestHandler<SQSEvent, Void> {

  private static final Logger LOGGER =
      LoggerFactory.getLogger(DeletePersistedIndexDocumentHandler.class);
  private static final String INDEX_DLQ = "INDEX_DLQ";
  private static final String EXPANDED_RESOURCES_BUCKET = "EXPANDED_RESOURCES_BUCKET";
  private static final String ERROR_MESSAGE = "Error message: {}";
  private static final String SUCCESS_INFO_MESSAGE = "Successfully deleted file with identifier {}";
  private static final String FAILED_TO_DELETE_MESSAGE = "Failed to delete file with identifier {}";
  private static final String FAILED_TO_PARSE_EVENT_MESSAGE =
      "Failed to map body to DynamodbStreamRecord: {}";
  private final StorageWriter<IndexDocumentWithConsumptionAttributes> storageWriter;
  private final QueueClient queueClient;
  private final String dlqUrl;

  @JacocoGenerated
  public DeletePersistedIndexDocumentHandler() {
    this(
        new S3StorageWriter(new Environment().readEnv(EXPANDED_RESOURCES_BUCKET)),
        new NviQueueClient(),
        new Environment());
  }

  public DeletePersistedIndexDocumentHandler(
      StorageWriter<IndexDocumentWithConsumptionAttributes> storageWriter,
      QueueClient queueClient,
      Environment environment) {
    this.storageWriter = storageWriter;
    this.queueClient = queueClient;
    this.dlqUrl = environment.readEnv(INDEX_DLQ);
  }

  @Override
  public Void handleRequest(SQSEvent input, Context context) {
    input.getRecords().stream().map(SQSMessage::getBody).forEach(this::processMessage);
    return null;
  }

  private static void logFailure(String message, String messageArgument, Exception exception) {
    LOGGER.error(message, messageArgument);
    LOGGER.error(ERROR_MESSAGE, getStackTrace(exception));
  }

  private void processMessage(String body) {
    var changeMessage = mapToDbChangeMessage(body);
    if (isNull(changeMessage) || isNull(changeMessage.candidateIdentifier())) {
      return;
    }
    deletePersistedIndexDocument(changeMessage.candidateIdentifier(), body);
  }

  private void deletePersistedIndexDocument(UUID identifier, String body) {
    try {
      storageWriter.delete(identifier);
      LOGGER.info(SUCCESS_INFO_MESSAGE, identifier);
    } catch (S3Exception exception) {
      logFailure(FAILED_TO_DELETE_MESSAGE, identifier.toString(), exception);
      sendToDlq(body, exception, identifier);
    }
  }

  private DynamoDbChangeMessage mapToDbChangeMessage(String body) {
    return attempt(() -> DynamoDbChangeMessage.from(body))
        .orElse(
            failure -> {
              logFailure(FAILED_TO_PARSE_EVENT_MESSAGE, body, failure.getException());
              sendToDlq(body, failure.getException(), null);
              return null;
            });
  }

  private void sendToDlq(String body, Exception exception, UUID candidateIdentifier) {
    var dlqMessage = QueueMessage.builder().withBody(body).withErrorContext(exception);
    if (nonNull(candidateIdentifier)) {
      dlqMessage.withCandidateIdentifier(candidateIdentifier);
    }
    queueClient.sendMessage(dlqMessage.build(), dlqUrl);
  }
}
