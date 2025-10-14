package no.sikt.nva.nvi.index;

import static no.sikt.nva.nvi.common.utils.ExceptionUtils.getStackTrace;
import static no.sikt.nva.nvi.index.aws.S3StorageWriter.GZIP_ENDING;
import static nva.commons.core.StringUtils.isBlank;
import static nva.commons.core.attempt.Try.attempt;

import com.amazonaws.services.lambda.runtime.Context;
import com.amazonaws.services.lambda.runtime.RequestHandler;
import com.amazonaws.services.lambda.runtime.events.SQSEvent;
import com.amazonaws.services.lambda.runtime.events.SQSEvent.SQSMessage;
import java.net.URI;
import java.util.Objects;
import java.util.UUID;
import no.sikt.nva.nvi.common.S3StorageReader;
import no.sikt.nva.nvi.common.StorageReader;
import no.sikt.nva.nvi.common.StorageWriter;
import no.sikt.nva.nvi.common.queue.DynamoDbChangeMessage;
import no.sikt.nva.nvi.common.queue.NviQueueClient;
import no.sikt.nva.nvi.common.queue.QueueClient;
import no.sikt.nva.nvi.common.service.CandidateService;
import no.sikt.nva.nvi.common.service.model.Candidate;
import no.sikt.nva.nvi.index.aws.S3StorageWriter;
import no.sikt.nva.nvi.index.model.PersistedIndexDocumentMessage;
import no.sikt.nva.nvi.index.model.PersistedResource;
import no.sikt.nva.nvi.index.model.document.IndexDocumentWithConsumptionAttributes;
import no.unit.nva.auth.uriretriever.UriRetriever;
import nva.commons.core.Environment;
import nva.commons.core.JacocoGenerated;
import nva.commons.core.attempt.Failure;
import nva.commons.core.paths.UriWrapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class IndexDocumentHandler implements RequestHandler<SQSEvent, Void> {

  private static final Logger LOGGER = LoggerFactory.getLogger(IndexDocumentHandler.class);
  private static final String INDEX_DLQ = "INDEX_DLQ";
  private static final String EXPANDED_RESOURCES_BUCKET = "EXPANDED_RESOURCES_BUCKET";
  private static final String QUEUE_URL = "PERSISTED_INDEX_DOCUMENT_QUEUE_URL";
  private static final String ERROR_MESSAGE = "Error message: {}";
  private static final String FAILED_SENDING_EVENT_MESSAGE = "Failed to send message to queue: {}";
  private static final String FAILED_TO_PERSIST_MESSAGE = "Failed to save {} in bucket";
  private static final String FAILED_TO_PARSE_EVENT_MESSAGE =
      "Failed to map body to DynamodbStreamRecord: {}";
  private static final String FAILED_TO_FETCH_CANDIDATE_MESSAGE =
      "Failed to fetch candidate with identifier: {}";
  private static final String FAILED_TO_GENERATE_INDEX_DOCUMENT_MESSAGE =
      "Failed to generate index document for candidate with identifier: {}";
  private static final String BLANK_ERROR_MESSAGE_PASSED_TO_ERROR_HANDLER =
      "An unexpected error occurred with a blank message passed to error handler.";
  private final StorageReader<URI> storageReader;
  private final StorageWriter<IndexDocumentWithConsumptionAttributes> storageWriter;
  private final CandidateService candidateService;
  private final UriRetriever uriRetriever;
  private final QueueClient sqsClient;
  private final String queueUrl;
  private final String dlqUrl;

  @JacocoGenerated
  public IndexDocumentHandler() {
    this(
        new S3StorageReader(new Environment().readEnv(EXPANDED_RESOURCES_BUCKET)),
        new S3StorageWriter(new Environment().readEnv(EXPANDED_RESOURCES_BUCKET)),
        new NviQueueClient(),
        CandidateService.defaultCandidateService(),
        new UriRetriever(),
        new Environment());
  }

  public IndexDocumentHandler(
      StorageReader<URI> storageReader,
      StorageWriter<IndexDocumentWithConsumptionAttributes> storageWriter,
      QueueClient sqsClient,
      CandidateService candidateService,
      UriRetriever uriRetriever,
      Environment environment) {
    this.storageReader = storageReader;
    this.storageWriter = storageWriter;
    this.sqsClient = sqsClient;
    this.candidateService = candidateService;
    this.uriRetriever = uriRetriever;
    this.queueUrl = environment.readEnv(QUEUE_URL);
    this.dlqUrl = environment.readEnv(INDEX_DLQ);
  }

  @Override
  public Void handleRequest(SQSEvent input, Context context) {
    LOGGER.info("Received event with {} records", input.getRecords().size());
    input.getRecords().stream()
        .map(SQSMessage::getBody)
        .map(this::mapToDbChangeMessage)
        .filter(Objects::nonNull)
        .map(this::generateIndexDocument)
        .filter(Objects::nonNull)
        .map(this::persistDocument)
        .filter(Objects::nonNull)
        .forEach(this::sendEvent);
    LOGGER.info("Finished processing all records");
    return null;
  }

  private static UUID extractCandidateIdentifier(URI docuemntUri) {
    return UUID.fromString(
        removeGz(UriWrapper.fromUri(docuemntUri).getPath().getLastPathElement()));
  }

  private static String removeGz(String filename) {
    return filename.replace(GZIP_ENDING, "");
  }

  private static void logFailure(String message, String messageArgument, Exception exception) {
    LOGGER.error(message, messageArgument);
    LOGGER.error(ERROR_MESSAGE, getStackTrace(exception));
  }

  private void sendEvent(URI uri) {
    attempt(
            () ->
                sqsClient.sendMessage(
                    new PersistedIndexDocumentMessage(uri).asJsonString(), queueUrl))
        .orElse(
            failure -> {
              handleFailure(
                  failure,
                  FAILED_SENDING_EVENT_MESSAGE,
                  uri.toString(),
                  extractCandidateIdentifier(uri));
              return null;
            });
  }

  private URI persistDocument(IndexDocumentWithConsumptionAttributes document) {
    return attempt(() -> document.persist(storageWriter))
        .orElse(
            failure -> {
              var identifier = document.indexDocument().identifier();
              handleFailure(failure, FAILED_TO_PERSIST_MESSAGE, identifier.toString(), identifier);
              return null;
            });
  }

  private DynamoDbChangeMessage mapToDbChangeMessage(String body) {
    return attempt(() -> DynamoDbChangeMessage.from(body))
        .orElse(
            failure -> {
              handleFailure(failure, body);
              return null;
            });
  }

  private Candidate fetchCandidate(UUID candidateIdentifier) {
    return attempt(() -> candidateService.getByIdentifier(candidateIdentifier))
        .orElse(
            failure -> {
              handleFailure(
                  failure,
                  FAILED_TO_FETCH_CANDIDATE_MESSAGE,
                  candidateIdentifier.toString(),
                  candidateIdentifier);
              return null;
            });
  }

  private PersistedResource fetchPersistedResource(Candidate candidate) {
    return PersistedResource.fromUri(
        candidate.getPublicationDetails().publicationBucketUri(), storageReader);
  }

  private IndexDocumentWithConsumptionAttributes generateIndexDocument(
      DynamoDbChangeMessage message) {
    var identifier = message.candidateIdentifier();
    return attempt(() -> generateIndexDocumentWithConsumptionAttributes(identifier))
        .orElse(
            failure -> {
              handleFailure(
                  failure,
                  FAILED_TO_GENERATE_INDEX_DOCUMENT_MESSAGE,
                  message.toString(),
                  identifier);
              return null;
            });
  }

  private IndexDocumentWithConsumptionAttributes generateIndexDocumentWithConsumptionAttributes(
      UUID candidateIdentifier) {
    var candidate = fetchCandidate(candidateIdentifier);
    if (candidate == null) {
      LOGGER.info("Candidate is null, skipping index document generation");
      return null;
    }
    if (!candidate.isApplicable()) {
      LOGGER.info("Candidate is not applicable, skipping index document generation");
      return null;
    }
    var id = candidate.getPublicationId();
    LOGGER.info("Generated index document for applicable candidate with publication ID: {}", id);
    return generateIndexDocumentWithConsumptionAttributes(candidate);
  }

  private IndexDocumentWithConsumptionAttributes generateIndexDocumentWithConsumptionAttributes(
      Candidate candidate) {
    var persistedResource = fetchPersistedResource(candidate);
    return IndexDocumentWithConsumptionAttributes.from(candidate, persistedResource, uriRetriever);
  }

  private void validateErrorMessage(String message) {
    if (isBlank(message)) {
      LOGGER.error(BLANK_ERROR_MESSAGE_PASSED_TO_ERROR_HANDLER);
      sqsClient.sendMessage(BLANK_ERROR_MESSAGE_PASSED_TO_ERROR_HANDLER, dlqUrl);
    }
  }

  private void handleFailure(Failure<?> failure, String messageArgument) {
    validateErrorMessage(messageArgument);
    logFailure(FAILED_TO_PARSE_EVENT_MESSAGE, messageArgument, failure.getException());
    sqsClient.sendMessage(failure.getException().getMessage(), dlqUrl);
  }

  private void handleFailure(
      Failure<?> failure, String message, String messageArgument, UUID candidateIdentifier) {
    validateErrorMessage(messageArgument);
    logFailure(message, messageArgument, failure.getException());
    sqsClient.sendMessage(failure.getException().getMessage(), dlqUrl, candidateIdentifier);
  }
}
