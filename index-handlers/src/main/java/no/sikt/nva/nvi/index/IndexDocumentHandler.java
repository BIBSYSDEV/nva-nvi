package no.sikt.nva.nvi.index;

import static java.util.Objects.nonNull;
import static no.sikt.nva.nvi.index.utils.SearchConstants.getSearchIndexName;

import com.amazonaws.services.lambda.runtime.Context;
import com.amazonaws.services.lambda.runtime.RequestHandler;
import com.amazonaws.services.lambda.runtime.events.SQSEvent;
import com.amazonaws.services.lambda.runtime.events.SQSEvent.SQSMessage;
import com.fasterxml.jackson.core.JsonProcessingException;
import java.io.IOException;
import java.net.URI;
import java.util.Optional;
import java.util.UUID;
import no.sikt.nva.nvi.common.S3StorageReader;
import no.sikt.nva.nvi.common.StorageWriter;
import no.sikt.nva.nvi.common.exceptions.ValidationException;
import no.sikt.nva.nvi.common.queue.DynamoDbChangeMessage;
import no.sikt.nva.nvi.common.queue.NviQueueClient;
import no.sikt.nva.nvi.common.queue.QueueClient;
import no.sikt.nva.nvi.common.queue.QueueMessage;
import no.sikt.nva.nvi.common.service.CandidateService;
import no.sikt.nva.nvi.common.service.exception.CandidateNotFoundException;
import no.sikt.nva.nvi.common.service.model.Candidate;
import no.sikt.nva.nvi.index.aws.S3StorageWriter;
import no.sikt.nva.nvi.index.mapper.IndexDocumentGenerator;
import no.sikt.nva.nvi.index.model.PersistedIndexDocumentMessage;
import no.sikt.nva.nvi.index.model.document.IndexDocumentWithConsumptionAttributes;
import no.sikt.nva.nvi.publication.PublicationLoaderService;
import nva.commons.core.Environment;
import nva.commons.core.JacocoGenerated;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import software.amazon.awssdk.core.exception.SdkException;

public class IndexDocumentHandler implements RequestHandler<SQSEvent, Void> {

  private static final Logger LOGGER = LoggerFactory.getLogger(IndexDocumentHandler.class);
  private static final String INDEX_DLQ = "INDEX_DLQ";
  private static final String EXPANDED_RESOURCES_BUCKET = "EXPANDED_RESOURCES_BUCKET";
  private static final String QUEUE_URL = "PERSISTED_INDEX_DOCUMENT_QUEUE_URL";
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

  private void processMessage(String body) {
    mapToDbChangeMessage(body)
        .flatMap(changeMessage -> fetchCandidate(changeMessage.candidateIdentifier(), body))
        .filter(IndexDocumentHandler::isApplicable)
        .flatMap(candidate -> generateIndexDocument(candidate, body))
        .flatMap(document -> persistDocument(document, body))
        .ifPresent(documentUri -> sendEvent(documentUri, body));
  }

  private static boolean isApplicable(Candidate candidate) {
    if (candidate.isApplicable()) {
      return true;
    }
    LOGGER.info("Candidate is not applicable, skipping index document generation");
    return false;
  }

  private Optional<DynamoDbChangeMessage> mapToDbChangeMessage(String body) {
    try {
      return Optional.of(DynamoDbChangeMessage.from(body));
    } catch (JsonProcessingException | ValidationException exception) {
      handleFailure(exception, FAILED_TO_PARSE_EVENT_MESSAGE, body, body, null);
      return Optional.empty();
    }
  }

  private Optional<Candidate> fetchCandidate(UUID candidateIdentifier, String body) {
    try {
      return Optional.of(candidateService.getCandidateByIdentifier(candidateIdentifier));
    } catch (CandidateNotFoundException exception) {
      handleFailure(
          exception,
          FAILED_TO_FETCH_CANDIDATE_MESSAGE,
          candidateIdentifier.toString(),
          body,
          candidateIdentifier);
      return Optional.empty();
    }
  }

  private Optional<IndexDocumentWithConsumptionAttributes> generateIndexDocument(
      Candidate candidate, String body) {
    try {
      var indexDocument = indexDocumentGenerator.generate(candidate);
      LOGGER.info(
          "Generated index document for applicable candidate with publication ID: {}",
          candidate.getPublicationId());
      return Optional.of(IndexDocumentWithConsumptionAttributes.from(indexDocument, indexName));
    } catch (SdkException exception) {
      handleFailure(
          exception,
          FAILED_TO_GENERATE_INDEX_DOCUMENT_MESSAGE,
          candidate.identifier().toString(),
          body,
          candidate.identifier());
      return Optional.empty();
    }
  }

  private Optional<URI> persistDocument(
      IndexDocumentWithConsumptionAttributes document, String body) {
    try {
      return Optional.of(document.persist(storageWriter));
    } catch (IOException | SdkException exception) {
      var identifier = document.indexDocument().identifier();
      handleFailure(exception, FAILED_TO_PERSIST_MESSAGE, identifier.toString(), body, identifier);
      return Optional.empty();
    }
  }

  private void sendEvent(URI documentUri, String body) {
    try {
      var message = new PersistedIndexDocumentMessage(documentUri).toJsonString();
      sqsClient.sendMessage(message, queueUrl);
    } catch (SdkException exception) {
      handleFailure(
          exception,
          FAILED_SENDING_EVENT_MESSAGE,
          documentUri.toString(),
          body,
          PersistedIndexDocumentMessage.candidateIdentifierFrom(documentUri));
    }
  }

  private void handleFailure(
      Exception exception,
      String errorMessage,
      String messageArgument,
      String originalBody,
      UUID candidateIdentifier) {
    LOGGER.error(errorMessage, messageArgument, exception);
    var dlqMessage = QueueMessage.builder().withBody(originalBody).withErrorContext(exception);
    if (nonNull(candidateIdentifier)) {
      dlqMessage.withCandidateIdentifier(candidateIdentifier);
    }
    sqsClient.sendMessage(dlqMessage.build(), dlqUrl);
  }
}
