package no.sikt.nva.nvi.index;

import static java.util.Objects.isNull;
import static java.util.Objects.nonNull;
import static no.sikt.nva.nvi.common.utils.ExceptionUtils.getStackTrace;
import static no.unit.nva.commons.json.JsonUtils.dtoObjectMapper;
import static nva.commons.core.attempt.Try.attempt;

import com.amazonaws.services.lambda.runtime.Context;
import com.amazonaws.services.lambda.runtime.RequestHandler;
import com.amazonaws.services.lambda.runtime.events.SQSEvent;
import com.amazonaws.services.lambda.runtime.events.SQSEvent.SQSMessage;
import java.net.URI;
import no.sikt.nva.nvi.common.S3StorageReader;
import no.sikt.nva.nvi.common.StorageReader;
import no.sikt.nva.nvi.common.queue.NviQueueClient;
import no.sikt.nva.nvi.common.queue.QueueClient;
import no.sikt.nva.nvi.common.queue.QueueMessage;
import no.sikt.nva.nvi.index.aws.CandidateSearchClient;
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
  private final CandidateSearchClient searchClient;
  private final StorageReader<URI> storageReader;
  private final QueueClient queueClient;
  private final String dlqUrl;

  @JacocoGenerated
  public UpdateIndexHandler() {
    this(
        CandidateSearchClient.defaultOpenSearchClient(),
        new S3StorageReader(new Environment().readEnv(EXPANDED_RESOURCES_BUCKET)),
        new NviQueueClient(),
        new Environment());
  }

  public UpdateIndexHandler(
      CandidateSearchClient searchClient,
      StorageReader<URI> storageReader,
      QueueClient queueClient,
      Environment environment) {
    this.searchClient = searchClient;
    this.storageReader = storageReader;
    this.queueClient = queueClient;
    this.dlqUrl = environment.readEnv(INDEX_DLQ);
  }

  @Override
  public Void handleRequest(SQSEvent input, Context context) {
    input.getRecords().stream().map(SQSMessage::getBody).forEach(this::processMessage);
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

  private void processMessage(String body) {
    var documentUri = extractDocumentUriFromBody(body);
    if (isNull(documentUri)) {
      return;
    }
    var document = fetchDocument(documentUri, body);
    if (nonNull(document)) {
      addDocumentToIndex(document, documentUri, body);
    }
  }

  private URI extractDocumentUriFromBody(String body) {
    return attempt(
            () ->
                dtoObjectMapper.readValue(body, PersistedIndexDocumentMessage.class).documentUri())
        .orElse(
            failure -> {
              handleFailure(failure, body, FAILED_TO_MAP_BODY_MESSAGE, null);
              return null;
            });
  }

  private NviCandidateIndexDocument fetchDocument(URI documentUri, String originalBody) {
    return attempt(() -> parseBlob(storageReader.read(documentUri)).indexDocument())
        .orElse(
            failure -> {
              handleFailure(failure, originalBody, FAILED_TO_FETCH_DOCUMENT_MESSAGE, documentUri);
              return null;
            });
  }

  private void addDocumentToIndex(
      NviCandidateIndexDocument document, URI documentUri, String originalBody) {
    attempt(() -> searchClient.addDocumentToIndex(document))
        .orElse(
            failure -> {
              handleFailure(failure, originalBody, FAILED_TO_ADD_DOCUMENT_TO_INDEX, documentUri);
              return null;
            });
  }

  private void handleFailure(
      Failure<?> failure, String originalBody, String logMessage, URI documentUri) {
    var exception = failure.getException();
    logFailure(logMessage, originalBody, exception);
    var dlqMessage = QueueMessage.builder().withBody(originalBody).withErrorContext(exception);
    if (nonNull(documentUri)) {
      attempt(() -> PersistedIndexDocumentMessage.candidateIdentifierFrom(documentUri))
          .toOptional()
          .ifPresent(dlqMessage::withCandidateIdentifier);
    }
    queueClient.sendMessage(dlqMessage.build(), dlqUrl);
  }
}
