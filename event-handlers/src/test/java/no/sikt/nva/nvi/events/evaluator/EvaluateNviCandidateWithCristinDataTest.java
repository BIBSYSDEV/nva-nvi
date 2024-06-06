package no.sikt.nva.nvi.events.evaluator;

import static no.sikt.nva.nvi.events.evaluator.TestUtils.createEvent;
import static no.sikt.nva.nvi.events.evaluator.TestUtils.createResponse;
import static no.sikt.nva.nvi.events.evaluator.TestUtils.mockOrganizationResponseForAffiliation;
import static no.unit.nva.testutils.RandomDataGenerator.objectMapper;
import static nva.commons.core.attempt.Try.attempt;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.is;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import com.amazonaws.services.lambda.runtime.Context;
import com.amazonaws.services.lambda.runtime.events.SQSEvent;
import java.io.IOException;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.net.URI;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.Optional;
import no.sikt.nva.nvi.common.S3StorageReader;
import no.sikt.nva.nvi.common.client.OrganizationRetriever;
import no.sikt.nva.nvi.common.db.CandidateRepository;
import no.sikt.nva.nvi.common.db.PeriodRepository;
import no.sikt.nva.nvi.events.evaluator.calculator.CreatorVerificationUtil;
import no.sikt.nva.nvi.events.model.CandidateEvaluatedMessage;
import no.sikt.nva.nvi.events.model.NviCandidate;
import no.sikt.nva.nvi.events.model.PersistedResourceMessage;
import no.sikt.nva.nvi.test.FakeSqsClient;
import no.unit.nva.auth.uriretriever.AuthorizedBackendUriRetriever;
import no.unit.nva.auth.uriretriever.BackendClientCredentials;
import no.unit.nva.auth.uriretriever.UriRetriever;
import no.unit.nva.s3.S3Driver;
import no.unit.nva.stubs.FakeS3Client;
import no.unit.nva.stubs.FakeSecretsManagerClient;
import nva.commons.core.Environment;
import nva.commons.core.ioutils.IoUtils;
import nva.commons.core.paths.UnixPath;
import nva.commons.core.paths.UriWrapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

public class EvaluateNviCandidateWithCristinDataTest {

    private static final Environment ENVIRONMENT = new Environment();
    private static final RoundingMode ROUNDING_MODE = RoundingMode.HALF_UP;
    private static final int SCALE = 4;
    private static final URI NTNU_TOP_LEVEL_ORG_ID = URI.create(
        ("https://api.sandbox.nva.aws.unit.no/cristin/organization/194.0.0.0"));
    private static final URI ST_OLAVS_TOP_LEVEL_ORG_ID = URI.create(
        "https://api.sandbox.nva.aws.unit.no/cristin/organization/1920.0.0.0");
    private static final URI UIO_TOP_LEVEL_ORG_ID = URI.create(
        "https://api.sandbox.nva.aws.unit.no/cristin/organization/185.90.0.0");
    private static final URI SINTEF_TOP_LEVEL_ORG_ID = URI.create(
        "https://api.sandbox.nva.aws.unit.no/cristin/organization/7401.0.0.0");
    private static final String BUCKET_NAME = ENVIRONMENT.readEnv("EXPANDED_RESOURCES_BUCKET");
    private static final String API_HOST = ENVIRONMENT.readEnv("API_HOST");
    private static final String CUSTOMER_API_NVI_RESPONSE = "{" + "\"nviInstitution\" : \"true\"" + "}";
    private static final String CUSTOMER = "customer";
    private static final String CRISTIN_ID = "cristinId";
    private final Context context = mock(Context.class);
    private S3Driver s3Driver;
    private EvaluateNviCandidateHandler handler;
    private AuthorizedBackendUriRetriever authorizedBackendUriRetriever;
    private UriRetriever uriRetriever;
    private FakeSqsClient queueClient;

    @BeforeEach
    void setUp() {
        var env = mock(Environment.class);
        when(env.readEnv("CANDIDATE_QUEUE_URL")).thenReturn("My test candidate queue url");
        when(env.readEnv("CANDIDATE_DLQ_URL")).thenReturn("My test candidate dlq url");
        var s3Client = new FakeS3Client();
        s3Driver = new S3Driver(s3Client, BUCKET_NAME);
        var secretsManagerClient = new FakeSecretsManagerClient();
        var credentials = new BackendClientCredentials("id", "secret");
        secretsManagerClient.putPlainTextSecret("secret", credentials.toString());
        authorizedBackendUriRetriever = mock(AuthorizedBackendUriRetriever.class);
        uriRetriever = mock(UriRetriever.class);
        var calculator = new CreatorVerificationUtil(authorizedBackendUriRetriever, uriRetriever);
        var storageReader = new S3StorageReader(s3Client, BUCKET_NAME);
        var organizationRetriever = new OrganizationRetriever(uriRetriever);
        var pointCalculator = new PointService(organizationRetriever);
        var evaluatorService = new EvaluatorService(storageReader, calculator, pointCalculator, mock(CandidateRepository.class),
                                                    mock(PeriodRepository.class));
        queueClient = new FakeSqsClient();
        handler = new EvaluateNviCandidateHandler(evaluatorService, queueClient, env);
    }

    @Test
    void shouldReturnSamePointsAsPointsCalculatedByCristinForAcademicArticleFrom2022() throws IOException {
        mockCristinApiResponsesForAllSubUnitsInAcademicArticle();
        mockCustomerApi();
        var event = setUpSqsEvent("evaluator/cristin_candidate_2022_academicArticle.json");
        handler.handleRequest(event, context);
        var candidate = getMessageBody();
        assertThat(candidate.getPointsForInstitution(NTNU_TOP_LEVEL_ORG_ID),
                   is(equalTo(scaledBigDecimal(0.8165))));
        assertThat(candidate.getPointsForInstitution(ST_OLAVS_TOP_LEVEL_ORG_ID),
                   is(equalTo(scaledBigDecimal(0.5774))));
    }

    @Test
    void shouldReturnSamePointsAsPointsCalculatedByCristinForAcademicMonographFrom2022() throws IOException {
        mockOrganizationResponseForAffiliation(UIO_TOP_LEVEL_ORG_ID, URI.create("https://api.sandbox.nva.aws.unit"
                                                                                + ".no/cristin/organization/185.15.13"
                                                                                + ".55"), uriRetriever);
        mockCristinResponseForNonNviOrganizationsForAcademicMonograph();
        mockCustomerApi(UIO_TOP_LEVEL_ORG_ID);

        var event = setUpSqsEvent("evaluator/cristin_candidate_2022_academicMonograph.json");
        handler.handleRequest(event, context);
        var candidate = getMessageBody();
        assertThat(candidate.getPointsForInstitution(UIO_TOP_LEVEL_ORG_ID), is(equalTo(scaledBigDecimal(3.7528))));
    }

    @Test
    void shouldReturnSamePointsAsPointsCalculatedByCristinForLiteratureReviewFrom2022() throws IOException {
        mockOrganizationResponseForAffiliation(NTNU_TOP_LEVEL_ORG_ID, URI.create("https://api.sandbox.nva.aws.unit"
                                                                                 + ".no/cristin/organization/194.65.15"
                                                                                 + ".0"), uriRetriever);
        mockCristinResponseForNonNviOrganizationsForLiteratureReview();
        mockCustomerApi(NTNU_TOP_LEVEL_ORG_ID);

        var event = setUpSqsEvent("evaluator/cristin_candidate_2022_academicLiteratureReview.json");
        handler.handleRequest(event, context);
        var candidate = getMessageBody();
        assertThat(candidate.getPointsForInstitution(NTNU_TOP_LEVEL_ORG_ID), is(equalTo(scaledBigDecimal(1.5922))));
    }

    @Test
    void shouldReturnSamePointsAsPointsCalculatedByCristinForAcademicChapterFrom2022() throws IOException {
        mockCristinApiResponsesForAllSubUnitsInAcademicChapter();
        mockCustomerApi(NTNU_TOP_LEVEL_ORG_ID);
        mockCustomerApi(SINTEF_TOP_LEVEL_ORG_ID);

        var event = setUpSqsEvent("evaluator/cristin_candidate_2022_academicChapter.json");
        handler.handleRequest(event, context);
        var candidate = getMessageBody();
        assertThat(candidate.getPointsForInstitution(NTNU_TOP_LEVEL_ORG_ID), is(equalTo(scaledBigDecimal(0.8660))));
        assertThat(candidate.getPointsForInstitution(SINTEF_TOP_LEVEL_ORG_ID), is(equalTo(scaledBigDecimal(0.5000))));
    }

    private static BigDecimal scaledBigDecimal(double val) {
        return BigDecimal.valueOf(val).setScale(SCALE, ROUNDING_MODE);
    }

    private static URI createCustomerApiUri(String institutionId) {
        var getCustomerEndpoint = UriWrapper.fromHost(API_HOST).addChild(CUSTOMER).addChild(CRISTIN_ID).getUri();
        return URI.create(getCustomerEndpoint + "/" + URLEncoder.encode(institutionId, StandardCharsets.UTF_8));
    }

    private void mockCristinResponseForNonNviOrganizationsForLiteratureReview() {
        mockOrganizationResponseForAffiliation(URI.create("https://api.sandbox.nva.aws.unit"
                                                          + ".no/cristin/organization/13900000.0.0.0"), null,
                                               uriRetriever);
        mockOrganizationResponseForAffiliation(
            URI.create("https://api.sandbox.nva.aws.unit.no/cristin/organization/13920157.0.0.0"), null,
            uriRetriever);
    }

    private void mockCristinResponseForNonNviOrganizationsForAcademicMonograph() {
        mockOrganizationResponseForAffiliation(
            URI.create("https://api.sandbox.nva.aws.unit.no/cristin/organization/14100020.0.0.0"), null,
            uriRetriever);
        mockOrganizationResponseForAffiliation(
            URI.create("https://api.sandbox.nva.aws.unit.no/cristin/organization/12300050.0.0.0"), null,
            uriRetriever);
    }

    private void mockCristinApiResponsesForAllSubUnitsInAcademicChapter() {
        mockOrganizationResponseForAffiliation(NTNU_TOP_LEVEL_ORG_ID, URI.create("https://api.sandbox.nva.aws.unit"
                                                                                 + ".no/cristin/organization/194.64.94"
                                                                                 + ".0"), uriRetriever);
        mockOrganizationResponseForAffiliation(NTNU_TOP_LEVEL_ORG_ID, URI.create("https://api.sandbox.nva.aws.unit"
                                                                                 + ".no/cristin/organization/194.64.45"
                                                                                 + ".0"), uriRetriever);
        mockOrganizationResponseForAffiliation(SINTEF_TOP_LEVEL_ORG_ID, URI.create("https://api.sandbox.nva.aws.unit"
                                                                                   + ".no/cristin/organization/7401.30"
                                                                                   + ".40.0"), uriRetriever);
    }

    private SQSEvent setUpSqsEvent(String path) throws IOException {
        var content = IoUtils.inputStreamFromResources(path);
        var fileUri = s3Driver.insertFile(UnixPath.of(path), content);
        return createEvent(new PersistedResourceMessage(fileUri));
    }

    private void mockCristinApiResponsesForAllSubUnitsInAcademicArticle() {
        mockOrganizationResponseForAffiliation(NTNU_TOP_LEVEL_ORG_ID, URI.create(
                                                   "https://api.sandbox.nva.aws.unit.no/cristin/organization/194.65.0"
                                                   + ".0"),
                                               uriRetriever);
        mockOrganizationResponseForAffiliation(NTNU_TOP_LEVEL_ORG_ID, URI.create("https://api.sandbox.nva.aws.unit"
                                                                                 + ".no/cristin/organization/194.63.10"
                                                                                 + ".0"), uriRetriever);
        mockOrganizationResponseForAffiliation(ST_OLAVS_TOP_LEVEL_ORG_ID, URI.create("https://api.sandbox.nva.aws.unit"
                                                                                     + ".no/cristin/organization/1920"
                                                                                     + ".13.0.0"),
                                               uriRetriever);
        mockOrganizationResponseForAffiliation(NTNU_TOP_LEVEL_ORG_ID, URI.create("https://api.sandbox.nva.aws.unit"
                                                                                 + ".no/cristin/organization/194.65.25"
                                                                                 + ".0"), uriRetriever);
        mockOrganizationResponseForAffiliation(ST_OLAVS_TOP_LEVEL_ORG_ID, null, uriRetriever);
    }

    private void mockCustomerApi() {
        mockCustomerApi(NTNU_TOP_LEVEL_ORG_ID);
        mockCustomerApi(ST_OLAVS_TOP_LEVEL_ORG_ID);
    }

    private void mockCustomerApi(URI topLevelOrgId) {
        var customerApiResponse = createResponse(200, CUSTOMER_API_NVI_RESPONSE);
        when(
            authorizedBackendUriRetriever.fetchResponse(eq(createCustomerApiUri(topLevelOrgId.toString())),
                                                        any())).thenReturn(Optional.of(customerApiResponse));
    }

    private NviCandidate getMessageBody() {
        var sentMessages = queueClient.getSentMessages();
        assertThat(sentMessages, hasSize(1));
        var message = sentMessages.get(0);
        var candidateEvaluatedMessage = attempt(
            () -> objectMapper.readValue(message.messageBody(), CandidateEvaluatedMessage.class)).orElseThrow();
        return (NviCandidate) candidateEvaluatedMessage.candidate();
    }
}
