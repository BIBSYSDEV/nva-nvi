package no.sikt.nva.nvi.index;

import static no.sikt.nva.nvi.common.DatabaseConstants.SORT_KEY;
import static no.sikt.nva.nvi.common.db.DynamoRepository.defaultDynamoClient;
import static no.sikt.nva.nvi.index.aws.OpenSearchClient.defaultOpenSearchClient;
import static no.unit.nva.commons.json.JsonUtils.dtoObjectMapper;
import static nva.commons.core.attempt.Try.attempt;
import com.amazonaws.services.lambda.runtime.Context;
import com.amazonaws.services.lambda.runtime.RequestHandler;
import com.amazonaws.services.lambda.runtime.events.DynamodbEvent;
import com.amazonaws.services.lambda.runtime.events.DynamodbEvent.DynamodbStreamRecord;
import com.amazonaws.services.lambda.runtime.events.models.dynamodb.AttributeValue;
import com.amazonaws.services.lambda.runtime.events.models.dynamodb.OperationType;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.net.URI;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import no.sikt.nva.nvi.common.S3StorageReader;
import no.sikt.nva.nvi.common.StorageReader;
import no.sikt.nva.nvi.common.db.CandidateRepository;
import no.sikt.nva.nvi.common.db.PeriodRepository;
import no.sikt.nva.nvi.common.queue.NviQueueClient;
import no.sikt.nva.nvi.common.queue.QueueClient;
import no.sikt.nva.nvi.common.service.model.Candidate;
import no.sikt.nva.nvi.index.aws.SearchClient;
import no.sikt.nva.nvi.index.model.NviCandidateIndexDocument;
import no.sikt.nva.nvi.index.utils.NviCandidateIndexDocumentGenerator;
import no.unit.nva.auth.uriretriever.UriRetriever;
import nva.commons.core.Environment;
import nva.commons.core.JacocoGenerated;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@JacocoGenerated
public class UpdateIndexHandler implements RequestHandler<DynamodbEvent, Void> {

    private static final Logger LOGGER = LoggerFactory.getLogger(UpdateIndexHandler.class);
    private static final String COULD_NOT_UPDATE_INDEX_MESSAGE = "Could not update index for record: {}";
    private static final String CANDIDATE_TYPE = "CANDIDATE";
    private static final String APPROVAL_TYPE = "APPROVAL_STATUS";
    private static final String PRIMARY_KEY_DELIMITER = "#";
    private static final String IDENTIFIER = "identifier";
    private static final String UPDATE_INDEX_DLQ_QUEUE_URL = "UPDATE_INDEX_DLQ_QUEUE_URL";
    private final SearchClient<NviCandidateIndexDocument> openSearchClient;
    private final StorageReader<URI> storageReader;
    private final CandidateRepository candidateRepository;
    private final PeriodRepository periodRepository;
    private final NviCandidateIndexDocumentGenerator documentGenerator;
    private final QueueClient queueClient;
    private final String dlqUrl;

    @JacocoGenerated
    public UpdateIndexHandler() {
        this(new S3StorageReader(new Environment().readEnv("EXPANDED_RESOURCES_BUCKET")), defaultOpenSearchClient(),
             new CandidateRepository(defaultDynamoClient()), new PeriodRepository(defaultDynamoClient()),
             new NviCandidateIndexDocumentGenerator(new UriRetriever()), new NviQueueClient(),
             new Environment());
    }

    public UpdateIndexHandler(StorageReader<URI> storageReader,
                              SearchClient<NviCandidateIndexDocument> openSearchClient,
                              CandidateRepository candidateRepository, PeriodRepository periodRepository,
                              NviCandidateIndexDocumentGenerator documentGenerator,
                              QueueClient queueClient,
                              Environment environment) {
        this.storageReader = storageReader;
        this.openSearchClient = openSearchClient;
        this.candidateRepository = candidateRepository;
        this.periodRepository = periodRepository;
        this.documentGenerator = documentGenerator;
        this.queueClient = queueClient;
        this.dlqUrl = environment.readEnv(UPDATE_INDEX_DLQ_QUEUE_URL);
    }

    public Void handleRequest(DynamodbEvent event, Context context) {
        LOGGER.info("Starting handleRequest. Records count: {}", event.getRecords().size());
        event.getRecords()
            .stream()
            .filter(this::isUpdate)
            .filter(this::isCandidateOrApproval)
            .forEach(this::updateIndex);
        LOGGER.info("Done executing handleRequest");
        return null;
    }

    protected OperationType getEventType(DynamodbStreamRecord record) {
        return OperationType.fromValue(record.getEventName());
    }

    private static String extractRecordType(DynamodbStreamRecord record) {
        return record.getDynamodb().getKeys().get(SORT_KEY).getS().split(PRIMARY_KEY_DELIMITER)[0];
    }

    private static UUID extractIdentifierFromNewImage(DynamodbStreamRecord record) {
        return UUID.fromString(record.getDynamodb().getNewImage().get(IDENTIFIER).getS());
    }

    private static boolean isApproval(DynamodbStreamRecord record) {
        return APPROVAL_TYPE.equals(extractRecordType(record));
    }

    private static boolean isCandidate(DynamodbStreamRecord record) {
        return CANDIDATE_TYPE.equals(extractRecordType(record));
    }

    private static String getRecordThatCouldNotBeIndexed(DynamodbStreamRecord record) {
        return attempt(() -> dtoObjectMapper.writeValueAsString(record)).orElseThrow();
    }

    private static String getStackTrace(Exception e) {
        var stringWriter = new StringWriter();
        e.printStackTrace(new PrintWriter(stringWriter));
        return stringWriter.toString();
    }

    private static String extractIdFromRecord(DynamodbStreamRecord record) {
        return Optional.ofNullable(record.getDynamodb().getOldImage())
                   .orElse(record.getDynamodb().getNewImage())
                   .getOrDefault(IDENTIFIER, new AttributeValue().withS("no id found")).getS();
    }

    private boolean isCandidateOrApproval(DynamodbStreamRecord record) {
        LOGGER.info("Record id {} type {}", extractIdFromRecord(record), extractRecordType(record));
        return isCandidate(record) || isApproval(record);
    }

    private void updateIndex(DynamodbStreamRecord record) {
        try {
            var candidateIdentifier = extractIdentifierFromNewImage(record);
            LOGGER.info("Doing updateIndex on {}", candidateIdentifier);
            var candidate = fetchCandidate(candidateIdentifier);
            if (candidate.isApplicable()) {
                addDocumentToIndex(candidate);
            } else {
                removeDocumentFromIndex(candidate);
            }
        } catch (Exception e) {
            LOGGER.error("updateIndex failed", e);
            LOGGER.error(COULD_NOT_UPDATE_INDEX_MESSAGE, getRecordThatCouldNotBeIndexed(record));
            attempt(() -> queueClient.sendMessage(dtoObjectMapper.writeValueAsString(Map.of(
                "exception", getStackTrace(e),
                "dynamodb", record
            )), dlqUrl));
        }
    }

    private Candidate fetchCandidate(UUID candidateIdentifier) {
        return Candidate.fromRequest(() -> candidateIdentifier, candidateRepository, periodRepository);
    }

    private boolean isUpdate(DynamodbStreamRecord record) {
        var eventType = getEventType(record);
        var identifier = extractIdFromRecord(record);

        LOGGER.info("Record id: {} Event type: {}", identifier, eventType.toString());
        return OperationType.INSERT.equals(eventType) || OperationType.MODIFY.equals(eventType);
    }

    private void removeDocumentFromIndex(Candidate candidate) {
        LOGGER.info("removeDocumentFromIndex for {}", candidate.getIdentifier());
        var result = openSearchClient.removeDocumentFromIndex(candidate.getIdentifier());
        LOGGER.info("done removeDocumentFromIndex for {}, result: {}", candidate.getIdentifier(),
                    result.result().jsonValue());
    }

    private void addDocumentToIndex(Candidate candidate) {
        LOGGER.info("addDocumentToIndex for {}", candidate.getIdentifier());
        var result = attempt(() -> getPublicationFromBucket(candidate))
                         .map(blob -> documentGenerator.generateDocument(blob, candidate))
                         .map(indexDocument -> {
                             LOGGER.info("Adding document to index, candidateId: {} publicationId: {}",
                                         indexDocument.identifier(),
                                         indexDocument.publicationDetails().id());
                             return indexDocument;
                         })
                         .map(openSearchClient::addDocumentToIndex)
                         .orElseThrow();
        LOGGER.info("done addDocumentToIndex for {}, result:{}", candidate.getIdentifier(),
                    result.result().jsonValue());
    }

    private String getPublicationFromBucket(Candidate candidate) {
        var bucketUri = candidate.getPublicationDetails().publicationBucketUri();
        LOGGER.info("getPublicationFromBucket with candidate id {} and url {}", candidate.getIdentifier(),
                    bucketUri.toString());
        var result = storageReader.read(bucketUri);
        LOGGER.info("Done fetching {}, string length {}", bucketUri, result.length());
        return result;
    }
}
