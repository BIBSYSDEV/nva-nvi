package no.sikt.nva.nvi.index;

import static no.sikt.nva.nvi.common.db.DynamoRepository.defaultDynamoClient;
import static no.unit.nva.commons.json.JsonUtils.dynamoObjectMapper;
import static nva.commons.core.attempt.Try.attempt;
import com.amazonaws.services.lambda.runtime.Context;
import com.amazonaws.services.lambda.runtime.RequestHandler;
import com.amazonaws.services.lambda.runtime.events.DynamodbEvent.DynamodbStreamRecord;
import com.amazonaws.services.lambda.runtime.events.SQSEvent;
import com.amazonaws.services.lambda.runtime.events.SQSEvent.SQSMessage;
import java.net.URI;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import no.sikt.nva.nvi.common.S3StorageReader;
import no.sikt.nva.nvi.common.StorageReader;
import no.sikt.nva.nvi.common.StorageWriter;
import no.sikt.nva.nvi.common.db.CandidateRepository;
import no.sikt.nva.nvi.common.db.PeriodRepository;
import no.sikt.nva.nvi.common.queue.NviQueueClient;
import no.sikt.nva.nvi.common.queue.QueueClient;
import no.sikt.nva.nvi.common.service.model.Candidate;
import no.sikt.nva.nvi.index.aws.S3StorageWriter;
import no.sikt.nva.nvi.index.model.IndexDocumentWithConsumptionAttributes;
import no.sikt.nva.nvi.index.model.PersistedResource;
import no.unit.nva.auth.uriretriever.UriRetriever;
import nva.commons.core.Environment;
import nva.commons.core.JacocoGenerated;
import nva.commons.core.attempt.Failure;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class IndexDocumentHandler implements RequestHandler<SQSEvent, Void> {

    public static final String FAILED_SENDING_EVENT_MESSAGE = "Failed to send message to queue: {}";
    private static final Logger LOGGER = LoggerFactory.getLogger(IndexDocumentHandler.class);
    private static final String EXPANDED_RESOURCES_BUCKET = "EXPANDED_RESOURCES_BUCKET";
    private static final String QUEUE_URL = "PERSISTED_INDEX_DOCUMENT_QUEUE_URL";
    private static final String IDENTIFIER = "identifier";
    private static final String ERROR_MESSAGE = "Error message: {}";
    private static final String FAILED_TO_PERSIST_MESSAGE = "Failed to save {} in bucket";
    private static final String FAILED_TO_PARSE_EVENT_MESSAGE = "Failed to map body to DynamodbStreamRecord: {}";
    private static final String FAILED_TO_FETCH_CANDIDATE_MESSAGE = "Failed to fetch candidate with identifier: {}";
    private static final String FAILED_TO_GENERATE_INDEX_DOCUMENT_MESSAGE =
        "Failed to generate index document for candidate with identifier: {}";
    private final StorageReader<URI> storageReader;
    private final StorageWriter<IndexDocumentWithConsumptionAttributes> storageWriter;
    private final CandidateRepository candidateRepository;
    private final PeriodRepository periodRepository;
    private final UriRetriever uriRetriever;
    private final QueueClient sqsClient;
    private final String queueUrl;

    @JacocoGenerated
    public IndexDocumentHandler() {
        this(new S3StorageReader(new Environment().readEnv(EXPANDED_RESOURCES_BUCKET)),
             new S3StorageWriter(new Environment().readEnv(EXPANDED_RESOURCES_BUCKET)),
             new NviQueueClient(), new CandidateRepository(defaultDynamoClient()),
             new PeriodRepository(defaultDynamoClient()),
             new UriRetriever(),
             new Environment());
    }

    public IndexDocumentHandler(StorageReader<URI> storageReader,
                                StorageWriter<IndexDocumentWithConsumptionAttributes> storageWriter,
                                QueueClient sqsClient,
                                CandidateRepository candidateRepository,
                                PeriodRepository periodRepository,
                                UriRetriever uriRetriever,
                                Environment environment) {
        this.storageReader = storageReader;
        this.storageWriter = storageWriter;
        this.sqsClient = sqsClient;
        this.candidateRepository = candidateRepository;
        this.periodRepository = periodRepository;
        this.uriRetriever = uriRetriever;
        this.queueUrl = environment.readEnv(QUEUE_URL);
    }

    @Override
    public Void handleRequest(SQSEvent input, Context context) {
        LOGGER.info("Received event with {} records", input.getRecords().size());
        input.getRecords()
            .stream()
            .map(SQSMessage::getBody)
            .map(this::mapToDynamoDbRecord)
            .filter(Objects::nonNull)
            .map(this::generateIndexDocument)
            .filter(Objects::nonNull)
            .map(this::persistDocument)
            .filter(Objects::nonNull)
            .forEach(this::sendEvent);
        return null;
    }

    private static Optional<UUID> extractIdFromRecord(DynamodbStreamRecord record) {
        return attempt(() -> UUID.fromString(extractIdentifier(record))).toOptional();
    }

    private static String extractIdentifier(DynamodbStreamRecord record) {
        return Optional.ofNullable(record.getDynamodb().getOldImage())
                   .orElse(record.getDynamodb().getNewImage())
                   .get(IDENTIFIER).getS();
    }

    private void sendEvent(URI uri) {
        attempt(() -> sqsClient.sendMessage(new PersistedIndexDocumentMessage(uri).asJsonString(), queueUrl))
            .orElse(failure -> {
                handleFailure(failure, FAILED_SENDING_EVENT_MESSAGE, uri.toString());
                return null;
            });
    }

    private URI persistDocument(IndexDocumentWithConsumptionAttributes document) {
        return attempt(() -> document.persist(storageWriter))
                   .orElse(failure -> {
                       handleFailure(failure, FAILED_TO_PERSIST_MESSAGE,
                                     document.indexDocument().identifier().toString());
                       return null;
                   });
    }

    private DynamodbStreamRecord mapToDynamoDbRecord(String body) {
        return attempt(() -> dynamoObjectMapper.readValue(body, DynamodbStreamRecord.class))
                   .orElse(failure -> {
                       handleFailure(failure, FAILED_TO_PARSE_EVENT_MESSAGE, body);
                       return null;
                   });
    }

    private Candidate fetchCandidate(DynamodbStreamRecord record) {
        return extractIdFromRecord(record).map(this::fetchCandidate).orElse(null);
    }

    private Candidate fetchCandidate(UUID candidateIdentifier) {
        return attempt(() -> Candidate.fromRequest(() -> candidateIdentifier, candidateRepository, periodRepository))
                   .orElse(failure -> {
                       handleFailure(failure, FAILED_TO_FETCH_CANDIDATE_MESSAGE, candidateIdentifier.toString());
                       return null;
                   });
    }

    private PersistedResource fetchPersistedResource(Candidate candidate) {
        return PersistedResource.fromUri(candidate.getPublicationDetails().publicationBucketUri(), storageReader);
    }

    private IndexDocumentWithConsumptionAttributes generateIndexDocument(DynamodbStreamRecord record) {
        return attempt(() -> generateIndexDocumentWithConsumptionAttributes(record))
                   .orElse(failure -> {
                       handleFailure(failure, FAILED_TO_GENERATE_INDEX_DOCUMENT_MESSAGE,
                                     extractIdFromRecord(record).map(UUID::toString).orElse(null));
                       return null;
                   });
    }

    private IndexDocumentWithConsumptionAttributes generateIndexDocumentWithConsumptionAttributes(
        DynamodbStreamRecord record) {
        var candidate = fetchCandidate(record);
        var persistedResource = fetchPersistedResource(candidate);
        return IndexDocumentWithConsumptionAttributes.from(candidate, persistedResource, uriRetriever);
    }

    private void handleFailure(Failure<?> failure, String message, String messageArgument) {
        LOGGER.error(message, messageArgument);
        LOGGER.error(ERROR_MESSAGE, failure.getException().getMessage());
        //TODO: Send message to DLQ
    }
}
