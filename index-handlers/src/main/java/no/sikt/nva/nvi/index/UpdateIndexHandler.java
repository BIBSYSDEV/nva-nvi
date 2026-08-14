package no.sikt.nva.nvi.index;

import static no.unit.nva.commons.json.JsonUtils.dtoObjectMapper;

import com.amazonaws.services.lambda.runtime.Context;
import com.amazonaws.services.lambda.runtime.RequestHandler;
import com.amazonaws.services.lambda.runtime.events.SQSEvent;
import com.amazonaws.services.lambda.runtime.events.SQSEvent.SQSMessage;
import com.fasterxml.jackson.core.JsonProcessingException;
import java.net.URI;
import java.util.Optional;
import java.util.UUID;
import no.sikt.nva.nvi.common.S3StorageReader;
import no.sikt.nva.nvi.common.StorageReader;
import no.sikt.nva.nvi.common.queue.NviQueueClient;
import no.sikt.nva.nvi.common.queue.QueueClient;
import no.sikt.nva.nvi.common.queue.QueueMessage;
import no.sikt.nva.nvi.index.aws.CandidateSearchClient;
import no.sikt.nva.nvi.index.aws.SearchClientException;
import no.sikt.nva.nvi.index.model.PersistedIndexDocumentMessage;
import no.sikt.nva.nvi.index.model.document.IndexDocumentWithConsumptionAttributes;
import no.sikt.nva.nvi.index.model.document.NviCandidateIndexDocument;
import nva.commons.core.Environment;
import nva.commons.core.JacocoGenerated;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import software.amazon.awssdk.core.exception.SdkException;

public class UpdateIndexHandler implements RequestHandler<SQSEvent, Void> {

  public static final String FAILED_TO_ADD_DOCUMENT_TO_INDEX =
      "Failed to add document to index: {}";
  public static final String INDEX_DLQ = "INDEX_DLQ";
  private static final Logger LOGGER = LoggerFactory.getLogger(UpdateIndexHandler.class);
  private static final String FAILED_TO_MAP_BODY_MESSAGE =
      "Failed to map body to PersistedIndexDocumentMessage: {}";
  private static final String FAILED_TO_FETCH_DOCUMENT_MESSAGE =
      "Failed to fetch document from S3: {}";
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

  private void processMessage(String body) {
    extractDocumentUri(body)
        .flatMap(documentUri -> fetchDocument(documentUri, body))
        .ifPresent(document -> addDocumentToIndex(document, body));
  }

  private Optional<URI> extractDocumentUri(String body) {
    try {
      var message = dtoObjectMapper.readValue(body, PersistedIndexDocumentMessage.class);
      return Optional.ofNullable(message.documentUri());
    } catch (JsonProcessingException exception) {
      handleFailure(exception, body, FAILED_TO_MAP_BODY_MESSAGE);
      return Optional.empty();
    }
  }

  private Optional<NviCandidateIndexDocument> fetchDocument(URI documentUri, String body) {
    try {
      var blob = storageReader.read(documentUri);
      var document = dtoObjectMapper.readValue(blob, IndexDocumentWithConsumptionAttributes.class);
      return Optional.of(document.indexDocument());
    } catch (SdkException | JsonProcessingException exception) {
      handleFailure(exception, body, FAILED_TO_FETCH_DOCUMENT_MESSAGE);
      return Optional.empty();
    }
  }

  private void addDocumentToIndex(NviCandidateIndexDocument document, String body) {
    try {
      searchClient.addDocumentToIndex(document);
    } catch (SearchClientException exception) {
      handleFailure(exception, body, FAILED_TO_ADD_DOCUMENT_TO_INDEX);
    }
  }

  private void handleFailure(Exception exception, String originalBody, String logMessage) {
    LOGGER.error(logMessage, originalBody, exception);
    var dlqMessage = QueueMessage.builder().withBody(originalBody).withErrorContext(exception);
    extractCandidateIdentifier(originalBody).ifPresent(dlqMessage::withCandidateIdentifier);
    queueClient.sendMessage(dlqMessage.build(), dlqUrl);
  }

  private static Optional<UUID> extractCandidateIdentifier(String body) {
    try {
      var message = dtoObjectMapper.readValue(body, PersistedIndexDocumentMessage.class);
      return Optional.ofNullable(message.documentUri())
          .map(PersistedIndexDocumentMessage::candidateIdentifierFrom);
    } catch (JsonProcessingException | IllegalArgumentException exception) {
      return Optional.empty();
    }
  }
}
