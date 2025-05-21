package no.sikt.nva.nvi.index;

import static nva.commons.core.attempt.Try.attempt;

import com.amazonaws.services.lambda.runtime.Context;
import com.amazonaws.services.lambda.runtime.RequestHandler;
import com.amazonaws.services.lambda.runtime.events.SQSEvent;
import com.amazonaws.services.lambda.runtime.events.SQSEvent.SQSMessage;
import java.util.Objects;
import java.util.UUID;
import no.sikt.nva.nvi.common.queue.DynamoDbChangeMessage;
import no.sikt.nva.nvi.index.aws.OpenSearchClient;
import nva.commons.core.JacocoGenerated;
import nva.commons.core.attempt.Failure;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class RemoveIndexDocumentHandler implements RequestHandler<SQSEvent, Void> {

  private static final Logger LOGGER = LoggerFactory.getLogger(RemoveIndexDocumentHandler.class);
  private static final String FAILED_TO_REMOVE_DOCUMENT_MESSAGE =
      "Failed to remove document from index: {}";
  private static final String FAILED_TO_PARSE_EVENT_MESSAGE =
      "Failed to map body to DynamodbStreamRecord: {}";
  private static final String ERROR_MESSAGE = "Error message: {}";
  private final OpenSearchClient openSearchClient;

  @JacocoGenerated
  public RemoveIndexDocumentHandler() {
    this(OpenSearchClient.defaultOpenSearchClient());
  }

  public RemoveIndexDocumentHandler(OpenSearchClient openSearchClient) {
    this.openSearchClient = openSearchClient;
  }

  @Override
  public Void handleRequest(SQSEvent input, Context context) {
    input.getRecords().stream()
        .map(SQSMessage::getBody)
        .map(this::mapToDbChangeMessage)
        .filter(Objects::nonNull)
        .map(DynamoDbChangeMessage::candidateIdentifier)
        .filter(Objects::nonNull)
        .forEach(this::removeDocumentFromIndex);
    return null;
  }

  private static void handleFailure(Failure<?> failure, String message, String messageArgument) {
    LOGGER.error(message, messageArgument);
    LOGGER.error(ERROR_MESSAGE, failure.getException().getMessage());
    // TODO: Send message to DLQ
  }

  private void removeDocumentFromIndex(UUID identifier) {
    attempt(() -> openSearchClient.removeDocumentFromIndex(identifier))
        .orElse(
            failure -> {
              handleFailure(failure, FAILED_TO_REMOVE_DOCUMENT_MESSAGE, identifier.toString());
              return null;
            });
  }

  private DynamoDbChangeMessage mapToDbChangeMessage(String body) {
    return attempt(() -> DynamoDbChangeMessage.from(body))
        .orElse(
            failure -> {
              handleFailure(failure, FAILED_TO_PARSE_EVENT_MESSAGE, body);
              return null;
            });
  }
}
