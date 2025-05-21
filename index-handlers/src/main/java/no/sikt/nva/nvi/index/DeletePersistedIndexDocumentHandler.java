package no.sikt.nva.nvi.index;

import static no.sikt.nva.nvi.common.utils.ExceptionUtils.getStackTrace;
import static nva.commons.core.attempt.Try.attempt;

import com.amazonaws.services.lambda.runtime.Context;
import com.amazonaws.services.lambda.runtime.RequestHandler;
import com.amazonaws.services.lambda.runtime.events.SQSEvent;
import com.amazonaws.services.lambda.runtime.events.SQSEvent.SQSMessage;
import java.io.IOException;
import java.util.Objects;
import java.util.UUID;
import no.sikt.nva.nvi.common.StorageWriter;
import no.sikt.nva.nvi.common.queue.DynamoDbChangeMessage;
import no.sikt.nva.nvi.common.queue.NviQueueClient;
import no.sikt.nva.nvi.common.queue.QueueClient;
import no.sikt.nva.nvi.index.aws.S3StorageWriter;
import no.sikt.nva.nvi.index.model.document.IndexDocumentWithConsumptionAttributes;
import nva.commons.core.Environment;
import nva.commons.core.JacocoGenerated;
import nva.commons.core.attempt.Failure;
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
    input.getRecords().stream()
        .map(SQSMessage::getBody)
        .map(this::mapToDbChangeMessage)
        .filter(Objects::nonNull)
        .map(DynamoDbChangeMessage::candidateIdentifier)
        .filter(Objects::nonNull)
        .forEach(this::deletePersistedIndexDocument);
    return null;
  }

  private static void logFailure(String message, String messageArgument, Exception exception) {
    LOGGER.error(message, messageArgument);
    LOGGER.error(ERROR_MESSAGE, getStackTrace(exception));
  }

  private void deletePersistedIndexDocument(UUID identifier) {
    try {
      storageWriter.delete(identifier);
      LOGGER.info(SUCCESS_INFO_MESSAGE, identifier);
    } catch (S3Exception | IOException exception) {
      handleFailure(new Failure<>(exception), identifier.toString(), identifier);
    }
  }

  private DynamoDbChangeMessage mapToDbChangeMessage(String body) {
    return attempt(() -> DynamoDbChangeMessage.from(body))
        .orElse(
            failure -> {
              handleFailure(failure, body);
              return null;
            });
  }

  private void handleFailure(Failure<?> failure, String messageArgument) {
    logFailure(FAILED_TO_PARSE_EVENT_MESSAGE, messageArgument, failure.getException());
    queueClient.sendMessage(failure.getException().getMessage(), dlqUrl);
  }

  private void handleFailure(Failure<?> failure, String messageArgument, UUID candidateIdentifier) {
    logFailure(FAILED_TO_DELETE_MESSAGE, messageArgument, failure.getException());
    queueClient.sendMessage(getStackTrace(failure.getException()), dlqUrl, candidateIdentifier);
  }
}
