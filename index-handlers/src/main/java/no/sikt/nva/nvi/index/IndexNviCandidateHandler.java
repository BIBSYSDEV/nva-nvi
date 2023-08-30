package no.sikt.nva.nvi.index;

import static no.sikt.nva.nvi.common.service.NviService.defaultNviService;
import static no.sikt.nva.nvi.index.aws.OpenSearchClient.defaultOpenSearchClient;
import static no.sikt.nva.nvi.index.utils.NviCandidateIndexDocumentGenerator.generateDocument;
import static nva.commons.core.attempt.Try.attempt;
import com.amazonaws.services.lambda.runtime.Context;
import com.amazonaws.services.lambda.runtime.RequestHandler;
import com.amazonaws.services.lambda.runtime.events.DynamodbEvent;
import com.amazonaws.services.lambda.runtime.events.DynamodbEvent.DynamodbStreamRecord;
import com.amazonaws.services.lambda.runtime.events.models.dynamodb.OperationType;
import java.net.URI;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import no.sikt.nva.nvi.common.StorageReader;
import no.sikt.nva.nvi.common.model.CandidateWithIdentifier;
import no.sikt.nva.nvi.common.model.business.ApprovalStatus;
import no.sikt.nva.nvi.common.model.business.Candidate;
import no.sikt.nva.nvi.common.service.NviService;
import no.sikt.nva.nvi.index.aws.S3StorageReader;
import no.sikt.nva.nvi.index.aws.SearchClient;
import no.sikt.nva.nvi.index.model.NviCandidateIndexDocument;
import nva.commons.core.Environment;
import nva.commons.core.JacocoGenerated;
import nva.commons.core.paths.UriWrapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class IndexNviCandidateHandler implements RequestHandler<DynamodbEvent, Void> {

    public static final String DOCUMENT_ADDED_MESSAGE = "Document has been added/updated";
    public static final String DOCUMENT_REMOVED_MESSAGE = "Document has been removed";
    public static final String CANDIDATE_TYPE = "CANDIDATE";
    public static final String PRIMARY_KEY_DELIMITER = "#";
    public static final String PRIMARY_KEY_RANGE_KEY = "PrimaryKeyRangeKey";
    public static final String IDENTIFIER = "identifier";
    private static final Logger LOGGER = LoggerFactory.getLogger(IndexNviCandidateHandler.class);
    private static final Environment ENVIRONMENT = new Environment();
    private static final String EXPANDED_RESOURCES_BUCKET = ENVIRONMENT.readEnv("EXPANDED_RESOURCES_BUCKET");
    private final SearchClient<NviCandidateIndexDocument> openSearchClient;
    private final StorageReader<URI> storageReader;
    private final NviService nviService;

    @JacocoGenerated
    public IndexNviCandidateHandler() {
        this(new S3StorageReader(EXPANDED_RESOURCES_BUCKET), defaultOpenSearchClient(), defaultNviService());
    }

    public IndexNviCandidateHandler(StorageReader<URI> storageReader,
                                    SearchClient<NviCandidateIndexDocument> openSearchClient, NviService nviService) {
        this.storageReader = storageReader;
        this.openSearchClient = openSearchClient;
        this.nviService = nviService;
    }

    public Void handleRequest(DynamodbEvent event, Context context) {
        LOGGER.info("Incoming event {}: ", event.getRecords().toString());
        event.getRecords().stream()
            .filter(this::isCandidate)
            .forEach(this::updateIndex);

        return null;
    }

    protected OperationType getEventType(DynamodbStreamRecord record) {
        return OperationType.fromValue(record.getEventName());
    }

    private static String extractRecordType(DynamodbStreamRecord record) {
        return record.getDynamodb().getKeys().get(PRIMARY_KEY_RANGE_KEY).getS().split(PRIMARY_KEY_DELIMITER)[0];
    }

    private static URI extractBucketUri(Candidate candidate) {
        return candidate.publicationBucketUri();
    }

    private static NviCandidateIndexDocument toIndexDocumentWithId(URI uri) {
        return new NviCandidateIndexDocument.Builder()
                   .withIdentifier(UriWrapper.fromUri(uri).getLastPathElement())
                   .build();
    }

    private static UUID extractIdentifierFromOldImage(DynamodbStreamRecord record) {
        return UUID.fromString(record.getDynamodb().getNewImage().get(IDENTIFIER).getS());
    }

    private boolean isCandidate(DynamodbStreamRecord record) {
        return CANDIDATE_TYPE.equals(extractRecordType(record));
    }

    private void updateIndex(DynamodbStreamRecord record) {
        LOGGER.info("Updating index {}: ", record.toString());
        if (isInsert(record) || isModify(record)) {
            addDocumentToIndex(record);
        }
        if (isRemove(record)) {
            removeDocumentFromIndex(record);
        }
    }

    private boolean isRemove(DynamodbStreamRecord record) {
        return getEventType(record).equals(OperationType.REMOVE);
    }

    private boolean isModify(DynamodbStreamRecord record) {
        return getEventType(record).equals(OperationType.MODIFY);
    }

    private boolean isInsert(DynamodbStreamRecord record) {
        return getEventType(record).equals(OperationType.INSERT);
    }

    private void removeDocumentFromIndex(DynamodbStreamRecord record) {

        attempt(() -> extractIdentifierFromOldImage(record))
            .map(nviService::findById)
            .map(Optional::get)
            .map(CandidateWithIdentifier::candidate)
            .map(Candidate::publicationId)
            .map(IndexNviCandidateHandler::toIndexDocumentWithId)
            .forEach(openSearchClient::removeDocumentFromIndex);

        LOGGER.info(DOCUMENT_REMOVED_MESSAGE);
    }

    private void addDocumentToIndex(DynamodbStreamRecord record) {
        LOGGER.info("Adding to index {}: ", record.toString());
        LOGGER.info("Record id {}: ", extractIdentifierFromOldImage(record).toString());
        var candidate = nviService.findById(extractIdentifierFromOldImage(record));
        LOGGER.info("Fetched candidate: {}", candidate);
        attempt(candidate::get)
            .map(CandidateWithIdentifier::candidate)
            .map(IndexNviCandidateHandler::extractBucketUri)
            .map(storageReader::read)
            .map(blob -> generateDocument(blob, extractApprovalStatuses(candidate.orElseThrow())))
            .forEach(openSearchClient::addDocumentToIndex);

        LOGGER.info(DOCUMENT_ADDED_MESSAGE);
    }

    private static List<ApprovalStatus> extractApprovalStatuses(CandidateWithIdentifier candidate) {
        return candidate.candidate().approvalStatuses();
    }
}
