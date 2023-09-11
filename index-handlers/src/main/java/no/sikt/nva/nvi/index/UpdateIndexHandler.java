package no.sikt.nva.nvi.index;

import static no.sikt.nva.nvi.common.DatabaseConstants.SORT_KEY;
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
import java.util.UUID;
import no.sikt.nva.nvi.common.StorageReader;
import no.sikt.nva.nvi.common.db.Candidate;
import no.sikt.nva.nvi.common.db.model.DbCandidate;
import no.sikt.nva.nvi.common.service.NviService;
import no.sikt.nva.nvi.index.aws.S3StorageReader;
import no.sikt.nva.nvi.index.aws.SearchClient;
import no.sikt.nva.nvi.index.model.Approval;
import no.sikt.nva.nvi.index.model.ApprovalStatus;
import no.sikt.nva.nvi.index.model.NviCandidateIndexDocument;
import no.unit.nva.commons.json.JsonUtils;
import nva.commons.core.Environment;
import nva.commons.core.JacocoGenerated;
import nva.commons.core.paths.UriWrapper;
import org.opensearch.client.opensearch.core.SearchResponse;
import org.opensearch.client.opensearch.core.search.Hit;
import org.opensearch.client.opensearch.core.search.HitsMetadata;
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
    public static final String APPROVAL_UPDATED_MESSAGE = "Approval has been updated";
    private final SearchClient<NviCandidateIndexDocument> openSearchClient;
    private final StorageReader<URI> storageReader;
    private final NviService nviService;

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
        LOGGER.info("Event: {}", attempt(() -> JsonUtils.dtoObjectMapper.writeValueAsString(event)).orElseThrow());
        LOGGER.info("Record: {}",
                    attempt(() -> JsonUtils.dtoObjectMapper
                                      .writeValueAsString(event.getRecords().get(0))).orElseThrow());
        event.getRecords().stream()
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

    private static URI extractBucketUri(DbCandidate candidate) {
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

    private static Boolean isApplicable(Candidate candidate) {
        return candidate.candidate().applicable();
    }

    private boolean isCandidateOrApproval(DynamodbStreamRecord record) {
        return isCandidate(record) || isApproval(record);
    }

    private static boolean isApproval(DynamodbStreamRecord record) {
        return APPROVAL_TYPE.equals(extractRecordType(record));
    }

    private static boolean isCandidate(DynamodbStreamRecord record) {
        return CANDIDATE_TYPE.equals(extractRecordType(record));
    }

    private void updateIndex(DynamodbStreamRecord record) {
        var candidate = nviService.findById(extractIdentifierFromOldImage(record)).orElseThrow();
        if (isApproval(record)) {
            updateDocumentApprovals(record, candidate.identifier());
        } else {
            updateDocument(candidate);
        }
    }

    private void updateDocument(Candidate candidate) {
        if (isApplicable(candidate)) {
            addDocumentToIndex(candidate);
        } else {
            removeDocumentFromIndex(candidate);
        }
    }

    private void updateDocumentApprovals(DynamodbStreamRecord record, UUID identifier) {
        var searchResponse =
            attempt(() -> openSearchClient.searchDocumentById(identifier.toString())).orElseThrow();
        if (containsSingleHit(searchResponse)) {
            attempt(searchResponse::hits)
                .map(HitsMetadata::hits)
                .map(UpdateIndexHandler::getSingleHit)
                .map(Hit::source)
                .map(document -> updateApprovals(document, record))
                .forEach(openSearchClient::addDocumentToIndex)
                .orElseThrow();
            LOGGER.info(APPROVAL_UPDATED_MESSAGE);
        }
    }

    private static Hit<NviCandidateIndexDocument> getSingleHit(List<Hit<NviCandidateIndexDocument>> hits) {
        return hits.get(0);
    }

    private NviCandidateIndexDocument updateApprovals(NviCandidateIndexDocument document, DynamodbStreamRecord record) {
        return document.copy()
                   .withApprovals(document.approvals().stream()
                                      .map(approval -> updateApprovals(approval, record))
                                      .toList())
                   .build();
    }

    private Approval updateApprovals(Approval approval, DynamodbStreamRecord record) {
        if (isSameApproval(approval, record)) {
            var approvalFromRecord = extractApprovalFromRecord(record);
            return approval.copy()
                       .withApprovalStatus(approvalFromRecord.approvalStatus())
                       .withAssignee(approvalFromRecord.assignee())
                       .build();
        } else {
            return approval;
        }
    }

    private static Approval extractApprovalFromRecord(DynamodbStreamRecord record) {
        var approvalMap = record.getDynamodb().getNewImage().get("data").getM();
        return new Approval.Builder()
                   .withAssignee(approvalMap.get("assignee").getM().get("value").getS())
                   .withApprovalStatus(ApprovalStatus.fromValue(approvalMap.get("status").getS()))
                   .build();
    }

    private static boolean isSameApproval(Approval approval, DynamodbStreamRecord record) {
        return approval.id().equals(extractApprovalId(record));
    }

    private static String extractApprovalId(DynamodbStreamRecord record) {
        return record.getDynamodb().getNewImage().get("data").getM().get("institutionId").getS();
    }

    private static boolean containsSingleHit(SearchResponse<NviCandidateIndexDocument> searchResponse) {
        return searchResponse.hits().hits().size() == 1;
    }

    private boolean isUpdate(DynamodbStreamRecord record) {
        var eventType = getEventType(record);
        return OperationType.INSERT.equals(eventType) || OperationType.MODIFY.equals(eventType);
    }

    private void removeDocumentFromIndex(Candidate candidate) {
        LOGGER.info("Attempting to remove document with identifier {}", candidate.identifier().toString());
        attempt(candidate::candidate)
            .map(DbCandidate::publicationId)
            .map(UpdateIndexHandler::toIndexDocumentWithId)
            .forEach(openSearchClient::removeDocumentFromIndex)
            .orElseThrow();
    }

    private void addDocumentToIndex(Candidate candidate) {
        LOGGER.info("Attempting to add/update document with identifier {}", candidate.identifier().toString());
        attempt(candidate::candidate)
            .map(UpdateIndexHandler::extractBucketUri)
            .map(storageReader::read)
            .map(blob -> generateDocument(blob, candidate))
            .forEach(openSearchClient::addDocumentToIndex)
            .orElseThrow();
    }
}
