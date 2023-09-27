package no.sikt.nva.nvi.index;

import static com.amazonaws.services.lambda.runtime.events.models.dynamodb.OperationType.INSERT;
import static com.amazonaws.services.lambda.runtime.events.models.dynamodb.OperationType.MODIFY;
import static com.amazonaws.services.lambda.runtime.events.models.dynamodb.OperationType.REMOVE;
import static java.util.UUID.randomUUID;
import static no.sikt.nva.nvi.index.utils.ExpandedResourceGenerator.createExpandedResource;
import static no.sikt.nva.nvi.index.utils.NviCandidateIndexDocumentGenerator.APPLICATION_JSON;
import static no.unit.nva.testutils.RandomDataGenerator.randomElement;
import static no.unit.nva.testutils.RandomDataGenerator.randomString;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.greaterThanOrEqualTo;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.notNullValue;
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
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.net.URI;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Stream;
import no.sikt.nva.nvi.common.StorageReader;
import no.sikt.nva.nvi.common.db.Candidate;
import no.sikt.nva.nvi.common.db.model.DbApprovalStatus;
import no.sikt.nva.nvi.common.db.model.DbInstitutionPoints;
import no.sikt.nva.nvi.common.db.model.DbPublicationDate;
import no.sikt.nva.nvi.common.db.model.DbStatus;
import no.sikt.nva.nvi.common.db.model.DbUsername;
import no.sikt.nva.nvi.common.service.NviService;
import no.sikt.nva.nvi.index.aws.SearchClient;
import no.sikt.nva.nvi.index.model.Affiliation;
import no.sikt.nva.nvi.index.model.Approval;
import no.sikt.nva.nvi.index.model.ApprovalStatus;
import no.sikt.nva.nvi.index.model.Contributor;
import no.sikt.nva.nvi.index.model.NviCandidateIndexDocument;
import no.sikt.nva.nvi.index.model.NviCandidateIndexDocument.Builder;
import no.sikt.nva.nvi.index.model.PublicationDetails;
import no.sikt.nva.nvi.index.utils.NviCandidateIndexDocumentGenerator;
import no.sikt.nva.nvi.test.LocalDynamoTest;
import no.sikt.nva.nvi.test.TestUtils;
import no.unit.nva.auth.uriretriever.AuthorizedBackendUriRetriever;
import no.unit.nva.commons.json.JsonUtils;
import nva.commons.core.StringUtils;
import nva.commons.core.ioutils.IoUtils;
import nva.commons.logutils.LogUtils;
import nva.commons.logutils.TestAppender;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import org.opensearch.client.opensearch.core.SearchResponse;

class UpdateIndexHandlerTest extends LocalDynamoTest {

    private static final Context CONTEXT = mock(Context.class);
    private static final String CANDIDATE = IoUtils.stringFromResources(Path.of("candidate.json"));
    private static final String INSTITUTION_ID_FROM_EVENT = "https://api.dev.nva.aws.unit"
                                                            + ".no/cristin/organization/20754.0.0.0";
    private static final URI CANDIDATE_CONTEXT = URI.create("https://bibsysdev.github.io/src/nvi-context.json");
    private static final int POINTS_SCALE = 4;
    private static final RoundingMode ROUNDING_MODE = RoundingMode.HALF_UP;
    private UpdateIndexHandler handler;
    private TestAppender appender;
    private StorageReader<URI> storageReader;
    private FakeSearchClient openSearchClient;
    private NviService nviService;
    private AuthorizedBackendUriRetriever uriRetriever;
    private NviCandidateIndexDocumentGenerator documentGenerator;

    @BeforeEach
    void setup() {
        storageReader = mock(StorageReader.class);
        uriRetriever = mock(AuthorizedBackendUriRetriever.class);
        openSearchClient = new FakeSearchClient();
        nviService = mock(NviService.class);
        documentGenerator = new NviCandidateIndexDocumentGenerator(uriRetriever);
        handler = new UpdateIndexHandler(storageReader, openSearchClient, nviService, documentGenerator);
        appender = LogUtils.getTestingAppenderForRootLogger();

        when(uriRetriever.getRawContent(any(), any()))
            .thenReturn(Optional.of(IoUtils.stringFromResources(Path.of("20754.0.0.6.json"))));
    }

    @Test
    void shouldAddDocumentToIndexWhenIncomingEventIsInsertAndCandidateIsApplicableAndTopLevelOrgsIsAList()
        throws JsonProcessingException {
        when(storageReader.read(any())).thenReturn(CANDIDATE);
        var persistedCandidate = randomApplicableCandidate();
        when(nviService.findCandidateById(any())).thenReturn(Optional.of(persistedCandidate));
        handler.handleRequest(createEvent(INSERT, toRecord("dynamoDbRecordApplicableEvent.json")), CONTEXT);
        var document = openSearchClient.getDocuments().get(0);
        var expectedDocument = constructExpectedDocument(persistedCandidate);

        assertThat(document, is(equalTo(expectedDocument)));
    }

    @Test
    void shouldAddDocumentToIndexWithTopLevelsWhenIncommingEventHasAAffilition()
        throws JsonProcessingException {
        when(storageReader.read(any())).thenReturn(CANDIDATE);
        var persistedCandidate = randomApplicableCandidate();
        when(nviService.findCandidateById(any())).thenReturn(Optional.of(persistedCandidate));
        var str = IoUtils.stringFromResources(Path.of("20754.0.0.6.json"));
        var expectedUri = URI.create("https://api.dev.nva.aws.unit.no/cristin/organization/20754.0.0.6");
        when(uriRetriever.getRawContent(expectedUri, APPLICATION_JSON))
            .thenReturn(Optional.of(str));
        handler.handleRequest(createEvent(INSERT, toRecord("dynamoDbRecordApplicableEvent.json")), CONTEXT);
        var document = openSearchClient.getDocuments().get(0);
        var topLevels = document.publicationDetails().contributors().get(0).affiliations().get(0).partOf();

        assertThat(topLevels, is(notNullValue()));
        assertThat(topLevels, hasSize(greaterThanOrEqualTo(1)));
    }

    @Test
    void shouldUpdateExistingIndexDocumentWhenIncomingEventIsModifyAndCandidateIsApplicable()
        throws JsonProcessingException {
        when(storageReader.read(any())).thenReturn(CANDIDATE);
        var persistedCandidate = randomApplicableCandidate();
        when(nviService.findCandidateById(any())).thenReturn(Optional.of(persistedCandidate));
        handler.handleRequest(createEvent(MODIFY, toRecord("dynamoDbRecordApplicableEvent.json")), CONTEXT);
        var document = openSearchClient.getDocuments().get(0);
        var expectedDocument = constructExpectedDocument(persistedCandidate);

        assertThat(document, is(equalTo(expectedDocument)));
    }

    @Test
    void shouldRemoveFromIndexWhenIncomingEventIsModifyAndCandidateIsNotApplicable() throws JsonProcessingException {
        when(storageReader.read(any())).thenReturn(CANDIDATE);
        when(nviService.findCandidateById(any())).thenReturn(Optional.of(randomCandidate(false)));

        handler.handleRequest(createEvent(MODIFY, toRecord("dynamoDbRecordNotApplicable.json")), CONTEXT);

        assertThat(appender.getMessages(), containsString(StringUtils.EMPTY_STRING));
    }

    @Test
    void shouldDoNothingWhenIncomingEventIsRemove() throws JsonProcessingException {
        when(storageReader.read(any())).thenReturn(CANDIDATE);
        when(nviService.findCandidateById(any())).thenReturn(Optional.of(randomCandidate(false)));

        handler.handleRequest(createEvent(REMOVE, toRecord("dynamoDbRecordApplicableEvent.json")), CONTEXT);

        assertThat(appender.getMessages(), containsString(StringUtils.EMPTY_STRING));
    }

    @Test
    void shouldDoNothingWhenConsumedRecordIsNotCandidate() throws JsonProcessingException {
        when(storageReader.read(any())).thenReturn(CANDIDATE);
        when(nviService.findCandidateById(any())).thenReturn(Optional.of(randomCandidate(true)));

        handler.handleRequest(createEvent(REMOVE, toRecord("dynamoDbUniqueEntryEvent.json")), CONTEXT);

        assertThat(appender.getMessages(), containsString(StringUtils.EMPTY_STRING));
    }

    @Test
    void shouldUpdateDocumentWithNewApprovalWhenIncomingEventIsApproval() throws JsonProcessingException {
        when(storageReader.read(any())).thenReturn(CANDIDATE);
        var candidate = applicableAssignedCandidate();
        when(nviService.findCandidateById(any())).thenReturn(Optional.of(candidate));
        handler.handleRequest(createEvent(MODIFY, toRecord("dynamoDbApprovalEvent.json")), CONTEXT);
        var expectedDocument = constructExpectedDocument(candidate);
        var document = openSearchClient.getDocuments().get(0);

        assertThat(document, is(equalTo(expectedDocument)));
    }

    @ParameterizedTest
    @MethodSource("publicationDates")
    void shouldAddDocumentToIndexWhenNviCandidateExistsInResourcesStorageWithDifferentDateFormats(
        DbPublicationDate date) throws JsonProcessingException {
        var candidate = createApplicableCandidateWithPublicationDate(date);
        var expectedDocument = constructExpectedIndexDocument(date, candidate);
        when(storageReader.read(any())).thenReturn(createExpandedResource(expectedDocument));
        when(nviService.findCandidateById(any())).thenReturn(Optional.of(candidate));

        handler.handleRequest(createEvent(INSERT, createDynamoDbRecord(candidate)), CONTEXT);

        var document = openSearchClient.getDocuments().get(0);

        assertThat(document, is(equalTo(expectedDocument)));
    }

    private static Candidate createApplicableCandidateWithPublicationDate(DbPublicationDate date) {
        return new Candidate(UUID.randomUUID(), TestUtils.randomCandidateBuilder(true)
                                                    .applicable(true)
                                                    .creators(Collections.emptyList())
                                                    .publicationDate(date).build(),
                             Collections.emptyList(),
                             Collections.emptyList());
    }

    private static NviCandidateIndexDocument constructExpectedIndexDocument(DbPublicationDate date,
                                                                            Candidate candidate) {
        return new Builder()
                   .withContext(CANDIDATE_CONTEXT)
                   .withIdentifier(candidate.identifier().toString())
                   .withApprovals(Collections.emptyList())
                   .withPoints(sumPoints(candidate.candidate().points()))
                   .withNumberOfApprovals(candidate.approvalStatuses().size())
                   .withPublicationDetails(new PublicationDetails(candidate.candidate().publicationId().toString(),
                                                                  candidate.candidate().instanceType().getValue(),
                                                                  randomString(),
                                                                  getExpectedPublicationDate(date),
                                                                  Collections.emptyList()))
                   .build();
    }

    private static BigDecimal sumPoints(List<DbInstitutionPoints> points) {
        return points.stream().map(DbInstitutionPoints::points)
                   .reduce(BigDecimal.ZERO, BigDecimal::add)
                   .setScale(POINTS_SCALE, ROUNDING_MODE);
    }

    private static String getExpectedPublicationDate(DbPublicationDate date) {
        return Objects.nonNull(date.month()) && Objects.nonNull(date.day())
                   ? date.year() + "-" + date.month() + "-" + date.day()
                   : date.year();
    }

    private static Stream<DbPublicationDate> publicationDates() {
        return Stream.of(new DbPublicationDate("2023", null, null),
                         new DbPublicationDate("2023", "01", "03"));
    }

    private static List<Approval> constructExpectedApprovals(Candidate candidate) {
        return candidate.approvalStatuses().stream()
                   .map(approval -> new Approval(approval.institutionId().toString(), getLabels(),
                                                 ApprovalStatus.fromValue(approval.status().getValue()),
                                                 Optional.of(approval)
                                                     .map(DbApprovalStatus::assignee)
                                                     .map(DbUsername::getValue)
                                                     .orElse(null)))
                   .toList();
    }

    private static Map<String, String> getLabels() {
        return Map.of("nb", "Sikt – Kunnskapssektorens tjenesteleverandør",
                      "en", "Sikt - Norwegian Agency for Shared Services in Education and Research");
    }

    private static PublicationDetails constructPublicationDetails() {
        return new PublicationDetails(
            "https://api.dev.nva.aws.unit.no/publication/01888b283f29-cae193c7-80fa-4f92-a164-c73b02c19f2d",
            "AcademicArticle",
            "Demo nvi candidate",
            "2023-06-04",
            List.of(new Contributor.Builder()
                        .withId("https://api.dev.nva.aws.unit.no/cristin/person/997998")
                        .withName("Mona Ullah")
                        .withRole("Creator")
                        .withAffiliations(List.of(constructAffiliation()))
                        .build()
            ));
    }

    private static Affiliation constructAffiliation() {
        return new Affiliation.Builder().withId("https://api.dev.nva.aws.unit"
                                         + ".no/cristin/organization/20754"
                                         + ".0.0.0").withPartOf(List.of(
            "https://api.dev.nva.aws.unit.no/cristin/organization/20754.0.0.0")).build();
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

    private static Candidate randomCandidate(boolean applicable) {
        var candidate = TestUtils.randomCandidateBuilder(applicable);
        return new Candidate(randomUUID(), candidate.build(), List.of(getApprovalStatus()), Collections.emptyList());
    }

    private static Candidate randomApplicableCandidate() {
        var applicableCandidate = TestUtils.randomCandidateBuilder(true).build();
        return new Candidate(randomUUID(), applicableCandidate, List.of(getApprovalStatus()), Collections.emptyList());
    }

    private static Candidate applicableAssignedCandidate() {
        var applicableCandidate = TestUtils.randomCandidateBuilder(true).build();
        return new Candidate(randomUUID(), applicableCandidate, List.of(approvalWithAssignee()),
                             Collections.emptyList());
    }

    private static DbApprovalStatus getApprovalStatus() {
        return DbApprovalStatus.builder()
                   .institutionId(URI.create(INSTITUTION_ID_FROM_EVENT))
                   .status(DbStatus.PENDING).build();
    }

    private static DbApprovalStatus approvalWithAssignee() {
        return DbApprovalStatus.builder()
                   .institutionId(URI.create(INSTITUTION_ID_FROM_EVENT))
                   .assignee(DbUsername.fromString(randomString()))
                   .status(DbStatus.PENDING).build();
    }

    private DynamodbStreamRecord createDynamoDbRecord(Candidate candidate) throws JsonProcessingException {
        return JsonUtils.dtoObjectMapper.readValue(IoUtils.stringFromResources(Path.of("genericDynamoDbRecord.json"))
                                                       .replace("__REPLACE_IDENTIFIER__",
                                                                candidate.identifier().toString()),
                                                   DynamodbStreamRecord.class);
    }

    private NviCandidateIndexDocument constructExpectedDocument(Candidate candidate) {
        return new NviCandidateIndexDocument.Builder()
                   .withContext(URI.create("https://bibsysdev.github.io/src/nvi-context.json"))
                   .withIdentifier(candidate.identifier().toString())
                   .withApprovals(constructExpectedApprovals(candidate))
                   .withPublicationDetails(constructPublicationDetails())
                   .withNumberOfApprovals(candidate.approvalStatuses().size())
                   .withPoints(sumPoint(candidate.candidate().points()))
                   .build();
    }

    private BigDecimal sumPoint(List<DbInstitutionPoints> points) {
        return points.stream().map(DbInstitutionPoints::points)
                   .reduce(BigDecimal.ZERO, BigDecimal::add)
                   .setScale(POINTS_SCALE, ROUNDING_MODE);
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
        public SearchResponse<NviCandidateIndexDocument> search(String affiliations, String filter, String username,
                                                                URI customer, int offset, int size) {
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