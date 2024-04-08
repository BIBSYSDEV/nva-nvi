package no.sikt.nva.nvi.events.evaluator;

import static java.math.BigDecimal.ONE;
import static no.sikt.nva.nvi.events.evaluator.TestUtils.createEvent;
import static no.sikt.nva.nvi.events.evaluator.TestUtils.createResponse;
import static no.sikt.nva.nvi.events.evaluator.TestUtils.mockOrganizationResponseForAffiliation;
import static no.sikt.nva.nvi.events.evaluator.model.InstanceType.ACADEMIC_ARTICLE;
import static no.sikt.nva.nvi.events.evaluator.model.InstanceType.ACADEMIC_LITERATURE_REVIEW;
import static no.sikt.nva.nvi.events.evaluator.model.InstanceType.ACADEMIC_MONOGRAPH;
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
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Stream;
import no.sikt.nva.nvi.common.S3StorageReader;
import no.sikt.nva.nvi.common.client.OrganizationRetriever;
import no.sikt.nva.nvi.events.evaluator.calculator.CandidateCalculator;
import no.sikt.nva.nvi.events.evaluator.model.InstanceType;
import no.sikt.nva.nvi.events.evaluator.model.Level;
import no.sikt.nva.nvi.events.evaluator.model.PublicationChannel;
import no.sikt.nva.nvi.events.model.CandidateEvaluatedMessage;
import no.sikt.nva.nvi.events.model.NonNviCandidate;
import no.sikt.nva.nvi.events.model.NviCandidate;
import no.sikt.nva.nvi.events.model.NviCandidate.NviCreatorWithAffiliationPoints;
import no.sikt.nva.nvi.events.model.NviCandidate.PublicationDate;
import no.sikt.nva.nvi.events.model.PersistedResourceMessage;
import no.sikt.nva.nvi.test.FakeSqsClient;
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
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

class EvaluateNviCandidateHandlerTest {

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
    private static final String BUCKET_NAME = "ignoredBucket";
    private static final String CUSTOMER_API_NVI_RESPONSE = "{" + "\"nviInstitution\" : \"true\"" + "}";
    private static final String ACADEMIC_ARTICLE_PATH = "evaluator/candidate_academicArticle.json";
    private static final URI HARDCODED_PUBLICATION_ID = URI.create(
        "https://api.dev.nva.aws.unit.no/publication/01888b283f29-cae193c7-80fa-4f92-a164-c73b02c19f2d");
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

    public static Stream<Arguments> levelValues() {
        return Stream.of(Arguments.of("1", "LevelOne"), Arguments.of("2", "LevelTwo"));
    }

    @BeforeEach
    void setUp() {
        var env = mock(Environment.class);
        when(env.readEnv("CANDIDATE_QUEUE_URL")).thenReturn("My test candidate queue url");
        when(env.readEnv("CANDIDATE_DLQ_URL")).thenReturn("My test candidate dlq url");
        notFoundResponse = createResponse(404, StringUtils.EMPTY_STRING);
        badResponse = createResponse(500, StringUtils.EMPTY_STRING);
        okResponse = createResponse(200, CUSTOMER_API_NVI_RESPONSE);
        var s3Client = new FakeS3Client();
        s3Driver = new S3Driver(s3Client, BUCKET_NAME);
        var secretsManagerClient = new FakeSecretsManagerClient();
        var credentials = new BackendClientCredentials("id", "secret");
        secretsManagerClient.putPlainTextSecret("secret", credentials.toString());
        authorizedBackendUriRetriever = mock(AuthorizedBackendUriRetriever.class);
        uriRetriever = mock(UriRetriever.class);
        var calculator = new CandidateCalculator(authorizedBackendUriRetriever, uriRetriever);
        var storageReader = new S3StorageReader(s3Client, BUCKET_NAME);
        var organizationRetriever = new OrganizationRetriever(uriRetriever);
        var pointCalculator = new PointService(organizationRetriever);
        var evaluatorService = new EvaluatorService(storageReader, calculator, pointCalculator);
        queueClient = new FakeSqsClient();
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
        var institutionPoints = ((NviCandidate) messageBody.candidate()).institutionPoints();
        assertEquals(1, institutionPoints.size());
        assertTrue(institutionPoints.containsKey(CRISTIN_NVI_ORG_TOP_LEVEL_ID));
    }

    @Test
    void shouldCreateNewCandidateEventOnValidAcademicArticle() throws IOException {
        mockCristinResponseAndCustomerApiResponseForNviInstitution(okResponse);
        var content = IoUtils.inputStreamFromResources(ACADEMIC_ARTICLE_PATH);
        var fileUri = s3Driver.insertFile(UnixPath.of(ACADEMIC_ARTICLE_PATH), content);
        var event = createEvent(new PersistedResourceMessage(fileUri));
        handler.handleRequest(event, context);
        var messageBody = getMessageBody();
        var expectedPoints = BigDecimal.valueOf(1).setScale(SCALE, ROUNDING_MODE);
        var expectedEvaluatedMessage = getExpectedEvaluatedMessage(ACADEMIC_ARTICLE.getValue(),
                                                                   expectedPoints,
                                                                   fileUri, JOURNAL,
                                                                   ONE, expectedPoints, Level.LEVEL_ONE_V2);
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
        var expectedEvaluatedMessage = getExpectedEvaluatedMessage(InstanceType.ACADEMIC_CHAPTER.getValue(),
                                                                   expectedPoints,
                                                                   fileUri, SERIES,
                                                                   ONE, expectedPoints, Level.LEVEL_ONE_V2);
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
        var expectedEvaluatedMessage = getExpectedEvaluatedMessage(ACADEMIC_MONOGRAPH.getValue(),
                                                                   expectedPoints,
                                                                   fileUri, SERIES,
                                                                   BigDecimal.valueOf(5), expectedPoints,
                                                                   Level.LEVEL_ONE_V2);
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
        var expectedEvaluatedMessage = getExpectedEvaluatedMessage(ACADEMIC_LITERATURE_REVIEW.getValue(),
                                                                   expectedPoints,
                                                                   fileUri, JOURNAL,
                                                                   ONE, expectedPoints, Level.LEVEL_ONE_V2);
        assertEquals(expectedEvaluatedMessage, messageBody);
    }

    @Test
    void shouldCalculatePointsOnValidAcademicArticle() throws IOException {
        mockCristinResponseAndCustomerApiResponseForNviInstitution(okResponse);
        var content = IoUtils.inputStreamFromResources(ACADEMIC_ARTICLE_PATH);
        var fileUri = s3Driver.insertFile(UnixPath.of(ACADEMIC_ARTICLE_PATH), content);
        var event = createEvent(new PersistedResourceMessage(fileUri));
        handler.handleRequest(event, context);
        var messageBody = getMessageBody();
        var institutionPoints = ((NviCandidate) messageBody.candidate()).institutionPoints();
        assertThat(institutionPoints, notNullValue());
        assertThat(institutionPoints.get(CRISTIN_NVI_ORG_TOP_LEVEL_ID),
                   is(equalTo(BigDecimal.valueOf(1).setScale(4, RoundingMode.HALF_UP))));
    }

    @Test
    void shouldCreateInstitutionApprovalsForTopLevelInstitutions() throws IOException {
        mockCristinResponseAndCustomerApiResponseForNviInstitution(okResponse);
        var content = IoUtils.inputStreamFromResources(ACADEMIC_ARTICLE_PATH);
        var fileUri = s3Driver.insertFile(UnixPath.of(ACADEMIC_ARTICLE_PATH), content);
        var event = createEvent(new PersistedResourceMessage(fileUri));
        handler.handleRequest(event, context);
        var messageBody = getMessageBody();
        var institutionPoints = ((NviCandidate) messageBody.candidate()).institutionPoints();
        assertThat(institutionPoints, notNullValue());
        assertThat(institutionPoints.get(CRISTIN_NVI_ORG_TOP_LEVEL_ID), notNullValue());
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

    @ParameterizedTest
    @MethodSource("levelValues")
    void shouldCreateCandidateWhenLevelValueHasVersionTwoValues(String versionOneValue, String versionTwoValue)
        throws IOException {
        mockCristinResponseAndCustomerApiResponseForNviInstitution(okResponse);
        var candidateWithNewLevel = IoUtils.stringFromResources(Path.of(ACADEMIC_ARTICLE_PATH))
                                        .replace("\"level\": " + "\"" + versionOneValue + "\"",
                                                 "\"scientificValue\": " + "\"" + versionTwoValue + "\"");
        var fileUri = s3Driver.insertFile(UnixPath.of(ACADEMIC_ARTICLE_PATH),
                                          IoUtils.stringToStream(candidateWithNewLevel));

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
    void shouldCreateNonCandidateWhenPublicationYearBefore2022() throws IOException {
        var path = "evaluator/candidate_publicationDate_before_2022.json";
        var content = IoUtils.inputStreamFromResources(path);
        var fileUri = s3Driver.insertFile(UnixPath.of(path), content);
        var event = createEvent(new PersistedResourceMessage(fileUri));
        handler.handleRequest(event, context);
        var nonCandidate = (NonNviCandidate) getMessageBody().candidate();
        assertThat(nonCandidate.publicationId(), is(equalTo(HARDCODED_PUBLICATION_ID)));
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
        var fileUri = s3Driver.insertFile(UnixPath.of(ACADEMIC_ARTICLE_PATH),
                                          IoUtils.inputStreamFromResources(ACADEMIC_ARTICLE_PATH));
        var event = createEvent(new PersistedResourceMessage(fileUri));
        handler.handleRequest(event, context);
        var nonCandidate = (NonNviCandidate) getMessageBody().candidate();
        assertThat(nonCandidate.publicationId(), is(equalTo(HARDCODED_PUBLICATION_ID)));
    }

    @Test
    void shouldThrowExceptionWhenProblemsFetchingCristinOrganization() throws IOException {
        when(uriRetriever.fetchResponse(any(), any())).thenReturn(Optional.of(badResponse));
        var fileUri = s3Driver.insertFile(UnixPath.of(ACADEMIC_ARTICLE_PATH),
                                          IoUtils.inputStreamFromResources(ACADEMIC_ARTICLE_PATH));
        var event = createEvent(new PersistedResourceMessage(fileUri));
        var appender = LogUtils.getTestingAppenderForRootLogger();
        assertThrows(RuntimeException.class, () -> handler.handleRequest(event, context));
        assertThat(appender.getMessages(), containsString(ERROR_COULD_NOT_FETCH_CRISTIN_ORG));
    }

    @Test
    void shouldThrowExceptionWhenProblemsFetchingCustomer() throws IOException {
        mockCristinResponseAndCustomerApiResponseForNviInstitution(badResponse);
        var fileUri = s3Driver.insertFile(UnixPath.of(ACADEMIC_ARTICLE_PATH),
                                          IoUtils.inputStreamFromResources(ACADEMIC_ARTICLE_PATH));
        var event = createEvent(new PersistedResourceMessage(fileUri));
        var appender = LogUtils.getTestingAppenderForRootLogger();
        assertThrows(RuntimeException.class, () -> handler.handleRequest(event, context));
        assertThat(appender.getMessages(), containsString(COULD_NOT_FETCH_CUSTOMER_MESSAGE));
    }

    @Test
    void shouldCreateNewCandidateEventWhenAffiliationAreNviInstitutions() throws IOException {
        mockCristinResponseAndCustomerApiResponseForNviInstitution(okResponse);
        var fileUri = s3Driver.insertFile(UnixPath.of(ACADEMIC_ARTICLE_PATH),
                                          IoUtils.inputStreamFromResources(ACADEMIC_ARTICLE_PATH));
        var event = createEvent(new PersistedResourceMessage(fileUri));
        handler.handleRequest(event, context);
        var candidate = (NviCandidate) getMessageBody().candidate();
        assertThat(candidate.publicationBucketUri(), is(equalTo(fileUri)));
    }

    @Test
    @Deprecated
    void shouldCreateNewCandidateEventOnValidDeprecatedAcademicArticle() throws IOException {
        mockCristinResponseAndCustomerApiResponseForNviInstitution(okResponse);
        var path = "evaluator/candidate_academicArticle_deprecated.json";
        var content = IoUtils.inputStreamFromResources(path);
        var fileUri = s3Driver.insertFile(UnixPath.of(path), content);
        var event = createEvent(new PersistedResourceMessage(fileUri));
        handler.handleRequest(event, context);
        var messageBody = getMessageBody();
        var expectedPoints = BigDecimal.valueOf(1).setScale(SCALE, ROUNDING_MODE);
        var expectedEvaluatedMessage = getExpectedEvaluatedMessage(ACADEMIC_ARTICLE.getValue(),
                                                                   expectedPoints,
                                                                   fileUri, JOURNAL,
                                                                   ONE, expectedPoints, Level.LEVEL_ONE);
        assertEquals(expectedEvaluatedMessage, messageBody);
    }

    @Test
    @Deprecated
    void shouldCreateNewCandidateEventWithCorrectDataOnValidDeprecatedAcademicChapter() throws IOException {
        mockCristinResponseAndCustomerApiResponseForNviInstitution(okResponse);
        var path = "evaluator/candidate_academicChapter_deprecated.json";
        var content = IoUtils.inputStreamFromResources(path);
        var fileUri = s3Driver.insertFile(UnixPath.of(path), content);
        var event = createEvent(new PersistedResourceMessage(fileUri));
        handler.handleRequest(event, context);
        var messageBody = getMessageBody();
        var expectedPoints = BigDecimal.valueOf(1).setScale(SCALE, ROUNDING_MODE);
        var expectedEvaluatedMessage = getExpectedEvaluatedMessage(InstanceType.ACADEMIC_CHAPTER.getValue(),
                                                                   expectedPoints,
                                                                   fileUri, SERIES,
                                                                   ONE, expectedPoints, Level.LEVEL_ONE);
        assertEquals(expectedEvaluatedMessage, messageBody);
    }

    @Test
    @Deprecated
    void shouldCreateNewCandidateEventWithCorrectDataOnValidDeprecatedAcademicMonograph() throws IOException {
        mockCristinResponseAndCustomerApiResponseForNviInstitution(okResponse);
        var path = "evaluator/candidate_academicMonograph_deprecated.json";
        var content = IoUtils.inputStreamFromResources(path);
        var fileUri = s3Driver.insertFile(UnixPath.of(path), content);
        var event = createEvent(new PersistedResourceMessage(fileUri));
        handler.handleRequest(event, context);
        var messageBody = getMessageBody();
        var expectedPoints = BigDecimal.valueOf(5).setScale(SCALE, ROUNDING_MODE);
        var expectedEvaluatedMessage = getExpectedEvaluatedMessage(ACADEMIC_MONOGRAPH.getValue(),
                                                                   expectedPoints,
                                                                   fileUri, SERIES,
                                                                   BigDecimal.valueOf(5), expectedPoints,
                                                                   Level.LEVEL_ONE);
        assertEquals(expectedEvaluatedMessage, messageBody);
    }

    @Test
    @Deprecated
    void shouldCreateNewCandidateEventWithCorrectDataOnValidDeprecatedAcademicLiteratureReview() throws IOException {
        mockCristinResponseAndCustomerApiResponseForNviInstitution(okResponse);
        var path = "evaluator/candidate_academicLiteratureReview_deprecated.json";
        var content = IoUtils.inputStreamFromResources(path);
        var fileUri = s3Driver.insertFile(UnixPath.of(path), content);
        var event = createEvent(new PersistedResourceMessage(fileUri));
        handler.handleRequest(event, context);
        var messageBody = getMessageBody();
        var expectedPoints = BigDecimal.valueOf(1).setScale(SCALE, ROUNDING_MODE);
        var expectedEvaluatedMessage = getExpectedEvaluatedMessage(ACADEMIC_LITERATURE_REVIEW.getValue(),
                                                                   expectedPoints,
                                                                   fileUri, JOURNAL,
                                                                   ONE, expectedPoints, Level.LEVEL_ONE);
        assertEquals(expectedEvaluatedMessage, messageBody);
    }

    @Test
    void shouldCreateDlqWhenUnableToConnectToResources() {

    }

    @Test
    void shouldCreateDlqWhenUnableToCreateNewCandidateEvent() {

    }

    @Test
    void shouldReadDlqAndRetryAfterGivenTime() {

    }

    private static CandidateEvaluatedMessage getExpectedEvaluatedMessage(String instanceType,
                                                                         BigDecimal points, URI bucketUri,
                                                                         PublicationChannel publicationChannel,
                                                                         BigDecimal basePoints,
                                                                         BigDecimal totalPoints, Level level) {
        return CandidateEvaluatedMessage.builder()
                   .withCandidateType(createExpectedCandidate(instanceType,
                                                              Map.of(CRISTIN_NVI_ORG_TOP_LEVEL_ID,
                                                                     points.setScale(SCALE, RoundingMode.HALF_UP)),
                                                              publicationChannel, level.getValue(),
                                                              basePoints, totalPoints, bucketUri))
                   .build();
    }

    private static NviCandidate createExpectedCandidate(String instanceType, Map<URI, BigDecimal> institutionPoints,
                                                        PublicationChannel channelType, String level,
                                                        BigDecimal basePoints, BigDecimal totalPoints,
                                                        URI publicationBucketUri) {
        var verifiedCreators = List.of(
            new NviCreatorWithAffiliationPoints(HARDCODED_CREATOR_ID, List.of(
                new NviCreatorWithAffiliationPoints.AffiliationPoints(CRISTIN_NVI_ORG_SUB_UNIT_ID,
                                                                      institutionPoints.get(
                                                                          CRISTIN_NVI_ORG_TOP_LEVEL_ID)))));
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
                   .withInstitutionPoints(institutionPoints)
                   .withTotalPoints(totalPoints)
                   .build();
    }

    private static int countCreatorShares(List<NviCreatorWithAffiliationPoints> nviCreators) {
        return (int) nviCreators.stream()
                         .mapToLong(creator -> creator.affiliationPoints().size())
                         .sum();
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
