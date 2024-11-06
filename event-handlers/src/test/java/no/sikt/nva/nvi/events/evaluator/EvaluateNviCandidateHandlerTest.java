package no.sikt.nva.nvi.events.evaluator;

import static java.math.BigDecimal.ONE;
import static no.sikt.nva.nvi.common.service.model.InstanceType.ACADEMIC_COMMENTARY;
import static no.sikt.nva.nvi.common.service.model.InstanceType.ACADEMIC_LITERATURE_REVIEW;
import static no.sikt.nva.nvi.common.service.model.InstanceType.ACADEMIC_MONOGRAPH;
import static no.sikt.nva.nvi.events.evaluator.TestUtils.createEvent;
import static no.sikt.nva.nvi.events.evaluator.TestUtils.createResponse;
import static no.sikt.nva.nvi.events.evaluator.TestUtils.mockOrganizationResponseForAffiliation;
import static no.sikt.nva.nvi.events.evaluator.model.PublicationChannel.JOURNAL;
import static no.sikt.nva.nvi.events.evaluator.model.PublicationChannel.SERIES;
import static no.unit.nva.testutils.RandomDataGenerator.objectMapper;
import static nva.commons.core.attempt.Try.attempt;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.notNullValue;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import com.amazonaws.services.lambda.runtime.Context;
import java.io.IOException;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import no.sikt.nva.nvi.common.S3StorageReader;
import no.sikt.nva.nvi.common.client.OrganizationRetriever;
import no.sikt.nva.nvi.common.db.CandidateRepository;
import no.sikt.nva.nvi.common.db.NviPeriodDao.DbNviPeriod;
import no.sikt.nva.nvi.common.db.PeriodRepository;
import no.sikt.nva.nvi.common.service.model.Candidate;
import no.sikt.nva.nvi.common.service.model.InstanceType;
import no.sikt.nva.nvi.common.service.model.InstitutionPoints;
import no.sikt.nva.nvi.common.service.model.InstitutionPoints.CreatorAffiliationPoints;
import no.sikt.nva.nvi.events.evaluator.calculator.CreatorVerificationUtil;
import no.sikt.nva.nvi.events.evaluator.model.Level;
import no.sikt.nva.nvi.events.evaluator.model.PublicationChannel;
import no.sikt.nva.nvi.events.model.CandidateEvaluatedMessage;
import no.sikt.nva.nvi.events.model.NonNviCandidate;
import no.sikt.nva.nvi.events.model.NviCandidate;
import no.sikt.nva.nvi.events.model.NviCandidate.NviCreator;
import no.sikt.nva.nvi.events.model.PersistedResourceMessage;
import no.sikt.nva.nvi.events.model.PublicationDate;
import no.sikt.nva.nvi.test.FakeSqsClient;
import no.sikt.nva.nvi.test.LocalDynamoTest;
import no.unit.nva.auth.uriretriever.AuthorizedBackendUriRetriever;
import no.unit.nva.auth.uriretriever.BackendClientCredentials;
import no.unit.nva.auth.uriretriever.UriRetriever;
import no.unit.nva.s3.S3Driver;
import no.unit.nva.stubs.FakeS3Client;
import no.unit.nva.stubs.FakeSecretsManagerClient;
import nva.commons.core.Environment;
import nva.commons.core.StringUtils;
import nva.commons.core.ioutils.IoUtils;
import nva.commons.core.paths.UnixPath;
import nva.commons.core.paths.UriWrapper;
import nva.commons.logutils.LogUtils;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class EvaluateNviCandidateHandlerTest extends LocalDynamoTest {

    public static final RoundingMode ROUNDING_MODE = RoundingMode.HALF_UP;
    public static final PublicationDate HARDCODED_PUBLICATION_DATE = new PublicationDate(null, null, "2023");
    public static final URI HARDCODED_PUBLICATION_CHANNEL_ID = URI.create(
        "https://api.dev.nva.aws.unit.no/publication-channels/series/490845/2023");
    public static final URI SIKT_CRISTIN_ORG_ID = URI.create(
        "https://api.dev.nva.aws.unit.no/cristin/organization/20754.0.0.0");
    private static final URI HARDCODED_CREATOR_ID = URI.create("https://api.dev.nva.aws.unit.no/cristin/person/997998");
    private static final String ACADEMIC_CHAPTER_PATH = "evaluator/candidate_academicChapter.json";
    private static final int SCALE = 4;
    private static final String ACADEMIC_LITERATURE_REVIEW_JSON_PATH = "evaluator/candidate_academicLiteratureReview"
                                                                       + ".json";
    private static final String ACADEMIC_MONOGRAPH_JSON_PATH = "evaluator/candidate_academicMonograph.json";
    private static final String ACADEMIC_COMMENTARY_JSON_PATH = "evaluator/candidate_academicCommentary.json";
    private static final String BUCKET_NAME = "ignoredBucket";
    private static final String CUSTOMER_API_NVI_RESPONSE = "{" + "\"nviInstitution\" : \"true\"" + "}";
    private static final String ACADEMIC_ARTICLE_PATH = "evaluator/candidate_academicArticle.json";
    private static final URI HARDCODED_PUBLICATION_ID = URI.create(
        "https://api.dev.nva.aws.unit.no/publication/01888b283f29-cae193c7-80fa-4f92-a164-c73b02c19f2d");
    private static final String ACADEMIC_ARTICLE = IoUtils.stringFromResources(Path.of(ACADEMIC_ARTICLE_PATH))
                                                       .replace("__REPLACE_WITH_PUBLICATION_ID__",
                                                                HARDCODED_PUBLICATION_ID.toString());
    private static final String ERROR_COULD_NOT_FETCH_CRISTIN_ORG = "Could not fetch Cristin organization for: ";
    private static final String COULD_NOT_FETCH_CUSTOMER_MESSAGE = "Could not fetch customer for: ";
    private static final URI CRISTIN_NVI_ORG_TOP_LEVEL_ID = URI.create(
        "https://api.dev.nva.aws.unit.no/cristin/organization/194.0.0.0");
    private static final URI CRISTIN_NVI_ORG_SUB_UNIT_ID = URI.create(
        "https://api.dev.nva.aws.unit.no/cristin/organization/194.64.20.0");
    private static final URI CUSTOMER_API_CRISTIN_NVI_ORG_TOP_LEVEL = URI.create(
        "https://api.dev.nva.aws.unit.no/customer/cristinId/https%3A%2F%2Fapi"
        + ".dev.nva.aws.unit.no%2Fcristin%2Forganization%2F194.0.0.0");
    private final Context context = mock(Context.class);
    public HttpResponse<String> notFoundResponse;
    private HttpResponse<String> badResponse;
    private HttpResponse<String> okResponse;
    private S3Driver s3Driver;
    private EvaluateNviCandidateHandler handler;
    private AuthorizedBackendUriRetriever authorizedBackendUriRetriever;
    private UriRetriever uriRetriever;
    private FakeSqsClient queueClient;
    private CandidateRepository candidateRepository;
    private PeriodRepository periodRepository;
    private Environment env;
    private EvaluatorService evaluatorService;
    private S3StorageReader storageReader;

    @BeforeEach
    void setUp() {
        env = mock(Environment.class);
        when(env.readEnv("CANDIDATE_QUEUE_URL")).thenReturn("My test candidate queue url");
        when(env.readEnv("CANDIDATE_DLQ_URL")).thenReturn("My test candidate dlq url");
        setupHttpResponses();
        mockSecretManager();
        var s3Client = new FakeS3Client();
        authorizedBackendUriRetriever = mock(AuthorizedBackendUriRetriever.class);
        queueClient = new FakeSqsClient();
        s3Driver = new S3Driver(s3Client, BUCKET_NAME);
        var dynamoDbClient = initializeTestDatabase();
        uriRetriever = mock(UriRetriever.class);
        storageReader = new S3StorageReader(s3Client, BUCKET_NAME);
        periodRepository = new PeriodRepository(dynamoDbClient);
        var calculator = new CreatorVerificationUtil(authorizedBackendUriRetriever, uriRetriever);
        var organizationRetriever = new OrganizationRetriever(uriRetriever);
        var pointCalculator = new PointService(organizationRetriever);
        candidateRepository = new CandidateRepository(dynamoDbClient);
        evaluatorService = new EvaluatorService(storageReader, calculator, pointCalculator, candidateRepository,
                                                periodRepository);
        handler = new EvaluateNviCandidateHandler(evaluatorService, queueClient, env);
    }

    @Test
    void shouldCreateNewCandidateEventOnValidCandidate() throws IOException {
        when(authorizedBackendUriRetriever.fetchResponse(any(), any())).thenReturn(Optional.of(okResponse));
        mockOrganizationResponseForAffiliation(SIKT_CRISTIN_ORG_ID, null, uriRetriever);
        var path = "evaluator/candidate.json";
        var content = IoUtils.inputStreamFromResources(path);
        var fileUri = s3Driver.insertFile(UnixPath.of(path), content);
        var event = createEvent(new PersistedResourceMessage(fileUri));
        handler.handleRequest(event, context);
        var candidate = (NviCandidate) getMessageBody().candidate();
        assertThat(candidate.publicationBucketUri(), is(equalTo(fileUri)));
    }

    @Test
    void shouldNotEvaluateExistingCandidateInClosedPeriod() throws IOException {
        var year = LocalDateTime.now().getYear();
        var resourceFileUri = setUpCandidate(year);
        periodRepository = no.sikt.nva.nvi.test.TestUtils.periodRepositoryReturningClosedPeriod(year);
        setupEvaluatorService(periodRepository);
        handler = new EvaluateNviCandidateHandler(evaluatorService, queueClient, env);
        var event = createEvent(new PersistedResourceMessage(resourceFileUri));
        handler.handleRequest(event, context);
        assertEquals(0, queueClient.getSentMessages().size());
    }

    @Test
    void shouldSkipEvaluationAndLogWarningOnPublicationWithInvalidYear() throws IOException {
        var invalidYear = "1948-1997";
        var fileUri = setUpPublicationWithInvalidYear(invalidYear);
        var event = createEvent(new PersistedResourceMessage(fileUri));
        final var logAppender = LogUtils.getTestingAppender(EvaluatorService.class);
        handler.handleRequest(event, context);
        var expectedLogMessage = String.format("Skipping evaluation due to invalid year format %s. Publication id %s",
                                               invalidYear, HARDCODED_PUBLICATION_ID);
        assertTrue(logAppender.getMessages().contains(expectedLogMessage));
        assertEquals(0, queueClient.getSentMessages().size());
    }

    @Test
    void shouldEvaluateExistingCandidateInOpenPeriod() throws IOException {
        mockCristinResponseAndCustomerApiResponseForNviInstitution(okResponse);
        var year = LocalDateTime.now().getYear();
        var resourceFileUri = setUpCandidate(year);
        periodRepository = no.sikt.nva.nvi.test.TestUtils.periodRepositoryReturningOpenedPeriod(year);
        setupEvaluatorService(periodRepository);
        handler = new EvaluateNviCandidateHandler(evaluatorService, queueClient, env);
        var event = createEvent(new PersistedResourceMessage(resourceFileUri));
        handler.handleRequest(event, context);
        var candidate = (NviCandidate) getMessageBody().candidate();
        assertThat(candidate.publicationBucketUri(), is(equalTo(resourceFileUri)));
    }

    @Test
    void shouldEvaluateStrippedCandidate() throws IOException {
        when(authorizedBackendUriRetriever.fetchResponse(any(), any())).thenReturn(Optional.of(okResponse));
        mockOrganizationResponseForAffiliation(SIKT_CRISTIN_ORG_ID, null, uriRetriever);
        var path = "evaluator/candidate_stripped.json";
        var content = IoUtils.inputStreamFromResources(path);
        var fileUri = s3Driver.insertFile(UnixPath.of(path), content);
        var event = createEvent(new PersistedResourceMessage(fileUri));
        handler.handleRequest(event, context);
        var candidate = (NviCandidate) getMessageBody().candidate();
        assertThat(candidate.publicationBucketUri(), is(equalTo(fileUri)));
    }

    @Test
    void shouldCreateNewCandidateWithPointsOnlyForNviInstitutions() throws IOException {
        mockCristinResponseAndCustomerApiResponseForNviInstitution(okResponse);
        mockCristinResponseAndCustomerApiResponseForNonNviInstitution();
        var content = IoUtils.inputStreamFromResources(
            "evaluator/candidate_verifiedCreator_with_nonNviInstitution.json");
        var fileUri = s3Driver.insertFile(
            UnixPath.of("evaluator/candidate_verifiedCreator_with_nonNviInstitution.json"),
            content);
        var event = createEvent(new PersistedResourceMessage(fileUri));
        handler.handleRequest(event, context);
        var messageBody = getMessageBody();
        var candidate = (NviCandidate) messageBody.candidate();
        assertEquals(1, candidate.institutionPoints().size());
        assertNotNull(candidate.getPointsForInstitution(CRISTIN_NVI_ORG_TOP_LEVEL_ID));
    }

    @Test
    void shouldCreateNewCandidateEventOnValidAcademicArticle() throws IOException {
        mockCristinResponseAndCustomerApiResponseForNviInstitution(okResponse);
        var fileUri = s3Driver.insertFile(UnixPath.of(ACADEMIC_ARTICLE_PATH), ACADEMIC_ARTICLE);
        var event = createEvent(new PersistedResourceMessage(fileUri));
        handler.handleRequest(event, context);
        var messageBody = getMessageBody();
        var expectedPoints = BigDecimal.valueOf(1).setScale(SCALE, ROUNDING_MODE);
        var expectedEvaluatedMessage = getExpectedEvaluatedMessage(InstanceType.ACADEMIC_ARTICLE,
                                                                   expectedPoints,
                                                                   fileUri, JOURNAL,
                                                                   ONE, expectedPoints);
        assertEquals(expectedEvaluatedMessage, messageBody);
    }

    @Test
    void shouldCreateNewCandidateEventWithCorrectDataOnValidAcademicChapter() throws IOException {
        mockCristinResponseAndCustomerApiResponseForNviInstitution(okResponse);
        var content = IoUtils.inputStreamFromResources(ACADEMIC_CHAPTER_PATH);
        var fileUri = s3Driver.insertFile(UnixPath.of(ACADEMIC_CHAPTER_PATH), content);
        var event = createEvent(new PersistedResourceMessage(fileUri));
        handler.handleRequest(event, context);
        var messageBody = getMessageBody();
        var expectedPoints = BigDecimal.valueOf(1).setScale(SCALE, ROUNDING_MODE);
        var expectedEvaluatedMessage = getExpectedEvaluatedMessage(InstanceType.ACADEMIC_CHAPTER,
                                                                   expectedPoints,
                                                                   fileUri, SERIES,
                                                                   ONE, expectedPoints);
        assertEquals(expectedEvaluatedMessage, messageBody);
    }

    @Test
    void shouldCreateNewCandidateEventWithCorrectDataOnValidAcademicMonograph() throws IOException {
        mockCristinResponseAndCustomerApiResponseForNviInstitution(okResponse);
        var content = IoUtils.inputStreamFromResources(ACADEMIC_MONOGRAPH_JSON_PATH);
        var fileUri = s3Driver.insertFile(UnixPath.of(ACADEMIC_MONOGRAPH_JSON_PATH), content);
        var event = createEvent(new PersistedResourceMessage(fileUri));
        handler.handleRequest(event, context);
        var messageBody = getMessageBody();
        var expectedPoints = BigDecimal.valueOf(5).setScale(SCALE, ROUNDING_MODE);
        var expectedEvaluatedMessage = getExpectedEvaluatedMessage(ACADEMIC_MONOGRAPH,
                                                                   expectedPoints,
                                                                   fileUri, SERIES,
                                                                   BigDecimal.valueOf(5), expectedPoints
        );
        assertEquals(expectedEvaluatedMessage, messageBody);
    }

    @Test
    void shouldCreateNewCandidateEventWithCorrectDataOnValidAcademicCommentary() throws IOException {
        mockCristinResponseAndCustomerApiResponseForNviInstitution(okResponse);
        var content = IoUtils.inputStreamFromResources(ACADEMIC_COMMENTARY_JSON_PATH);
        var fileUri = s3Driver.insertFile(UnixPath.of(ACADEMIC_COMMENTARY_JSON_PATH), content);
        var event = createEvent(new PersistedResourceMessage(fileUri));
        handler.handleRequest(event, context);
        var messageBody = getMessageBody();
        var expectedPoints = BigDecimal.valueOf(5).setScale(SCALE, ROUNDING_MODE);
        var expectedEvaluatedMessage = getExpectedEvaluatedMessage(ACADEMIC_COMMENTARY,
          expectedPoints,
          fileUri, SERIES,
          BigDecimal.valueOf(5), expectedPoints
        );
        assertEquals(expectedEvaluatedMessage, messageBody);
    }

    @Test
    void shouldCreateNewCandidateEventWithCorrectDataOnValidAcademicLiteratureReview() throws IOException {
        mockCristinResponseAndCustomerApiResponseForNviInstitution(okResponse);
        var content = IoUtils.inputStreamFromResources(ACADEMIC_LITERATURE_REVIEW_JSON_PATH);
        var fileUri = s3Driver.insertFile(UnixPath.of(ACADEMIC_LITERATURE_REVIEW_JSON_PATH), content);
        var event = createEvent(new PersistedResourceMessage(fileUri));
        handler.handleRequest(event, context);
        var messageBody = getMessageBody();
        var expectedPoints = BigDecimal.valueOf(1).setScale(SCALE, ROUNDING_MODE);
        var expectedEvaluatedMessage = getExpectedEvaluatedMessage(ACADEMIC_LITERATURE_REVIEW,
                                                                   expectedPoints,
                                                                   fileUri, JOURNAL,
                                                                   ONE, expectedPoints);
        assertEquals(expectedEvaluatedMessage, messageBody);
    }

    @Test
    void shouldCalculatePointsOnValidAcademicArticle() throws IOException {
        mockCristinResponseAndCustomerApiResponseForNviInstitution(okResponse);
        var fileUri = s3Driver.insertFile(UnixPath.of(ACADEMIC_ARTICLE_PATH), ACADEMIC_ARTICLE);
        var event = createEvent(new PersistedResourceMessage(fileUri));
        handler.handleRequest(event, context);
        var messageBody = getMessageBody();
        var candidate = (NviCandidate) messageBody.candidate();
        assertThat(candidate.institutionPoints(), notNullValue());
        assertThat(candidate.getPointsForInstitution(CRISTIN_NVI_ORG_TOP_LEVEL_ID),
                   is(equalTo(BigDecimal.valueOf(1).setScale(4, RoundingMode.HALF_UP))));
    }

    @Test
    void shouldCreateInstitutionApprovalsForTopLevelInstitutions() throws IOException {
        mockCristinResponseAndCustomerApiResponseForNviInstitution(okResponse);
        var fileUri = s3Driver.insertFile(UnixPath.of(ACADEMIC_ARTICLE_PATH), ACADEMIC_ARTICLE);
        var event = createEvent(new PersistedResourceMessage(fileUri));
        handler.handleRequest(event, context);
        var messageBody = getMessageBody();
        var candidate = (NviCandidate) messageBody.candidate();
        assertThat(candidate.institutionPoints(), notNullValue());
        assertThat(candidate.getPointsForInstitution(CRISTIN_NVI_ORG_TOP_LEVEL_ID), notNullValue());
    }

    @Test
    void shouldCreateNewCandidateEventOnValidAcademicChapterWithoutSeriesLevelWithPublisherLevel() throws IOException {
        mockCristinResponseAndCustomerApiResponseForNviInstitution(okResponse);
        var path = "evaluator/candidate_academicChapter_seriesLevelEmptyPublisherLevelOne.json";
        var content = IoUtils.inputStreamFromResources(path);
        var fileUri = s3Driver.insertFile(UnixPath.of(path), content);
        var event = createEvent(new PersistedResourceMessage(fileUri));
        handler.handleRequest(event, context);
        var candidate = (NviCandidate) getMessageBody().candidate();
        assertThat(candidate.publicationBucketUri(), is(equalTo(fileUri)));
    }

    @Test
    void shouldCreateNewCandidateEventOnValidAcademicCommentaryWithoutSeriesLevelWithPublisherLevel() throws IOException {
        mockCristinResponseAndCustomerApiResponseForNviInstitution(okResponse);
        var path = "evaluator/candidate_academicCommentary_withoutSeries.json";
        var content = IoUtils.inputStreamFromResources(path);
        var fileUri = s3Driver.insertFile(UnixPath.of(path), content);
        var event = createEvent(new PersistedResourceMessage(fileUri));
        handler.handleRequest(event, context);
        var candidate = (NviCandidate) getMessageBody().candidate();
        assertThat(candidate.publicationBucketUri(), is(equalTo(fileUri)));
    }

    @Test
    void shouldCreateNewCandidateEventOnValidAcademicMonograph() throws IOException {
        mockCristinResponseAndCustomerApiResponseForNviInstitution(okResponse);
        var content = IoUtils.inputStreamFromResources(ACADEMIC_MONOGRAPH_JSON_PATH);
        var fileUri = s3Driver.insertFile(UnixPath.of(ACADEMIC_MONOGRAPH_JSON_PATH), content);
        var event = createEvent(new PersistedResourceMessage(fileUri));
        handler.handleRequest(event, context);
        var candidate = (NviCandidate) getMessageBody().candidate();
        assertThat(candidate.publicationBucketUri(), is(equalTo(fileUri)));
    }

    @Test
    void shouldCreateNewCandidateEventOnValidAcademicLiteratureReview() throws IOException {
        mockCristinResponseAndCustomerApiResponseForNviInstitution(okResponse);
        var content = IoUtils.inputStreamFromResources(ACADEMIC_LITERATURE_REVIEW_JSON_PATH);
        var fileUri = s3Driver.insertFile(UnixPath.of(ACADEMIC_LITERATURE_REVIEW_JSON_PATH), content);
        var event = createEvent(new PersistedResourceMessage(fileUri));
        handler.handleRequest(event, context);
        var candidate = (NviCandidate) getMessageBody().candidate();
        assertThat(candidate.publicationBucketUri(), is(equalTo(fileUri)));
    }

    @Test
    void shouldCreateNonCandidateEventOnAcademicChapterWithSeriesLevelZero() throws IOException {
        var path = "evaluator/nonCandidate_academicChapter_seriesLevelZero.json";
        var content = IoUtils.inputStreamFromResources(path);
        var fileUri = s3Driver.insertFile(UnixPath.of(path), content);
        var event = createEvent(new PersistedResourceMessage(fileUri));
        handler.handleRequest(event, context);
        var nonCandidate = (NonNviCandidate) getMessageBody().candidate();
        assertThat(nonCandidate.publicationId(), is(equalTo(HARDCODED_PUBLICATION_ID)));
    }

    @Test
    void shouldCreateNonCandidateEventOnAcademicCommentaryWithSeriesLevelZero() throws IOException {
        var path = "evaluator/nonCandidate_academicCommentary_seriesLevelZero.json";
        var content = IoUtils.inputStreamFromResources(path);
        var fileUri = s3Driver.insertFile(UnixPath.of(path), content);
        var event = createEvent(new PersistedResourceMessage(fileUri));
        handler.handleRequest(event, context);
        var nonCandidate = (NonNviCandidate) getMessageBody().candidate();
        assertThat(nonCandidate.publicationId(), is(equalTo(HARDCODED_PUBLICATION_ID)));
    }

    @Test
    void shouldCreateNonCandidateEventWhenIdentityIsNotVerified() throws IOException {
        var path = "evaluator/nonCandidate_nonVerified.json";
        var content = IoUtils.inputStreamFromResources(path);
        var fileUri = s3Driver.insertFile(UnixPath.of(path), content);
        var event = createEvent(new PersistedResourceMessage(fileUri));
        handler.handleRequest(event, context);
        var nonCandidate = (NonNviCandidate) getMessageBody().candidate();
        assertThat(nonCandidate.publicationId(), is(equalTo(HARDCODED_PUBLICATION_ID)));
    }

    @Test
    void shouldNotEvaluatePublicationPublishedBeforeLatestClosedPeriod() throws IOException {
        mockCristinResponseAndCustomerApiResponseForNviInstitution(okResponse);
        var path = "evaluator/candidate_publicationDate_replace_year.json";
        var previousYear = LocalDateTime.now().getYear() - 1;
        var yearBeforePreviousYear = previousYear - 1;
        persistPeriod(yearBeforePreviousYear);
        persistPeriod(previousYear);
        var content = IoUtils.stringFromResources(Path.of(path)).replace("__REPLACE_YEAR__",
                                                                         String.valueOf(yearBeforePreviousYear));
        var fileUri = s3Driver.insertFile(UnixPath.of(path), content);
        var event = createEvent(new PersistedResourceMessage(fileUri));
        handler.handleRequest(event, context);
        assertEquals(0, queueClient.getSentMessages().size());
    }

    @Test
    void shouldCreateNonCandidateEventWhenPublicationIsNotPublished() throws IOException {
        var path = "evaluator/nonCandidate_notPublished.json";
        var content = IoUtils.inputStreamFromResources(path);
        var fileUri = s3Driver.insertFile(UnixPath.of(path), content);
        var event = createEvent(new PersistedResourceMessage(fileUri));
        handler.handleRequest(event, context);
        var nonCandidate = (NonNviCandidate) getMessageBody().candidate();
        assertThat(nonCandidate.publicationId(), is(equalTo(HARDCODED_PUBLICATION_ID)));
    }

    @Test
    void shouldCreateNonCandidateForMusicalArts() throws IOException {
        var path = "evaluator/nonCandidate_musicalArts.json";
        var content = IoUtils.inputStreamFromResources(path);
        var fileUri = s3Driver.insertFile(UnixPath.of(path), content);
        var event = createEvent(new PersistedResourceMessage(fileUri));
        handler.handleRequest(event, context);
        var nonCandidate = (NonNviCandidate) getMessageBody().candidate();
        assertThat(nonCandidate.publicationId(), is(equalTo(HARDCODED_PUBLICATION_ID)));
    }

    @Test
    void shouldCreateNonCandidateIfSeriesInMonographHasNviLevelZero() throws IOException {
        var path = "evaluator/nonCandidate_notValidMonographArticle.json";
        var content = IoUtils.inputStreamFromResources(path);
        var fileUri = s3Driver.insertFile(UnixPath.of(path), content);
        var event = createEvent(new PersistedResourceMessage(fileUri));
        handler.handleRequest(event, context);
        var nonCandidate = (NonNviCandidate) getMessageBody().candidate();
        assertThat(nonCandidate.publicationId(), is(equalTo(HARDCODED_PUBLICATION_ID)));
    }

    @Test
    void shouldThrowExceptionIfFileDoesntExist() {
        var event = createEvent(new PersistedResourceMessage(UriWrapper.fromUri("s3://dummy").getUri()));
        assertThrows(RuntimeException.class, () -> handler.handleRequest(event, context));
    }

    @Test
    void shouldCreateNonCandidateEventWhenZeroNviInstitutions() throws IOException {
        mockCristinResponseAndCustomerApiResponseForNviInstitution(notFoundResponse);
        var fileUri = s3Driver.insertFile(UnixPath.of(ACADEMIC_ARTICLE_PATH), ACADEMIC_ARTICLE);
        var event = createEvent(new PersistedResourceMessage(fileUri));
        handler.handleRequest(event, context);
        var nonCandidate = (NonNviCandidate) getMessageBody().candidate();
        assertThat(nonCandidate.publicationId(), is(equalTo(HARDCODED_PUBLICATION_ID)));
    }

    @Test
    void shouldThrowExceptionWhenProblemsFetchingCristinOrganization() throws IOException {
        when(uriRetriever.fetchResponse(any(), any())).thenReturn(Optional.of(badResponse));
        var fileUri = s3Driver.insertFile(UnixPath.of(ACADEMIC_ARTICLE_PATH), ACADEMIC_ARTICLE);
        var event = createEvent(new PersistedResourceMessage(fileUri));
        var appender = LogUtils.getTestingAppenderForRootLogger();
        assertThrows(RuntimeException.class, () -> handler.handleRequest(event, context));
        assertThat(appender.getMessages(), containsString(ERROR_COULD_NOT_FETCH_CRISTIN_ORG));
    }

    @Test
    void shouldThrowExceptionWhenProblemsFetchingCustomer() throws IOException {
        mockCristinResponseAndCustomerApiResponseForNviInstitution(badResponse);
        var fileUri = s3Driver.insertFile(UnixPath.of(ACADEMIC_ARTICLE_PATH), ACADEMIC_ARTICLE);
        var event = createEvent(new PersistedResourceMessage(fileUri));
        var appender = LogUtils.getTestingAppenderForRootLogger();
        assertThrows(RuntimeException.class, () -> handler.handleRequest(event, context));
        assertThat(appender.getMessages(), containsString(COULD_NOT_FETCH_CUSTOMER_MESSAGE));
    }

    @Test
    void shouldCreateNewCandidateEventWhenAffiliationAreNviInstitutions() throws IOException {
        mockCristinResponseAndCustomerApiResponseForNviInstitution(okResponse);
        var fileUri = s3Driver.insertFile(UnixPath.of(ACADEMIC_ARTICLE_PATH), ACADEMIC_ARTICLE);
        var event = createEvent(new PersistedResourceMessage(fileUri));
        handler.handleRequest(event, context);
        var candidate = (NviCandidate) getMessageBody().candidate();
        assertThat(candidate.publicationBucketUri(), is(equalTo(fileUri)));
    }

    @Test
    @Deprecated
    void shouldHandleSeriesWithMultipleTypes() throws IOException {
        mockCristinResponseAndCustomerApiResponseForNviInstitution(okResponse);
        var path = "evaluator/candidate_academicMonograph_series_multiple_types.json";
        var content = IoUtils.inputStreamFromResources(path);
        var fileUri = s3Driver.insertFile(UnixPath.of(path), content);
        var event = createEvent(new PersistedResourceMessage(fileUri));
        handler.handleRequest(event, context);
        var messageBody = getMessageBody();
        var expectedPoints = BigDecimal.valueOf(5).setScale(SCALE, ROUNDING_MODE);
        var expectedEvaluatedMessage = getExpectedEvaluatedMessage(ACADEMIC_MONOGRAPH,
                                                                   expectedPoints,
                                                                   fileUri, SERIES,
                                                                   BigDecimal.valueOf(5), expectedPoints);
        assertEquals(expectedEvaluatedMessage, messageBody);
    }

    @Test
    @Deprecated
    void shouldHandleJournalWithMultipleTypes() throws IOException {
        mockCristinResponseAndCustomerApiResponseForNviInstitution(okResponse);
        var path = "evaluator/candidate_academicArticle_journal_multiple_types.json";
        var content = IoUtils.inputStreamFromResources(path);
        var fileUri = s3Driver.insertFile(UnixPath.of(path), content);
        var event = createEvent(new PersistedResourceMessage(fileUri));
        handler.handleRequest(event, context);
        var messageBody = getMessageBody();
        var expectedPoints = ONE.setScale(SCALE, ROUNDING_MODE);
        var expectedEvaluatedMessage = getExpectedEvaluatedMessage(InstanceType.ACADEMIC_ARTICLE,
                                                                   expectedPoints,
                                                                   fileUri, JOURNAL,
                                                                   BigDecimal.valueOf(1), expectedPoints);
        assertEquals(expectedEvaluatedMessage, messageBody);
    }

    private static void mockSecretManager() {
        try (var secretsManagerClient = new FakeSecretsManagerClient()) {
            var credentials = new BackendClientCredentials("id", "secret");
            secretsManagerClient.putPlainTextSecret("secret", credentials.toString());
        }
    }

    private static CandidateEvaluatedMessage getExpectedEvaluatedMessage(InstanceType instanceType,
                                                                         BigDecimal points, URI bucketUri,
                                                                         PublicationChannel publicationChannel,
                                                                         BigDecimal basePoints,
                                                                         BigDecimal totalPoints) {
        return CandidateEvaluatedMessage.builder()
                   .withCandidateType(createExpectedCandidate(instanceType,
                                                              Map.of(CRISTIN_NVI_ORG_TOP_LEVEL_ID,
                                                                     points.setScale(SCALE, RoundingMode.HALF_UP)),
                                                              publicationChannel, Level.LEVEL_ONE.getValue(),
                                                              basePoints, totalPoints, bucketUri))
                   .build();
    }

    private static NviCandidate createExpectedCandidate(InstanceType instanceType, Map<URI, BigDecimal> institutionPoints,
                                                        PublicationChannel channelType, String level,
                                                        BigDecimal basePoints, BigDecimal totalPoints,
                                                        URI publicationBucketUri) {
        var verifiedCreators = List.of(new NviCreator(HARDCODED_CREATOR_ID, List.of(CRISTIN_NVI_ORG_SUB_UNIT_ID)));
        return NviCandidate.builder()
                   .withPublicationId(HARDCODED_PUBLICATION_ID)
                   .withPublicationBucketUri(publicationBucketUri)
                   .withDate(HARDCODED_PUBLICATION_DATE)
                   .withInstanceType(instanceType)
                   .withChannelType(channelType.getValue())
                   .withLevel(level)
                   .withPublicationChannelId(HARDCODED_PUBLICATION_CHANNEL_ID)
                   .withIsInternationalCollaboration(false)
                   .withCollaborationFactor(BigDecimal.ONE.setScale(1, ROUNDING_MODE))
                   .withCreatorShareCount(countCreatorShares(verifiedCreators))
                   .withBasePoints(basePoints)
                   .withVerifiedCreators(verifiedCreators)
                   .withInstitutionPoints(institutionPoints.entrySet().stream()
                                              .map(entry -> new InstitutionPoints(entry.getKey(), entry.getValue(),
                                                                                  List.of(
                                                                                      new CreatorAffiliationPoints(
                                                                                          HARDCODED_CREATOR_ID,
                                                                                          CRISTIN_NVI_ORG_SUB_UNIT_ID,
                                                                                          entry.getValue()))))
                                              .toList())
                   .withTotalPoints(totalPoints)
                   .build();
    }

    private static int countCreatorShares(List<NviCreator> nviCreators) {
        return (int) nviCreators.stream()
                         .mapToLong(creator -> creator.nviAffiliations().size())
                         .sum();
    }

    private URI setUpPublicationWithInvalidYear(String year) throws IOException {
        var path = "evaluator/candidate_publicationDate_replace_year.json";
        var content = IoUtils.stringFromResources(Path.of(path)).replace("__REPLACE_YEAR__", year);
        return s3Driver.insertFile(UnixPath.of(path), content);
    }

    private void persistPeriod(int publishingYear) {
        periodRepository.save(DbNviPeriod.builder()
                                  .publishingYear(String.valueOf(publishingYear))
                                  .startDate(LocalDateTime.of(publishingYear, 4, 1, 0, 0, 0).toInstant(ZoneOffset.UTC))
                                  .reportingDate(
                                      LocalDateTime.of(publishingYear + 1, 3, 1, 0, 0, 0).toInstant(ZoneOffset.UTC))
                                  .build());
    }

    private void setupEvaluatorService(PeriodRepository periodRepository) {
        var calculator = new CreatorVerificationUtil(authorizedBackendUriRetriever, uriRetriever);
        var organizationRetriever = new OrganizationRetriever(uriRetriever);
        var pointCalculator = new PointService(organizationRetriever);
        evaluatorService = new EvaluatorService(storageReader, calculator, pointCalculator, candidateRepository,
                                                periodRepository);
    }

    private URI setUpCandidate(int year) throws IOException {
        var candidateInClosedPeriod = Candidate.upsert(
            no.sikt.nva.nvi.test.TestUtils.createUpsertCandidateRequest(year), candidateRepository,
            periodRepository).orElseThrow();
        var content = IoUtils.stringFromResources(Path.of(ACADEMIC_ARTICLE_PATH))
                          .replace("__REPLACE_WITH_PUBLICATION_ID__",
                                   candidateInClosedPeriod.getPublicationId().toString());
        return s3Driver.insertFile(UnixPath.of(ACADEMIC_ARTICLE_PATH), content);
    }

    private void setupHttpResponses() {
        notFoundResponse = createResponse(404, StringUtils.EMPTY_STRING);
        badResponse = createResponse(500, StringUtils.EMPTY_STRING);
        okResponse = createResponse(200, CUSTOMER_API_NVI_RESPONSE);
    }

    private CandidateEvaluatedMessage getMessageBody() {
        var sentMessages = queueClient.getSentMessages();
        assertThat(sentMessages, hasSize(1));
        var message = sentMessages.get(0);
        return attempt(
            () -> objectMapper.readValue(message.messageBody(), CandidateEvaluatedMessage.class)).orElseThrow();
    }

    private void mockCristinResponseAndCustomerApiResponseForNonNviInstitution() {
        var cristinOrgNonNviSubUnit = URI.create("https://api.dev.nva.aws.unit.no/cristin/organization/150.50.50.0");
        var cristinOrgNonNviTopLevel = URI.create("https://api.dev.nva.aws.unit.no/cristin/organization/150.0.0.0");
        var customerApiEndpoint = URI.create("https://api.dev.nva.aws.unit.no/customer/cristinId");
        var cristinOrgNonNviTopLevelCustomerApiUri =
            URI.create(customerApiEndpoint + "/" + URLEncoder.encode(cristinOrgNonNviTopLevel.toString(),
                                                                     StandardCharsets.UTF_8));
        when(
            authorizedBackendUriRetriever.fetchResponse(eq(cristinOrgNonNviTopLevelCustomerApiUri),
                                                        any())).thenReturn(
            (Optional.of(notFoundResponse)));
        mockOrganizationResponseForAffiliation(cristinOrgNonNviTopLevel, cristinOrgNonNviSubUnit, uriRetriever);
    }

    private void mockCristinResponseAndCustomerApiResponseForNviInstitution(HttpResponse<String> httpResponse) {
        mockOrganizationResponseForAffiliation(CRISTIN_NVI_ORG_TOP_LEVEL_ID, CRISTIN_NVI_ORG_SUB_UNIT_ID,
                                               uriRetriever);
        when(
            authorizedBackendUriRetriever.fetchResponse(eq(CUSTOMER_API_CRISTIN_NVI_ORG_TOP_LEVEL),
                                                        any())).thenReturn(
            (Optional.of(httpResponse)));
    }
}
