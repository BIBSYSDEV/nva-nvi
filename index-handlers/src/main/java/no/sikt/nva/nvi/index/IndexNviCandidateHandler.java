package no.sikt.nva.nvi.index;

import static no.sikt.nva.nvi.common.model.business.ApprovalStatus.FINALIZED_BY_FIELD;
import static no.sikt.nva.nvi.common.model.business.ApprovalStatus.FINALIZED_DATE_FIELD;
import static no.sikt.nva.nvi.common.model.business.ApprovalStatus.INSTITUTION_ID_FIELD;
import static no.sikt.nva.nvi.common.model.business.ApprovalStatus.STATUS_FIELD;
import static no.sikt.nva.nvi.common.model.business.Candidate.APPROVAL_STATUSES_FIELD;
import static no.sikt.nva.nvi.common.model.business.Candidate.CREATORS_FIELD;
import static no.sikt.nva.nvi.common.model.business.Candidate.CREATOR_COUNT_FIELD;
import static no.sikt.nva.nvi.common.model.business.Candidate.INSTANCE_TYPE_FIELD;
import static no.sikt.nva.nvi.common.model.business.Candidate.IS_APPLICABLE_FIELD;
import static no.sikt.nva.nvi.common.model.business.Candidate.IS_INTERNATIONAL_COLLABORATION_FIELD;
import static no.sikt.nva.nvi.common.model.business.Candidate.LEVEL_FIELD;
import static no.sikt.nva.nvi.common.model.business.Candidate.NOTES_FIELD;
import static no.sikt.nva.nvi.common.model.business.Candidate.PUBLICATION_BUCKET_URI_FIELD;
import static no.sikt.nva.nvi.common.model.business.Candidate.PUBLICATION_DATE_FIELD;
import static no.sikt.nva.nvi.common.model.business.Candidate.PUBLICATION_ID_FIELD;
import static no.sikt.nva.nvi.index.aws.OpenSearchClient.defaultOpenSearchClient;
import static no.sikt.nva.nvi.index.utils.NviCandidateIndexDocumentGenerator.generateDocument;
import static nva.commons.core.attempt.Try.attempt;
import com.amazonaws.services.lambda.runtime.Context;
import com.amazonaws.services.lambda.runtime.RequestHandler;
import com.amazonaws.services.lambda.runtime.events.DynamodbEvent;
import com.amazonaws.services.lambda.runtime.events.DynamodbEvent.DynamodbStreamRecord;
import com.amazonaws.services.lambda.runtime.events.models.dynamodb.AttributeValue;
import com.amazonaws.services.lambda.runtime.events.models.dynamodb.OperationType;
import java.net.URI;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import no.sikt.nva.nvi.common.StorageReader;
import no.sikt.nva.nvi.common.model.business.ApprovalStatus;
import no.sikt.nva.nvi.common.model.business.Candidate;
import no.sikt.nva.nvi.common.model.business.Candidate.Builder;
import no.sikt.nva.nvi.common.model.business.Creator;
import no.sikt.nva.nvi.common.model.business.Level;
import no.sikt.nva.nvi.common.model.business.Note;
import no.sikt.nva.nvi.common.model.business.PublicationDate;
import no.sikt.nva.nvi.common.model.business.Status;
import no.sikt.nva.nvi.common.model.business.Username;
import no.sikt.nva.nvi.index.aws.S3StorageReader;
import no.sikt.nva.nvi.index.aws.SearchClient;
import no.sikt.nva.nvi.index.model.NviCandidateIndexDocument;
import no.unit.nva.commons.json.JsonUtils;
import nva.commons.core.Environment;
import nva.commons.core.JacocoGenerated;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class IndexNviCandidateHandler implements RequestHandler<DynamodbEvent, Void> {

    public static final String DOCUMENT_ADDED_MESSAGE = "Document has been added/updated";
    public static final String DOCUMENT_REMOVED_MESSAGE = "Document has been removed";
    private static final Logger LOGGER = LoggerFactory.getLogger(IndexNviCandidateHandler.class);
    private static final Environment ENVIRONMENT = new Environment();
    private static final String EXPANDED_RESOURCES_BUCKET = ENVIRONMENT.readEnv("EXPANDED_RESOURCES_BUCKET");
    private final SearchClient<NviCandidateIndexDocument> openSearchClient;
    private final StorageReader<URI> storageReader;

    @JacocoGenerated
    public IndexNviCandidateHandler() {
        this(new S3StorageReader(EXPANDED_RESOURCES_BUCKET), defaultOpenSearchClient());
    }

    public IndexNviCandidateHandler(StorageReader<URI> storageReader,
                                    SearchClient<NviCandidateIndexDocument> openSearchClient) {
        this.storageReader = storageReader;
        this.openSearchClient = openSearchClient;
    }

    public Void handleRequest(DynamodbEvent event, Context context) {
        try {
            event.getRecords().forEach(this::updateIndex);
        } catch (Exception e) {
            return null;
        }
        return null;
    }

    protected OperationType getEventType(DynamodbStreamRecord record) {
        return OperationType.fromValue(record.getEventName());
    }

    //TODO: Implement when we have DynamoDbRecord
    private static URI extractBucketUri(DynamodbStreamRecord record) {
        return URI.create("example.com" + record.getEventName());
    }

    private void updateIndex(DynamodbStreamRecord record) {
        var string = attempt(() -> JsonUtils.dynamoObjectMapper.writeValueAsString(record)).orElseThrow();
        LOGGER.info("String {}: ", string);

        var dynamodbStreamRecord =
            attempt(() -> JsonUtils.dtoObjectMapper.readValue(string, DynamodbStreamRecord.class)).orElseThrow();

//        var some = toCandidate(dynamodbStreamRecord.getDynamodb().getNewImage());

        if (isInsert(record) || isModify(record)) {
            addDocumentToIndex(record);
        }
        if (isRemove(record)) {
            removeDocumentFromIndex(record);
        }
    }

//    private Candidate toCandidate(
//        Map<String, com.amazonaws.services.lambda.runtime.events.models.dynamodb.AttributeValue> map) {
//        return new Builder()
//                   .withPublicationId(URI.create(map.get(PUBLICATION_ID_FIELD).getS()))
//                   .withPublicationBucketUri(Optional.ofNullable(map.get(PUBLICATION_BUCKET_URI_FIELD))
//                                                 .map(
//                                                     com.amazonaws.services.lambda.runtime.events.models.dynamodb.AttributeValue::getS).map(URI::create).orElse(null))
//                   .withIsApplicable(map.get(IS_APPLICABLE_FIELD).getBOOL())
//                   .withInstanceType(map.get(INSTANCE_TYPE_FIELD).getS())
//                   .withLevel(Level.parse(map.get(LEVEL_FIELD).getS()))
////                   .withPublicationDate(PublicationDate.fromDynamoDb(map.get(PUBLICATION_DATE_FIELD).getM().get()))
//                   .withIsInternationalCollaboration(map.get(IS_INTERNATIONAL_COLLABORATION_FIELD).getBOOL())
//                   .withCreatorCount(Integer.parseInt(map.get(CREATOR_COUNT_FIELD).getN()))
//                   .withCreators(
//                       map.get(CREATORS_FIELD).getL().stream().map(Creator::fromDynamoDb).toList()
//                   )
//                   .withApprovalStatuses(
//                       map.get(APPROVAL_STATUSES_FIELD).getL()
//                           .stream().map(a -> {
//                                new ApprovalStatus.Builder()
//                                          .withInstitutionId(URI.create(map.get(INSTITUTION_ID_FIELD).getS()))
//                                          .withStatus(Status.parse(map.get(STATUS_FIELD).getS()))
//                                          .withFinalizedBy(
//                                              Optional.ofNullable(map.get(FINALIZED_BY_FIELD)).map(Username::fromDynamoDb).orElse(null))
//                                          .withFinalizedDate(Optional.ofNullable(map.get(FINALIZED_DATE_FIELD))
//                                                                 .map(software.amazon.awssdk.services.dynamodb.model.AttributeValue::n)
//                                                                 .map(Long::parseLong)
//                                                                 .map(Instant::ofEpochMilli)
//                                                                 .orElse(null))
//                                          .build();
//                           }).toList()
//                   )
//                   .withNotes(Optional.ofNullable(map.get(NOTES_FIELD))
//                                  .map(AttributeValue::getL).map(l -> l.stream().map(Note::fromDynamoDb)
//                                                                       .toList()).orElse(null)
//                   )
//                   .build();
//    }

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
        openSearchClient.removeDocumentFromIndex(toCandidate(record));
        LOGGER.info(DOCUMENT_REMOVED_MESSAGE);
    }

    private void addDocumentToIndex(DynamodbStreamRecord record) {
        var approvalAffiliations = extractAffiliationApprovals();

        attempt(() -> extractBucketUri(record)).map(storageReader::read)
                                               .map(string -> generateDocument(string, approvalAffiliations))
                                               .forEach(openSearchClient::addDocumentToIndex);

        LOGGER.info(DOCUMENT_ADDED_MESSAGE);
    }

    //TODO: Implement when we have DynamoDbRecord
    private List<String> extractAffiliationApprovals() {
        return List.of("https://api.dev.nva.aws.unit.no/cristin/organization/20754.0.0.0");
    }

    //TODO::Have to map DynamoDbRecord to IndexDocument when we know how DynamoRecord looks like
    private NviCandidateIndexDocument toCandidate(DynamodbStreamRecord record) {
        return new NviCandidateIndexDocument.Builder().withYear(record.toString()).build();
    }
}
