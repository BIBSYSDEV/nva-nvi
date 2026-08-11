package no.sikt.nva.nvi.index;

import static java.util.Objects.isNull;
import static java.util.Objects.nonNull;
import static no.sikt.nva.nvi.common.utils.ExceptionUtils.getStackTrace;
import static no.sikt.nva.nvi.index.utils.SearchConstants.getSearchIndexName;
import static nva.commons.core.attempt.Try.attempt;

import com.amazonaws.services.lambda.runtime.Context;
import com.amazonaws.services.lambda.runtime.RequestHandler;
import com.amazonaws.services.lambda.runtime.events.SQSEvent;
import com.amazonaws.services.lambda.runtime.events.SQSEvent.SQSMessage;
import java.net.URI;
import java.util.UUID;
import no.sikt.nva.nvi.common.S3StorageReader;
import no.sikt.nva.nvi.common.StorageWriter;
import no.sikt.nva.nvi.common.queue.DynamoDbChangeMessage;
import no.sikt.nva.nvi.common.queue.NviQueueClient;
import no.sikt.nva.nvi.common.queue.QueueClient;
import no.sikt.nva.nvi.common.queue.QueueMessage;
import no.sikt.nva.nvi.common.service.CandidateService;
import no.sikt.nva.nvi.common.service.model.Candidate;
import no.sikt.nva.nvi.index.aws.S3StorageWriter;
import no.sikt.nva.nvi.index.mapper.IndexDocumentGenerator;
import no.sikt.nva.nvi.index.model.PersistedIndexDocumentMessage;
import no.sikt.nva.nvi.index.model.document.IndexDocumentWithConsumptionAttributes;
import no.sikt.nva.nvi.publication.PublicationLoaderService;
import nva.commons.core.Environment;
import nva.commons.core.JacocoGenerated;
import nva.commons.core.attempt.Failure;
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
  private final StorageWriter<IndexDocumentWithConsumptionAttributes> storageWriter;
  private final CandidateService candidateService;
  private final IndexDocumentGenerator indexDocumentGenerator;
  private final QueueClient sqsClient;
  private final String queueUrl;
  private final String dlqUrl;
  private final String indexName;

  @JacocoGenerated
  public IndexDocumentHandler() {
    this(
        new S3StorageWriter(new Environment().readEnv(EXPANDED_RESOURCES_BUCKET)),
        new NviQueueClient(),
        CandidateService.defaultCandidateService(),
        new IndexDocumentGenerator(
            new PublicationLoaderService(
                new S3StorageReader(new Environment().readEnv(EXPANDED_RESOURCES_BUCKET))),
            new Environment()),
        new Environment());
  }

  public IndexDocumentHandler(
      StorageWriter<IndexDocumentWithConsumptionAttributes> storageWriter,
      QueueClient sqsClient,
      CandidateService candidateService,
      IndexDocumentGenerator indexDocumentGenerator,
      Environment environment) {
    this.storageWriter = storageWriter;
    this.sqsClient = sqsClient;
    this.candidateService = candidateService;
    this.indexDocumentGenerator = indexDocumentGenerator;
    this.queueUrl = environment.readEnv(QUEUE_URL);
    this.dlqUrl = environment.readEnv(INDEX_DLQ);
    this.indexName = getSearchIndexName(environment);
  }

  @Override
  public Void handleRequest(SQSEvent input, Context context) {
    LOGGER.info("Received event with {} records", input.getRecords().size());
    input.getRecords().stream().map(SQSMessage::getBody).forEach(this::processMessage);
    LOGGER.info("Finished processing all records");
    return null;
  }

  private static void logFailure(String message, String messageArgument, Exception exception) {
    LOGGER.error(message, messageArgument);
    LOGGER.error(ERROR_MESSAGE, getStackTrace(exception));
  }

  private void processMessage(String body) {
    var changeMessage = mapToDbChangeMessage(body);
    if (isNull(changeMessage)) {
      return;
    }
    var document = generateIndexDocument(changeMessage, body);
    if (isNull(document)) {
      return;
    }
    var documentUri = persistDocument(document, body);
    if (nonNull(documentUri)) {
      sendEvent(documentUri, body);
    }
  }

  private void sendEvent(URI documentUri, String originalBody) {
    attempt(
            () ->
                sqsClient.sendMessage(
                    new PersistedIndexDocumentMessage(documentUri).toJsonString(), queueUrl))
        .orElse(
            failure -> {
              handleFailure(
                  failure,
                  FAILED_SENDING_EVENT_MESSAGE,
                  documentUri.toString(),
                  originalBody,
                  PersistedIndexDocumentMessage.candidateIdentifierFrom(documentUri));
              return null;
            });
  }

  private URI persistDocument(IndexDocumentWithConsumptionAttributes document, String body) {
    return attempt(() -> document.persist(storageWriter))
        .orElse(
            failure -> {
              var identifier = document.indexDocument().identifier();
              handleFailure(
                  failure, FAILED_TO_PERSIST_MESSAGE, identifier.toString(), body, identifier);
              return null;
            });
  }

  private DynamoDbChangeMessage mapToDbChangeMessage(String body) {
    return attempt(() -> DynamoDbChangeMessage.from(body))
        .orElse(
            failure -> {
              handleFailure(failure, FAILED_TO_PARSE_EVENT_MESSAGE, body, body, null);
              return null;
            });
  }

  private Candidate fetchCandidate(UUID candidateIdentifier, String body) {
    return attempt(() -> candidateService.getCandidateByIdentifier(candidateIdentifier))
        .orElse(
            failure -> {
              handleFailure(
                  failure,
                  FAILED_TO_FETCH_CANDIDATE_MESSAGE,
                  candidateIdentifier.toString(),
                  body,
                  candidateIdentifier);
              return null;
            });
  }

  private IndexDocumentWithConsumptionAttributes generateIndexDocument(
      DynamoDbChangeMessage message, String body) {
    var identifier = message.candidateIdentifier();
    return attempt(() -> generateIndexDocumentWithConsumptionAttributes(identifier, body))
        .orElse(
            failure -> {
              handleFailure(
                  failure,
                  FAILED_TO_GENERATE_INDEX_DOCUMENT_MESSAGE,
                  message.toString(),
                  body,
                  identifier);
              return null;
            });
  }

  private IndexDocumentWithConsumptionAttributes generateIndexDocumentWithConsumptionAttributes(
      UUID candidateIdentifier, String body) {
    var candidate = fetchCandidate(candidateIdentifier, body);
    if (isNull(candidate)) {
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
    var indexDocument = indexDocumentGenerator.generate(candidate);
    return IndexDocumentWithConsumptionAttributes.from(indexDocument, indexName);
  }

  private void handleFailure(
      Failure<?> failure,
      String errorMessage,
      String messageArgument,
      String originalBody,
      UUID candidateIdentifier) {
    logFailure(errorMessage, messageArgument, failure.getException());
    var dlqMessage =
        QueueMessage.builder().withBody(originalBody).withErrorContext(failure.getException());
    if (nonNull(candidateIdentifier)) {
      dlqMessage.withCandidateIdentifier(candidateIdentifier);
    }
    sqsClient.sendMessage(dlqMessage.build(), dlqUrl);
  }
}
