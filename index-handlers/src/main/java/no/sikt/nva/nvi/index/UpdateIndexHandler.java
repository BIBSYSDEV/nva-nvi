package no.sikt.nva.nvi.index;

import static no.sikt.nva.nvi.common.DatabaseConstants.SORT_KEY;
import static no.sikt.nva.nvi.common.service.NviService.defaultNviService;
import static no.sikt.nva.nvi.index.aws.OpenSearchClient.defaultOpenSearchClient;
import static nva.commons.core.attempt.Try.attempt;
import com.amazonaws.services.lambda.runtime.Context;
import com.amazonaws.services.lambda.runtime.RequestHandler;
import com.amazonaws.services.lambda.runtime.events.DynamodbEvent;
import com.amazonaws.services.lambda.runtime.events.DynamodbEvent.DynamodbStreamRecord;
import com.amazonaws.services.lambda.runtime.events.models.dynamodb.OperationType;
import java.net.URI;
import java.util.UUID;
import no.sikt.nva.nvi.common.StorageReader;
import no.sikt.nva.nvi.common.db.CandidateDao.DbCandidate;
import no.sikt.nva.nvi.common.service.Candidate;
import no.sikt.nva.nvi.common.service.NviService;
import no.sikt.nva.nvi.index.aws.S3StorageReader;
import no.sikt.nva.nvi.index.aws.SearchClient;
import no.sikt.nva.nvi.index.model.NviCandidateIndexDocument;
import no.sikt.nva.nvi.index.utils.NviCandidateIndexDocumentGenerator;
import no.unit.nva.auth.uriretriever.AuthorizedBackendUriRetriever;
import no.unit.nva.commons.json.JsonUtils;
import nva.commons.core.Environment;
import nva.commons.core.JacocoGenerated;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@JacocoGenerated
public class UpdateIndexHandler implements RequestHandler<DynamodbEvent, Void> {

    private static final String CANDIDATE_TYPE = "CANDIDATE";
    private static final String APPROVAL_TYPE = "APPROVAL_STATUS";
    private static final String PRIMARY_KEY_DELIMITER = "#";
    private static final String IDENTIFIER = "identifier";
    private static final Logger LOGGER = LoggerFactory.getLogger(UpdateIndexHandler.class);
    private static final Environment ENVIRONMENT = new Environment();
    private static final String EXPANDED_RESOURCES_BUCKET = ENVIRONMENT.readEnv("EXPANDED_RESOURCES_BUCKET");
    public static final String COULD_NOT_UPDATE_INDEX_MESSAGE = "Could not update index for event: {}";
    private final SearchClient<NviCandidateIndexDocument> openSearchClient;
    private final StorageReader<URI> storageReader;
    private final NviService nviService;
    private final NviCandidateIndexDocumentGenerator documentGenerator;

    public UpdateIndexHandler() {
        this(new S3StorageReader(EXPANDED_RESOURCES_BUCKET), defaultOpenSearchClient(), defaultNviService(),
             new NviCandidateIndexDocumentGenerator(defaultUriRetriver()));
    }

    public UpdateIndexHandler(StorageReader<URI> storageReader,
                              SearchClient<NviCandidateIndexDocument> openSearchClient,
                              NviService nviService,
                              NviCandidateIndexDocumentGenerator documentGenerator
    ) {
        this.storageReader = storageReader;
        this.openSearchClient = openSearchClient;
        this.nviService = nviService;
        this.documentGenerator = documentGenerator;
    }

    public Void handleRequest(DynamodbEvent event, Context context) {
        try {
            event.getRecords().stream()
                .filter(this::isUpdate)
                .filter(this::isCandidateOrApproval)
                .forEach(this::updateIndex);
        } catch (Exception e) {
            var eventString = attempt(() -> JsonUtils.dtoObjectMapper.writeValueAsString(event)).orElseThrow();
            LOGGER.error(COULD_NOT_UPDATE_INDEX_MESSAGE, eventString);
        }

        return null;
    }

    protected OperationType getEventType(DynamodbStreamRecord record) {
        return OperationType.fromValue(record.getEventName());
    }

    private static String extractRecordType(DynamodbStreamRecord record) {
        return record.getDynamodb().getKeys().get(SORT_KEY).getS().split(PRIMARY_KEY_DELIMITER)[0];
    }

    private static URI extractBucketUri(DbCandidate candidate) {
        return candidate.publicationBucketUri();
    }

    private static NviCandidateIndexDocument toIndexDocumentWithId(UUID candidateIdentifier) {
        return new NviCandidateIndexDocument.Builder()
                   .withIdentifier(candidateIdentifier.toString())
                   .build();
    }

    private static UUID extractIdentifierFromNewImage(DynamodbStreamRecord record) {
        return UUID.fromString(record.getDynamodb().getNewImage().get(IDENTIFIER).getS());
    }

    private static Boolean isApplicable(Candidate candidate) {
        return candidate.candidate().applicable();
    }

    private static boolean isApproval(DynamodbStreamRecord record) {
        return APPROVAL_TYPE.equals(extractRecordType(record));
    }

    private static boolean isCandidate(DynamodbStreamRecord record) {
        return CANDIDATE_TYPE.equals(extractRecordType(record));
    }

    private boolean isCandidateOrApproval(DynamodbStreamRecord record) {
        return isCandidate(record) || isApproval(record);
    }

    private void updateIndex(DynamodbStreamRecord record) {
        var candidate = nviService.findCandidateById(extractIdentifierFromNewImage(record)).orElseThrow();
        if (isApplicable(candidate)) {
            addDocumentToIndex(candidate);
        } else {
            removeDocumentFromIndex(candidate);
        }
    }

    private boolean isUpdate(DynamodbStreamRecord record) {
        var eventType = getEventType(record);
        return OperationType.INSERT.equals(eventType) || OperationType.MODIFY.equals(eventType);
    }

    private void removeDocumentFromIndex(Candidate candidate) {
        LOGGER.info("Attempting to remove document with identifier {}", candidate.identifier().toString());
        attempt(candidate::identifier)
            .map(UpdateIndexHandler::toIndexDocumentWithId)
            .forEach(openSearchClient::removeDocumentFromIndex)
            .orElseThrow();
    }

    private static AuthorizedBackendUriRetriever defaultUriRetriver() {
        return new AuthorizedBackendUriRetriever(
            new Environment().readEnv("BACKEND_CLIENT_AUTH_URL"),
            new Environment().readEnv("BACKEND_CLIENT_SECRET_NAME")
        );
    }

    private void addDocumentToIndex(Candidate candidate) {
        LOGGER.info("Attempting to add/update document with identifier {}", candidate.identifier().toString());
        attempt(candidate::candidate)
            .map(UpdateIndexHandler::extractBucketUri)
            .map(storageReader::read)
            .map(blob -> documentGenerator.generateDocument(blob, candidate))
            .forEach(openSearchClient::addDocumentToIndex)
            .orElseThrow();
    }
}
