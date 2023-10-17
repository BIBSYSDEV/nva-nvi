package no.sikt.nva.nvi.index;

import static com.amazonaws.services.lambda.runtime.events.models.dynamodb.OperationType.INSERT;
import static com.amazonaws.services.lambda.runtime.events.models.dynamodb.OperationType.MODIFY;
import static com.amazonaws.services.lambda.runtime.events.models.dynamodb.OperationType.REMOVE;
import static no.sikt.nva.nvi.index.utils.NviCandidateIndexDocumentGenerator.APPLICATION_JSON;
import static no.sikt.nva.nvi.test.TestUtils.createUpsertCandidateRequest;
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
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
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
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Stream;
import no.sikt.nva.nvi.common.StorageReader;
import no.sikt.nva.nvi.common.db.ApprovalStatusDao;
import no.sikt.nva.nvi.common.db.ApprovalStatusDao.DbApprovalStatus;
import no.sikt.nva.nvi.common.db.ApprovalStatusDao.DbStatus;
import no.sikt.nva.nvi.common.db.CandidateDao;
import no.sikt.nva.nvi.common.db.CandidateDao.DbCandidate;
import no.sikt.nva.nvi.common.db.CandidateDao.DbCreator;
import no.sikt.nva.nvi.common.db.CandidateDao.DbInstitutionPoints;
import no.sikt.nva.nvi.common.db.CandidateDao.DbLevel;
import no.sikt.nva.nvi.common.db.CandidateDao.DbPublicationDate;
import no.sikt.nva.nvi.common.db.CandidateRepository;
import no.sikt.nva.nvi.common.db.PeriodRepository;
import no.sikt.nva.nvi.common.db.model.InstanceType;
import no.sikt.nva.nvi.common.db.model.Username;
import no.sikt.nva.nvi.common.queue.NviSendMessageResponse;
import no.sikt.nva.nvi.common.queue.QueueClient;
import no.sikt.nva.nvi.common.service.ApprovalBO;
import no.sikt.nva.nvi.common.service.CandidateBO;
import no.sikt.nva.nvi.index.aws.SearchClient;
import no.sikt.nva.nvi.index.model.Affiliation;
import no.sikt.nva.nvi.index.model.Approval;
import no.sikt.nva.nvi.index.model.ApprovalStatus;
import no.sikt.nva.nvi.index.model.CandidateSearchParameters;
import no.sikt.nva.nvi.index.model.Contributor;
import no.sikt.nva.nvi.index.model.NviCandidateIndexDocument;
import no.sikt.nva.nvi.index.model.PublicationDate;
import no.sikt.nva.nvi.index.model.PublicationDetails;
import no.sikt.nva.nvi.index.utils.NviCandidateIndexDocumentGenerator;
import no.sikt.nva.nvi.test.LocalDynamoTest;
import no.unit.nva.auth.uriretriever.AuthorizedBackendUriRetriever;
import no.unit.nva.commons.json.JsonUtils;
import nva.commons.core.Environment;
import nva.commons.core.StringUtils;
import nva.commons.core.ioutils.IoUtils;
import nva.commons.logutils.LogUtils;
import nva.commons.logutils.TestAppender;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.opensearch.client.opensearch.core.SearchResponse;

class UpdateIndexHandlerTest extends LocalDynamoTest {

    public static final String DLQ_QUEUE_URL = "test_dlq_url";
    private static final Context CONTEXT = mock(Context.class);
    private static final String CANDIDATE = IoUtils.stringFromResources(Path.of("candidate.json"));
    private static final URI INSTITUTION_ID_FROM_EVENT = URI.create(
        "https://api.dev.nva.aws.unit.no/cristin/organization/20754.0.0.0");
    private static final int POINTS_SCALE = 4;
    private static final RoundingMode ROUNDING_MODE = RoundingMode.HALF_UP;
    private UpdateIndexHandler handler;
    private TestAppender appender;
    private StorageReader<URI> storageReader;
    private FakeSearchClient openSearchClient;
    private CandidateRepository candidateRepository;
    private PeriodRepository periodRepository;
    private AuthorizedBackendUriRetriever uriRetriever;
    private NviCandidateIndexDocumentGenerator generator;
    private QueueClient<NviSendMessageResponse> queueClient;
    private Environment env;

    @BeforeEach
    void setup() {
        env = mock(Environment.class);
        when(env.readEnv("UPDATE_INDEX_DLQ_QUEUE_URL")).thenReturn(DLQ_QUEUE_URL);

        storageReader = mock(StorageReader.class);
        uriRetriever = mock(AuthorizedBackendUriRetriever.class);
        openSearchClient = new FakeSearchClient();
        candidateRepository = new CandidateRepository(initializeTestDatabase());
        periodRepository = new PeriodRepository(initializeTestDatabase());
        generator = new NviCandidateIndexDocumentGenerator(uriRetriever);
        queueClient = mock(QueueClient.class);
        handler = new UpdateIndexHandler(storageReader, openSearchClient, candidateRepository, periodRepository,
                                         generator, queueClient, env);
        appender = LogUtils.getTestingAppenderForRootLogger();

        when(uriRetriever.getRawContent(any(), any())).thenReturn(
            Optional.of(IoUtils.stringFromResources(Path.of("20754.0.0.6.json"))));
    }

    @Test
    void shouldAddDocumentToIndexWhenIncomingEventIsInsertAndCandidateIsApplicableAndTopLevelOrgsIsAList()
        throws JsonProcessingException {
        var persistedCandidate = randomApplicableCandidate();
        mockRepositories(persistedCandidate);
        handler.handleRequest(createEvent(INSERT, toRecord("dynamoDbRecordApplicableEvent.json")), CONTEXT);
        var document = openSearchClient.getDocuments().get(0);
        var expectedDocument = constructExpectedDocument(persistedCandidate);

        assertThat(document, is(equalTo(expectedDocument)));
    }

    @Test
    void shouldAddDocumentToIndexWithTopLevelsWhenIncommingEventHasAAffilition() throws JsonProcessingException {
        var persistedCandidate = randomApplicableCandidate();
        mockRepositories(persistedCandidate);
        var str = IoUtils.stringFromResources(Path.of("20754.0.0.6.json"));
        var expectedUri = URI.create("https://api.dev.nva.aws.unit.no/cristin/organization/20754.0.0.6");
        when(uriRetriever.getRawContent(expectedUri, APPLICATION_JSON)).thenReturn(Optional.of(str));
        handler.handleRequest(createEvent(INSERT, toRecord("dynamoDbRecordApplicableEvent.json")), CONTEXT);
        var document = openSearchClient.getDocuments().get(0);
        var topLevels = document.publicationDetails().contributors().get(0).affiliations().get(0).partOf();

        assertThat(topLevels, is(notNullValue()));
        assertThat(topLevels, hasSize(greaterThanOrEqualTo(1)));
    }

    @Test
    void shouldUpdateExistingIndexDocumentWhenIncomingEventIsModifyAndCandidateIsApplicable()
        throws JsonProcessingException {
        var persistedCandidate = randomApplicableCandidate();
        mockRepositories(persistedCandidate);
        handler.handleRequest(createEvent(MODIFY, toRecord("dynamoDbRecordApplicableEvent.json")), CONTEXT);
        var document = openSearchClient.getDocuments().get(0);
        var expectedDocument = constructExpectedDocument(persistedCandidate);

        assertThat(document, is(equalTo(expectedDocument)));
    }

    @Test
    void indexingFailureShouldBeAddedToDlq()
        throws JsonProcessingException {
        mockRepositories(randomApplicableCandidate());
        when(candidateRepository.findCandidateDaoById(any())).thenReturn(Optional.empty());

        handler.handleRequest(createEvent(MODIFY, toRecord("dynamoDbRecordApplicableEvent.json")), CONTEXT);

        verify(queueClient, times(1)).sendMessage(any(String.class), eq(DLQ_QUEUE_URL));
    }

    @Test
    void shouldRemoveFromIndexWhenIncomingEventIsModifyAndCandidateIsNotApplicable() throws JsonProcessingException {
        var persistedCandidate = randomApplicableCandidate();
        mockRepositories(persistedCandidate);

        handler.handleRequest(createEvent(MODIFY, toRecord("dynamoDbRecordNotApplicable.json")), CONTEXT);

        assertThat(appender.getMessages(), containsString(StringUtils.EMPTY_STRING));
    }

    @Test
    void shouldDoNothingWhenIncomingEventIsRemove() throws JsonProcessingException {
        var persistedCandidate = randomApplicableCandidate();
        mockRepositories(persistedCandidate);

        handler.handleRequest(createEvent(REMOVE, toRecord("dynamoDbRecordApplicableEvent.json")), CONTEXT);

        assertThat(appender.getMessages(), containsString(StringUtils.EMPTY_STRING));
    }

    @Test
    void shouldDoNothingWhenConsumedRecordIsNotCandidate() throws JsonProcessingException {
        var persistedCandidate = randomApplicableCandidate();
        mockRepositories(persistedCandidate);

        handler.handleRequest(createEvent(REMOVE, toRecord("dynamoDbUniqueEntryEvent.json")), CONTEXT);

        assertThat(appender.getMessages(), containsString(StringUtils.EMPTY_STRING));
    }

    @Test
    void shouldUpdateDocumentWithNewApprovalWhenIncomingEventIsApproval() throws JsonProcessingException {
        var candidate = randomApplicableCandidate();
        mockRepositories(candidate);
        handler.handleRequest(createEvent(MODIFY, toRecord("dynamoDbApprovalEvent.json")), CONTEXT);
        var expectedDocument = constructExpectedDocument(candidate);
        var document = openSearchClient.getDocuments().get(0);

        assertThat(document, is(equalTo(expectedDocument)));
    }

    @ParameterizedTest
    @MethodSource("publicationDates")
    void shouldAddDocumentToIndexWhenNviCandidateExistsInResourcesStorageWithDifferentDateFormats(
        PublicationDate date, String persistedResourceDate)
        throws JsonProcessingException {
        var candidate = randomApplicableCandidate();
        mockRepositories(candidate);
        when(storageReader.read(any())).thenReturn(expandedResourceWithDate(persistedResourceDate));
        var expectedDocument = constructExpectedDocumentWithPublicationDate(candidate, date);
        handler.handleRequest(createEvent(INSERT, createDynamoDbRecord(candidate)), CONTEXT);
        var document = openSearchClient.getDocuments().get(0);

        assertThat(document, is(equalTo(expectedDocument)));
    }

    private static String expandedResourceWithDate(String date) {
        return CANDIDATE.replace("""
                                     "publicationDate": {
                                             "type": "PublicationDate",
                                             "day": "4",
                                             "month": "6",
                                             "year": "2023"
                                           },""", date);
    }

    private static Stream<Arguments> publicationDates() {
        return Stream.of(Arguments.of(new PublicationDate("2023", null, null), """
            "publicationDate": {
                    "type": "PublicationDate",
                    "year": "2023"
                  },"""), Arguments.of(new PublicationDate("2023", "6", "4"), """
            "publicationDate": {
                    "type": "PublicationDate",
                    "day": "4",
                    "month": "6",
                    "year": "2023"
                  },"""));
    }

    private static List<Approval> constructExpectedApprovals(Map<URI, ApprovalBO> approvals) {
        return approvals.keySet()
                   .stream()
                   .map(approval -> new Approval(approvals.get(approval).institutionId().toString(), getLabels(),
                                                 ApprovalStatus.fromValue(approvals.get(approval)
                                                                              .approval()
                                                                              .approvalStatus()
                                                                              .status()
                                                                              .getValue()),
                                                 Optional.of(approvals.get(approval).approval().approvalStatus())
                                                     .map(DbApprovalStatus::assignee)
                                                     .map(Username::value)
                                                     .orElse(null)))
                   .toList();
    }

    private static Map<String, String> getLabels() {
        return Map.of("nb", "Sikt – Kunnskapssektorens tjenesteleverandør", "en",
                      "Sikt - Norwegian Agency for Shared Services in Education and Research");
    }

    private static PublicationDetails constructPublicationDetails() {
        return new PublicationDetails(
            "https://api.dev.nva.aws.unit.no/publication/01888b283f29-cae193c7-80fa-4f92-a164-c73b02c19f2d",
            "AcademicArticle", "Demo nvi candidate", new PublicationDate("2023", "6", "4"), List.of(
            new Contributor.Builder().withId("https://api.dev.nva.aws.unit.no/cristin/person/997998")
                .withName("Mona Ullah")
                .withRole("Creator")
                .withAffiliations(List.of(constructAffiliation()))
                .build()));
    }

    private static PublicationDetails constructPublicationDetailsWithPublicationDate(PublicationDate publicationDate) {
        return new PublicationDetails(
            "https://api.dev.nva.aws.unit.no/publication/01888b283f29-cae193c7-80fa-4f92-a164-c73b02c19f2d",
            "AcademicArticle", "Demo nvi candidate", publicationDate, List.of(
            new Contributor.Builder().withId("https://api.dev.nva.aws.unit.no/cristin/person/997998")
                .withName("Mona Ullah")
                .withRole("Creator")
                .withAffiliations(List.of(constructAffiliation()))
                .build()));
    }

    private static Affiliation constructAffiliation() {
        return new Affiliation.Builder().withId(
                "https://api.dev.nva.aws.unit" + ".no/cristin/organization/20754" + ".0.0.0")
                   .withPartOf(List.of("https://api.dev.nva.aws.unit.no/cristin/organization/20754.0.0.0"))
                   .build();
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

    private void mockRepositories(CandidateBO persistedCandidate) {
        candidateRepository = mock(CandidateRepository.class);
        periodRepository = mock(PeriodRepository.class);
        when(storageReader.read(any())).thenReturn(CANDIDATE);
        when(candidateRepository.findCandidateDaoById(any())).thenReturn(Optional.of(toDao(persistedCandidate)));
        when(candidateRepository.fetchApprovals(any())).thenReturn(getApproval(persistedCandidate));
        handler = new UpdateIndexHandler(storageReader, openSearchClient, candidateRepository, periodRepository,
                                         generator, queueClient, env);
    }

    private CandidateDao toDao(CandidateBO candidate) {
        return CandidateDao.builder()
                   .identifier(candidate.identifier())
                   .candidate(DbCandidate.builder()
                                  .publicationId(candidate.publicationId())
                                  .points(candidate.getPoints())
                                  .applicable(candidate.isApplicable())
                                  .creatorCount(1)
                                  .instanceType(InstanceType.ACADEMIC_ARTICLE)
                                  .creators(List.of(new DbCreator(
                                      URI.create("https://api.dev.nva.aws.unit" + ".no/cristin/person/997998"), List.of(
                                      URI.create("https://api.dev.nva.aws.unit.no/cristin/organization/20754.0.0.0")))))
                                  .publicationDate(new DbPublicationDate("2023", "6", "4"))
                                  .internationalCollaboration(false)
                                  .level(DbLevel.LEVEL_ONE)
                                  .publicationBucketUri(candidate.getBucketUri())
                                  .build())
                   .build();
    }

    private List<ApprovalStatusDao> getApproval(CandidateBO persistedCandidate) {
        var approval = persistedCandidate.getApprovals().get(INSTITUTION_ID_FROM_EVENT);
        return List.of(ApprovalStatusDao.builder()
                           .approvalStatus(DbApprovalStatus.builder()
                                               .institutionId(approval.institutionId())
                                               .status(DbStatus.PENDING)
                                               .build())
                           .build());
    }

    private CandidateBO randomApplicableCandidate() {
        return CandidateBO.fromRequest(createUpsertCandidateRequest(INSTITUTION_ID_FROM_EVENT), candidateRepository,
                                       periodRepository).orElseThrow();
    }

    private DynamodbStreamRecord createDynamoDbRecord(CandidateBO candidate) throws JsonProcessingException {
        return JsonUtils.dtoObjectMapper.readValue(IoUtils.stringFromResources(Path.of("genericDynamoDbRecord.json"))
                                                       .replace("__REPLACE_IDENTIFIER__",
                                                                candidate.identifier().toString()),
                                                   DynamodbStreamRecord.class);
    }

    private NviCandidateIndexDocument constructExpectedDocument(CandidateBO candidate) {
        return new NviCandidateIndexDocument.Builder().withContext(
                URI.create("https://bibsysdev.github.io/src/nvi-context.json"))
                   .withIdentifier(candidate.identifier().toString())
                   .withApprovals(constructExpectedApprovals(candidate.getApprovals()))
                   .withPublicationDetails(constructPublicationDetails())
                   .withNumberOfApprovals(candidate.getApprovals().size())
                   .withPoints(sumPoint(candidate.getPoints()))
                   .build();
    }

    private NviCandidateIndexDocument constructExpectedDocumentWithPublicationDate(CandidateBO candidate,
                                                                                   PublicationDate publicationDate) {
        return new NviCandidateIndexDocument.Builder().withContext(
                URI.create("https://bibsysdev.github.io/src/nvi-context.json"))
                   .withIdentifier(candidate.identifier().toString())
                   .withApprovals(constructExpectedApprovals(candidate.getApprovals()))
                   .withPublicationDetails(constructPublicationDetailsWithPublicationDate(publicationDate))
                   .withNumberOfApprovals(candidate.getApprovals().size())
                   .withPoints(sumPoint(candidate.getPoints()))
                   .build();
    }

    private BigDecimal sumPoint(List<DbInstitutionPoints> points) {
        return points.stream()
                   .map(DbInstitutionPoints::points)
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
        public SearchResponse<NviCandidateIndexDocument> search(CandidateSearchParameters candidateSearchParameters) {
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