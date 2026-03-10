package no.sikt.nva.nvi.index;

import static java.util.Objects.nonNull;

import com.amazonaws.services.lambda.runtime.Context;
import com.amazonaws.services.lambda.runtime.RequestHandler;
import com.amazonaws.services.lambda.runtime.events.SQSBatchResponse;
import com.amazonaws.services.lambda.runtime.events.SQSEvent;
import com.fasterxml.jackson.core.JsonProcessingException;
import java.net.URI;
import java.util.ArrayList;
import java.util.UUID;
import no.sikt.nva.nvi.common.S3StorageReader;
import no.sikt.nva.nvi.common.StorageReader;
import no.sikt.nva.nvi.common.StorageWriter;
import no.sikt.nva.nvi.common.queue.DynamoDbChangeMessage;
import no.sikt.nva.nvi.common.queue.NviQueueClient;
import no.sikt.nva.nvi.common.queue.QueueClient;
import no.sikt.nva.nvi.common.queue.QueueMessage;
import no.sikt.nva.nvi.common.service.CandidateService;
import no.sikt.nva.nvi.common.service.model.Candidate;
import no.sikt.nva.nvi.index.aws.S3StorageWriter;
import no.sikt.nva.nvi.index.model.IndexCandidateMessage;
import no.sikt.nva.nvi.index.model.PersistedIndexDocumentMessage;
import no.sikt.nva.nvi.index.model.PersistedResource;
import no.sikt.nva.nvi.index.model.document.IndexDocumentWithConsumptionAttributes;
import no.unit.nva.auth.uriretriever.UriRetriever;
import nva.commons.core.Environment;
import nva.commons.core.JacocoGenerated;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class IndexDocumentHandler implements RequestHandler<SQSEvent, SQSBatchResponse> {

  private static final Logger LOGGER = LoggerFactory.getLogger(IndexDocumentHandler.class);
  private static final String INDEX_DLQ = "INDEX_DLQ";
  private static final String EXPANDED_RESOURCES_BUCKET = "EXPANDED_RESOURCES_BUCKET";
  private static final String QUEUE_URL = "PERSISTED_INDEX_DOCUMENT_QUEUE_URL";
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
  public SQSBatchResponse handleRequest(SQSEvent event, Context context) {
    LOGGER.info("Processing event with {} messages", event.getRecords().size());
    var failedMessages = new ArrayList<SQSBatchResponse.BatchItemFailure>();

    for (var sqsMessage : event.getRecords()) {
      try {
        var changeMessage = DynamoDbChangeMessage.from(sqsMessage.getBody());
        createIndexDocument(changeMessage.candidateIdentifier());
      } catch (JsonProcessingException exception) {
        LOGGER.error("Failed to process message {}", sqsMessage, exception);
        failedMessages.add(new SQSBatchResponse.BatchItemFailure(sqsMessage.getMessageId()));
      }
    }

    LOGGER.info("Event processed with {} failures", failedMessages.size());
    return new SQSBatchResponse(failedMessages);
  }

  // Catching all exceptions because we want to handle it regardless of what the error is
  @SuppressWarnings("PMD.AvoidCatchingGenericException")
  private void createIndexDocument(UUID candidateIdentifier) {
    try {
      var document = generateIndexDocumentWithConsumptionAttributes(candidateIdentifier);
      if (nonNull(document)) {
        var docUri = document.persist(storageWriter);
        sqsClient.sendMessage(new PersistedIndexDocumentMessage(docUri).toJsonString(), queueUrl);
      }
    } catch (Exception exception) {
      handleFailure(exception, candidateIdentifier);
    }
  }

  private PersistedResource fetchPersistedResource(Candidate candidate) {
    return PersistedResource.fromUri(
        candidate.publicationDetails().publicationBucketUri(), storageReader);
  }

  private IndexDocumentWithConsumptionAttributes generateIndexDocumentWithConsumptionAttributes(
      UUID candidateIdentifier) {
    var candidate = candidateService.getCandidateByIdentifier(candidateIdentifier);
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

  private void handleFailure(Exception exception, UUID candidateIdentifier) {
    LOGGER.error(
        "Failed to generate index document for candidate {}", candidateIdentifier, exception);
    var reindexCandidateMessage =
        QueueMessage.builder()
            .withBody(new IndexCandidateMessage(candidateIdentifier))
            .withCandidateIdentifier(candidateIdentifier)
            .withErrorContext(exception)
            .build();
    sqsClient.sendMessage(reindexCandidateMessage, dlqUrl);
  }
}
