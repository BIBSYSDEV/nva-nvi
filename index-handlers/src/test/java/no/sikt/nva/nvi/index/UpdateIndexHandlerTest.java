package no.sikt.nva.nvi.index;

import static com.amazonaws.services.lambda.runtime.events.models.dynamodb.OperationType.INSERT;
import static com.amazonaws.services.lambda.runtime.events.models.dynamodb.OperationType.MODIFY;
import static com.amazonaws.services.lambda.runtime.events.models.dynamodb.OperationType.REMOVE;
import static java.util.UUID.randomUUID;
import static no.sikt.nva.nvi.test.TestUtils.randomCandidateBuilder;
import static no.unit.nva.testutils.RandomDataGenerator.randomElement;
import static no.unit.nva.testutils.RandomDataGenerator.randomString;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.core.StringContains.containsString;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import com.amazonaws.services.lambda.runtime.Context;
import com.amazonaws.services.lambda.runtime.events.DynamodbEvent;
import com.amazonaws.services.lambda.runtime.events.DynamodbEvent.DynamodbStreamRecord;
import com.amazonaws.services.lambda.runtime.events.models.dynamodb.AttributeValue;
import com.amazonaws.services.lambda.runtime.events.models.dynamodb.OperationType;
import com.amazonaws.services.lambda.runtime.events.models.dynamodb.StreamRecord;
import com.fasterxml.jackson.core.JsonProcessingException;
import java.net.URI;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import no.sikt.nva.nvi.common.StorageReader;
import no.sikt.nva.nvi.common.db.Candidate;
import no.sikt.nva.nvi.common.model.business.DbApprovalStatus;
import no.sikt.nva.nvi.common.model.business.DbStatus;
import no.sikt.nva.nvi.common.service.NviService;
import no.sikt.nva.nvi.index.aws.SearchClient;
import no.sikt.nva.nvi.index.model.Approval;
import no.sikt.nva.nvi.index.model.ApprovalStatus;
import no.sikt.nva.nvi.index.model.Contributor;
import no.sikt.nva.nvi.index.model.NviCandidateIndexDocument;
import no.sikt.nva.nvi.index.model.PublicationDetails;
import no.sikt.nva.nvi.test.LocalDynamoTest;
import no.unit.nva.commons.json.JsonUtils;
import nva.commons.core.StringUtils;
import nva.commons.core.ioutils.IoUtils;
import nva.commons.logutils.LogUtils;
import nva.commons.logutils.TestAppender;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.opensearch.client.opensearch._types.query_dsl.Query;
import org.opensearch.client.opensearch.core.SearchResponse;

class UpdateIndexHandlerTest extends LocalDynamoTest {

    public static final Context CONTEXT = mock(Context.class);
    public static final String CANDIDATE = IoUtils.stringFromResources(Path.of("candidate.json"));
    public static final String INSTITUTION_ID_FROM_EVENT = "https://api.dev.nva.aws.unit"
                                                           + ".no/cristin/organization/20754.0.0.0";
    private UpdateIndexHandler handler;
    private TestAppender appender;
    private StorageReader<URI> storageReader;
    private FakeSearchClient openSearchClient;
    private NviService nviService;

    @BeforeEach
    void setup() {
        storageReader = mock(StorageReader.class);
        openSearchClient = new FakeSearchClient();
        nviService = mock(NviService.class);
        handler = new UpdateIndexHandler(storageReader, openSearchClient, nviService);
        appender = LogUtils.getTestingAppenderForRootLogger();
    }

    @Test
    void shouldAddDocumentToIndexWhenIncomingEventIsInsert() throws JsonProcessingException {
        when(storageReader.read(any())).thenReturn(CANDIDATE);
        var persistedCandidate = randomCandidate();
        when(nviService.findById(any())).thenReturn(Optional.of(persistedCandidate));
        handler.handleRequest(createEvent(INSERT, toRecord("dynamoDbRecordApplicableEvent.json")), CONTEXT);
        var document = openSearchClient.getDocuments().get(0);
        var expectedDocument = constructExpectedDocument(persistedCandidate);

        assertThat(document, is(equalTo(expectedDocument)));
    }

    @Test
    void shouldUpdateExistingIndexDocumentWhenIncomingEventIsModify() throws JsonProcessingException {
        when(storageReader.read(any())).thenReturn(CANDIDATE);
        var persistedCandidate = randomCandidate();
        when(nviService.findById(any())).thenReturn(Optional.of(persistedCandidate));
        handler.handleRequest(createEvent(MODIFY, toRecord("dynamoDbRecordApplicableEvent.json")), CONTEXT);
        var document = openSearchClient.getDocuments().get(0);
        var expectedDocument = constructExpectedDocument(persistedCandidate);

        assertThat(document, is(equalTo(expectedDocument)));
    }

    @Test
    void shouldRemoveFromIndexWhenIncomingEventIsRemove() throws JsonProcessingException {
        when(storageReader.read(any())).thenReturn(CANDIDATE);
        when(nviService.findById(any())).thenReturn(Optional.of(randomCandidate()));

        handler.handleRequest(createEvent(MODIFY, toRecord("dynamoDbRecordNotApplicable.json")), CONTEXT);

        assertThat(appender.getMessages(), containsString(StringUtils.EMPTY_STRING));
    }

    @Test
    void shouldDoNothingWhenIncomingEventIsRemove() throws JsonProcessingException {
        when(storageReader.read(any())).thenReturn(CANDIDATE);
        when(nviService.findById(any())).thenReturn(Optional.of(randomCandidate()));

        handler.handleRequest(createEvent(REMOVE, toRecord("dynamoDbRecordApplicableEvent.json")), CONTEXT);

        assertThat(appender.getMessages(), containsString(StringUtils.EMPTY_STRING));
    }

    @Test
    void shouldDoNothingWhenConsumedRecordIsNotCandidate() throws JsonProcessingException {
        when(storageReader.read(any())).thenReturn(CANDIDATE);
        when(nviService.findById(any())).thenReturn(Optional.of(randomCandidate()));

        handler.handleRequest(createEvent(REMOVE, toRecord("dynamoDbUniqueEntryEvent.json")), CONTEXT);

        assertThat(appender.getMessages(), containsString(StringUtils.EMPTY_STRING));
    }

    private static List<Approval> constructExpectedApprovals() {
        return List.of(new Approval(
            "https://api.dev.nva.aws.unit.no/cristin/organization/20754.0.0.0",
            Map.of("nb", "Sikt – Kunnskapssektorens tjenesteleverandør",
                   "en", "Sikt - Norwegian Agency for Shared Services in Education and Research"),
            ApprovalStatus.PENDING, null));
    }

    private static PublicationDetails constructPublicationDetails() {
        return new PublicationDetails(
            "https://api.dev.nva.aws.unit.no/publication/01888b283f29-cae193c7-80fa-4f92-a164-c73b02c19f2d",
            "AcademicArticle",
            "Demo nvi candidate",
            "2023-06-04",
            List.of(new Contributor(
                "https://api.dev.nva.aws.unit.no/cristin/person/997998",
                "Mona Ullah",
                null,
                List.of("https://api.dev.nva.aws.unit.no/cristin/organization/20754.0.0.0")
            )));
    }

    private static DynamodbStreamRecord toRecord(String fileName) throws JsonProcessingException {
        return JsonUtils.dtoObjectMapper.readValue(IoUtils.stringFromResources(Path.of(fileName)),
                                                   DynamodbStreamRecord.class);
    }

    private static DynamodbEvent createEvent(OperationType operationType, DynamodbStreamRecord record) {
        var event = new DynamodbEvent();
        event.setRecords(List.of(dynamoRecord(operationType, record)));
        return event;
    }

    private static DynamodbEvent.DynamodbStreamRecord dynamoRecord(OperationType operationType,
                                                                   DynamodbStreamRecord record) {

        return (DynamodbStreamRecord) new DynamodbStreamRecord().withEventName(randomElement(operationType))
                                          .withEventID(randomString())
                                          .withAwsRegion(randomString())
                                          .withDynamodb(randomPayload())
                                          .withEventSource(randomString())
                                          .withEventVersion(randomString())
                                          .withDynamodb(record.getDynamodb());
    }

    private static StreamRecord randomPayload() {
        return new StreamRecord().withOldImage(Map.of(randomString(), new AttributeValue(randomString())))
                   .withNewImage(Map.of(randomString(), new AttributeValue(randomString())));
    }

    private static Candidate randomCandidate() {
        var candidate = randomCandidateBuilder();
        return new Candidate(randomUUID(),candidate.build(),  List.of(getApprovalStatus()));
    }

    private static DbApprovalStatus getApprovalStatus() {
        return DbApprovalStatus.builder()
                   .institutionId(URI.create(INSTITUTION_ID_FROM_EVENT))
                   .status(DbStatus.PENDING).build();
    }

    private NviCandidateIndexDocument constructExpectedDocument(Candidate candidate) {
        return new NviCandidateIndexDocument.Builder()
                   .withContext(URI.create("https://bibsysdev.github.io/src/nvi-context.json"))
                   .withIdentifier(candidate.identifier().toString())
                   .withApprovals(constructExpectedApprovals())
                   .withPublicationDetails(constructPublicationDetails())
                   .withNumberOfApprovals(1)
                   .build();
    }

    private static class FakeSearchClient implements SearchClient<NviCandidateIndexDocument> {

        private final List<NviCandidateIndexDocument> documents;

        public FakeSearchClient() {
            this.documents = new ArrayList<>();
        }

        @Override
        public void addDocumentToIndex(NviCandidateIndexDocument indexDocument) {
            documents.add(indexDocument);
        }

        @Override
        public void removeDocumentFromIndex(NviCandidateIndexDocument indexDocument) {

        }

        @Override
        public SearchResponse<NviCandidateIndexDocument> search(Query query, int offset, int size, String username,
                                                                URI customer) {
            return null;
        }

        @Override
        public void deleteIndex() {

        }

        public List<NviCandidateIndexDocument> getDocuments() {
            return documents;
        }
    }
}