package no.sikt.nva.nvi.index;

import static no.sikt.nva.nvi.common.utils.ExceptionUtils.getStackTrace;
import static no.unit.nva.commons.json.JsonUtils.dtoObjectMapper;
import static nva.commons.core.attempt.Try.attempt;

import com.amazonaws.services.lambda.runtime.Context;
import com.amazonaws.services.lambda.runtime.RequestHandler;
import com.amazonaws.services.lambda.runtime.events.SQSEvent;
import com.amazonaws.services.lambda.runtime.events.SQSEvent.SQSMessage;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.net.URI;
import java.util.Objects;
import no.sikt.nva.nvi.common.S3StorageReader;
import no.sikt.nva.nvi.common.StorageReader;
import no.sikt.nva.nvi.common.queue.NviQueueClient;
import no.sikt.nva.nvi.common.queue.QueueClient;
import no.sikt.nva.nvi.index.aws.OpenSearchClient;
import no.sikt.nva.nvi.index.model.PersistedIndexDocumentMessage;
import no.sikt.nva.nvi.index.model.document.IndexDocumentWithConsumptionAttributes;
import no.sikt.nva.nvi.index.model.document.NviCandidateIndexDocument;
import nva.commons.core.Environment;
import nva.commons.core.JacocoGenerated;
import nva.commons.core.attempt.Failure;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class UpdateIndexHandler implements RequestHandler<SQSEvent, Void> {

  public static final String FAILED_TO_ADD_DOCUMENT_TO_INDEX =
      "Failed to add document to index: {}";
  public static final String INDEX_DLQ = "INDEX_DLQ";
  private static final Logger LOGGER = LoggerFactory.getLogger(UpdateIndexHandler.class);
  private static final String FAILED_TO_MAP_BODY_MESSAGE =
      "Failed to map body to PersistedIndexDocumentMessage: {}";
  private static final String FAILED_TO_FETCH_DOCUMENT_MESSAGE =
      "Failed to fetch document from S3: {}";
  private static final String ERROR_MESSAGE = "Error message: {}";
  private static final String EXPANDED_RESOURCES_BUCKET = "EXPANDED_RESOURCES_BUCKET";
  private static final String EXCEPTION_FIELD = "exception";
  private final OpenSearchClient openSearchClient;
  private final StorageReader<URI> storageReader;
  private final QueueClient queueClient;
  private final String dlqUrl;

  @JacocoGenerated
  public UpdateIndexHandler() {
    this(
        OpenSearchClient.defaultOpenSearchClient(),
        new S3StorageReader(new Environment().readEnv(EXPANDED_RESOURCES_BUCKET)),
        new NviQueueClient());
  }

  public UpdateIndexHandler(
      OpenSearchClient openSearchClient,
      StorageReader<URI> storageReader,
      QueueClient queueClient) {
    this.openSearchClient = openSearchClient;
    this.storageReader = storageReader;
    this.queueClient = queueClient;
    this.dlqUrl = new Environment().readEnv(INDEX_DLQ);
  }

  @Override
  public Void handleRequest(SQSEvent input, Context context) {
    input.getRecords().stream()
        .map(SQSMessage::getBody)
        .map(this::extractDocumentUriFromBody)
        .filter(Objects::nonNull)
        .map(this::fetchDocument)
        .filter(Objects::nonNull)
        .forEach(this::addDocumentToIndex);
    return null;
  }

  private static IndexDocumentWithConsumptionAttributes parseBlob(String blob) {
    return attempt(
            () -> dtoObjectMapper.readValue(blob, IndexDocumentWithConsumptionAttributes.class))
        .orElseThrow();
  }

  private static void logFailure(String message, String messageArgument, Exception exception) {
    LOGGER.error(message, messageArgument);
    LOGGER.error(ERROR_MESSAGE, getStackTrace(exception));
  }

  private URI extractDocumentUriFromBody(String body) {
    return attempt(
            () ->
                dtoObjectMapper.readValue(body, PersistedIndexDocumentMessage.class).documentUri())
        .orElse(
            failure -> {
              handleFailure(failure, body, FAILED_TO_MAP_BODY_MESSAGE);
              return null;
            });
  }

  private void handleFailure(Failure<?> failure, String body, String logMessage) {
    var exception = failure.getException();
    logFailure(logMessage, body, exception);
    var messageWithError = injectExceptionIntoJson(body, exception);
    queueClient.sendMessage(messageWithError, dlqUrl);
  }

  private String injectExceptionIntoJson(String jsonMessage, Exception exception) {
    return attempt(() -> dtoObjectMapper.readTree(jsonMessage))
        .map(ObjectNode.class::cast)
        .map(tree -> tree.put(EXCEPTION_FIELD, getStackTrace(exception)))
        .map(ObjectNode::toString)
        .orElse(failure -> jsonMessage);
  }

  private void addDocumentToIndex(NviCandidateIndexDocument document) {
    attempt(() -> openSearchClient.addDocumentToIndex(document))
        .orElse(
            failure -> {
              handleFailure(
                  failure,
                  new PersistedIndexDocumentMessage(document.id()).toJsonString(),
                  FAILED_TO_ADD_DOCUMENT_TO_INDEX);
              return null;
            });
  }

  private NviCandidateIndexDocument fetchDocument(URI documentUri) {
    return attempt(() -> parseBlob(storageReader.read(documentUri)).indexDocument())
        .orElse(
            failure -> {
              handleFailure(
                  failure,
                  new PersistedIndexDocumentMessage(documentUri).toJsonString(),
                  FAILED_TO_FETCH_DOCUMENT_MESSAGE);
              return null;
            });
  }
}
