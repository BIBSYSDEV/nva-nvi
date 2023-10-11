package no.sikt.nva.nvi.index;

import static no.sikt.nva.nvi.common.DatabaseConstants.SORT_KEY;
import static no.sikt.nva.nvi.common.db.DynamoRepository.defaultDynamoClient;
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
import no.sikt.nva.nvi.common.db.CandidateRepository;
import no.sikt.nva.nvi.common.db.PeriodRepository;
import no.sikt.nva.nvi.common.service.CandidateBO;
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

    public static final String COULD_NOT_UPDATE_INDEX_MESSAGE = "Could not update index for record: {}";
    private static final String CANDIDATE_TYPE = "CANDIDATE";
    private static final String APPROVAL_TYPE = "APPROVAL_STATUS";
    private static final String PRIMARY_KEY_DELIMITER = "#";
    private static final String IDENTIFIER = "identifier";
    private static final Logger LOGGER = LoggerFactory.getLogger(UpdateIndexHandler.class);
    private static final Environment ENVIRONMENT = new Environment();
    private static final String EXPANDED_RESOURCES_BUCKET = ENVIRONMENT.readEnv("EXPANDED_RESOURCES_BUCKET");
    private final SearchClient<NviCandidateIndexDocument> openSearchClient;
    private final StorageReader<URI> storageReader;
    private final CandidateRepository candidateRepository;
    private final PeriodRepository periodRepository;
    private final NviCandidateIndexDocumentGenerator documentGenerator;

    @JacocoGenerated
    public UpdateIndexHandler() {
        this(new S3StorageReader(EXPANDED_RESOURCES_BUCKET), defaultOpenSearchClient(),
             new CandidateRepository(defaultDynamoClient()), new PeriodRepository(defaultDynamoClient()),
             new NviCandidateIndexDocumentGenerator(defaultUriRetriver()));
    }

    public UpdateIndexHandler(StorageReader<URI> storageReader,
                              SearchClient<NviCandidateIndexDocument> openSearchClient,
                              CandidateRepository candidateRepository, PeriodRepository periodRepository,
                              NviCandidateIndexDocumentGenerator documentGenerator) {
        this.storageReader = storageReader;
        this.openSearchClient = openSearchClient;
        this.candidateRepository = candidateRepository;
        this.periodRepository = periodRepository;
        this.documentGenerator = documentGenerator;
    }

    public Void handleRequest(DynamodbEvent event, Context context) {
        event.getRecords()
            .stream()
            .filter(this::isUpdate)
            .filter(this::isCandidateOrApproval)
            .forEach(this::updateIndex);

        return null;
    }

    protected OperationType getEventType(DynamodbStreamRecord record) {
        return OperationType.fromValue(record.getEventName());
    }

    private static String extractRecordType(DynamodbStreamRecord record) {
        return record.getDynamodb().getKeys().get(SORT_KEY).getS().split(PRIMARY_KEY_DELIMITER)[0];
    }

    private static NviCandidateIndexDocument toIndexDocumentWithId(UUID candidateIdentifier) {
        return new NviCandidateIndexDocument.Builder().withIdentifier(candidateIdentifier.toString()).build();
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

    @JacocoGenerated
    private static AuthorizedBackendUriRetriever defaultUriRetriver() {
        return new AuthorizedBackendUriRetriever(new Environment().readEnv("BACKEND_CLIENT_AUTH_URL"),
                                                 new Environment().readEnv("BACKEND_CLIENT_SECRET_NAME"));
    }

    private static void logRecordThatCouldNotBeIndexed(DynamodbStreamRecord record) {
        var eventString = attempt(() -> JsonUtils.dtoObjectMapper.writeValueAsString(record)).orElseThrow();
        LOGGER.error(COULD_NOT_UPDATE_INDEX_MESSAGE, eventString);
    }

    private boolean isCandidateOrApproval(DynamodbStreamRecord record) {
        return isCandidate(record) || isApproval(record);
    }

    private void updateIndex(DynamodbStreamRecord record) {
        try {
            var candidateIdentifier = extractIdentifierFromNewImage(record);
            var candidate = fetchCandidate(candidateIdentifier);
            if (candidate.isApplicable()) {
                addDocumentToIndex(candidate);
            } else {
                removeDocumentFromIndex(candidate);
            }
        } catch (Exception e) {
            logRecordThatCouldNotBeIndexed(record);
        }
    }

    private CandidateBO fetchCandidate(UUID candidateIdentifier) {
        return CandidateBO.fromRequest(() -> candidateIdentifier, candidateRepository, periodRepository);
    }

    private boolean isUpdate(DynamodbStreamRecord record) {
        var eventType = getEventType(record);
        return OperationType.INSERT.equals(eventType) || OperationType.MODIFY.equals(eventType);
    }

    private void removeDocumentFromIndex(CandidateBO candidate) {
        openSearchClient.removeDocumentFromIndex(toIndexDocumentWithId(candidate.identifier()));
    }

    private void addDocumentToIndex(CandidateBO candidate) {
        attempt(() -> storageReader.read(candidate.getBucketUri()))
            .map(blob -> documentGenerator.generateDocument(blob, candidate))
            .forEach(openSearchClient::addDocumentToIndex)
            .orElseThrow();
    }
}
