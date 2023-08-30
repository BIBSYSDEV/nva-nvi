package no.sikt.nva.nvi.index;

import static no.sikt.nva.nvi.common.ApplicationConstants.SORT_KEY;
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
import java.util.Optional;
import java.util.UUID;
import no.sikt.nva.nvi.common.StorageReader;
import no.sikt.nva.nvi.common.model.CandidateWithIdentifier;
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

public class UpdateIndexHandler implements RequestHandler<DynamodbEvent, Void> {

    public static final String DOCUMENT_ADDED_MESSAGE = "Document has been added/updated";
    public static final String DOCUMENT_REMOVED_MESSAGE = "Document has been removed";
    private static final String CANDIDATE_TYPE = "CANDIDATE";
    private static final String PRIMARY_KEY_DELIMITER = "#";
    private static final String IDENTIFIER = "identifier";
    private static final Logger LOGGER = LoggerFactory.getLogger(UpdateIndexHandler.class);
    private static final Environment ENVIRONMENT = new Environment();
    private static final String EXPANDED_RESOURCES_BUCKET = ENVIRONMENT.readEnv("EXPANDED_RESOURCES_BUCKET");
    public static final String DATA_PROPERTY = "data";
    public static final String IS_APPLICABLE_PROPERTY = "isApplicable";
    private final SearchClient<NviCandidateIndexDocument> openSearchClient;
    private final StorageReader<URI> storageReader;
    private final NviService nviService;

    @JacocoGenerated
    public UpdateIndexHandler() {
        this(new S3StorageReader(EXPANDED_RESOURCES_BUCKET), defaultOpenSearchClient(), defaultNviService());
    }

    public UpdateIndexHandler(StorageReader<URI> storageReader,
                              SearchClient<NviCandidateIndexDocument> openSearchClient,
                              NviService nviService) {
        this.storageReader = storageReader;
        this.openSearchClient = openSearchClient;
        this.nviService = nviService;
    }

    public Void handleRequest(DynamodbEvent event, Context context) {
        event.getRecords().stream()
            .filter(this::isCandidate)
            .filter(this::isUpdate)
            .forEach(this::updateIndex);

        return null;
    }

    protected OperationType getEventType(DynamodbStreamRecord record) {
        return OperationType.fromValue(record.getEventName());
    }

    private static String extractRecordType(DynamodbStreamRecord record) {
        return record.getDynamodb().getKeys().get(SORT_KEY).getS().split(PRIMARY_KEY_DELIMITER)[0];
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
        if (isApplicable(record)) {
            addDocumentToIndex(record);
        } else {
            removeDocumentFromIndex(record);
        }
    }

    private static Boolean isApplicable(DynamodbStreamRecord record) {
        return record.getDynamodb().getNewImage().get(DATA_PROPERTY).getM().get(IS_APPLICABLE_PROPERTY).getBOOL();
    }

    private boolean isUpdate(DynamodbStreamRecord record) {
        var eventType = getEventType(record);
        return OperationType.INSERT.equals(eventType) || OperationType.MODIFY.equals(eventType);
    }

    private void removeDocumentFromIndex(DynamodbStreamRecord record) {

        attempt(() -> extractIdentifierFromOldImage(record))
            .map(nviService::findById)
            .map(Optional::get)
            .map(CandidateWithIdentifier::candidate)
            .map(Candidate::publicationId)
            .map(UpdateIndexHandler::toIndexDocumentWithId)
            .forEach(openSearchClient::removeDocumentFromIndex);

        LOGGER.info(DOCUMENT_REMOVED_MESSAGE);
    }

    private void addDocumentToIndex(DynamodbStreamRecord record) {
        var candidate = nviService.findById(extractIdentifierFromOldImage(record));

        attempt(candidate::get)
            .map(CandidateWithIdentifier::candidate)
            .map(UpdateIndexHandler::extractBucketUri)
            .map(storageReader::read)
            .map(blob -> generateDocument(blob, candidate.orElseThrow()))
            .forEach(openSearchClient::addDocumentToIndex);

        LOGGER.info(DOCUMENT_ADDED_MESSAGE);
    }
}
