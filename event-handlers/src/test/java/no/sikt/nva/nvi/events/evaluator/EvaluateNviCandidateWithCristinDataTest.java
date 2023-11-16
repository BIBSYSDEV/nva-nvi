package no.sikt.nva.nvi.events.evaluator;

import static no.sikt.nva.nvi.events.evaluator.TestUtils.createResponse;
import static no.sikt.nva.nvi.events.evaluator.TestUtils.createS3Event;
import static no.sikt.nva.nvi.events.evaluator.TestUtils.mockOrganizationResponseForAffiliation;
import static no.unit.nva.testutils.RandomDataGenerator.objectMapper;
import static nva.commons.core.attempt.Try.attempt;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.is;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import com.amazonaws.services.lambda.runtime.Context;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.net.URI;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.Optional;
import no.sikt.nva.nvi.common.queue.NviQueueClient;
import no.sikt.nva.nvi.events.evaluator.calculator.CandidateCalculator;
import no.sikt.nva.nvi.events.evaluator.calculator.OrganizationRetriever;
import no.sikt.nva.nvi.events.evaluator.calculator.PointCalculator;
import no.sikt.nva.nvi.events.evaluator.model.CandidateEvaluatedMessage;
import no.sikt.nva.nvi.events.model.NviCandidate;
import no.unit.nva.auth.uriretriever.AuthorizedBackendUriRetriever;
import no.unit.nva.auth.uriretriever.BackendClientCredentials;
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
    private FakeSqsClient sqsClient;
    private ByteArrayOutputStream output;
    private AuthorizedBackendUriRetriever uriRetriever;

    @BeforeEach
    void setUp() {
        var env = mock(Environment.class);
        when(env.readEnv("CANDIDATE_QUEUE_URL")).thenReturn("My test candidate queue url");
        when(env.readEnv("CANDIDATE_DLQ_URL")).thenReturn("My test candidate dlq url");
        var s3Client = new FakeS3Client();
        s3Driver = new S3Driver(s3Client, BUCKET_NAME);
        sqsClient = new FakeSqsClient();
        var queueClient = new NviQueueClient(sqsClient);
        var secretsManagerClient = new FakeSecretsManagerClient();
        var credentials = new BackendClientCredentials("id", "secret");
        secretsManagerClient.putPlainTextSecret("secret", credentials.toString());
        uriRetriever = mock(AuthorizedBackendUriRetriever.class);
        var calculator = new CandidateCalculator(uriRetriever);
        var storageReader = new FakeStorageReader(s3Client);
        var organizationRetriever = new OrganizationRetriever(uriRetriever);
        var pointCalculator = new PointCalculator(organizationRetriever);
        var evaluatorService = new EvaluatorService(storageReader, calculator, pointCalculator);
        handler = new EvaluateNviCandidateHandler(evaluatorService, queueClient, env);
        output = new ByteArrayOutputStream();
    }

    @Test
    void shouldReturnSamePointsAsPointsCalculatedByCristinForAcademicArticleFrom2022() throws IOException {
        mockCristinApiResponsesForAllSubUnitsInAcademicArticle();
        mockCustomerApi();
        handler.handleRequest(setUpS3Event("cristin_candidate_2022_academicArticle.json"), output, context);
        var body = getCandidateEvaluatedMessage();
        var institutionPoints = ((NviCandidate) body.candidate()).institutionPoints();
        assertThat(institutionPoints.get(NTNU_TOP_LEVEL_ORG_ID), is(equalTo(scaledBigDecimal(0.8165))));
        assertThat(institutionPoints.get(ST_OLAVS_TOP_LEVEL_ORG_ID), is(equalTo(scaledBigDecimal(0.5774))));
    }

    @Test
    void shouldReturnSamePointsAsPointsCalculatedByCristinForAcademicMonographFrom2022() throws IOException {
        mockOrganizationResponseForAffiliation(UIO_TOP_LEVEL_ORG_ID, URI.create("https://api.sandbox.nva.aws.unit"
                                                                                + ".no/cristin/organization/185.15.13"
                                                                                + ".55"), uriRetriever);
        mockCristinResponseForNonNviOrganizationsForAcademicMonograph();
        mockCustomerApi(UIO_TOP_LEVEL_ORG_ID);

        handler.handleRequest(setUpS3Event("cristin_candidate_2022_academicMonograph.json"), output, context);
        var body = getCandidateEvaluatedMessage();
        var institutionPoints = ((NviCandidate) body.candidate()).institutionPoints();
        assertThat(institutionPoints.get(UIO_TOP_LEVEL_ORG_ID), is(equalTo(scaledBigDecimal(3.7528))));
    }

    @Test
    void shouldReturnSamePointsAsPointsCalculatedByCristinForLiteratureReviewFrom2022() throws IOException {
        mockOrganizationResponseForAffiliation(NTNU_TOP_LEVEL_ORG_ID, URI.create("https://api.sandbox.nva.aws.unit"
                                                                                 + ".no/cristin/organization/194.65.15"
                                                                                 + ".0"), uriRetriever);
        mockCristinResponseForNonNviOrganizationsForLiteratureReview();
        mockCustomerApi(NTNU_TOP_LEVEL_ORG_ID);

        handler.handleRequest(setUpS3Event("cristin_candidate_2022_academicLiteratureReview.json"), output, context);
        var body = getCandidateEvaluatedMessage();
        var institutionPoints = ((NviCandidate) body.candidate()).institutionPoints();
        assertThat(institutionPoints.get(NTNU_TOP_LEVEL_ORG_ID), is(equalTo(scaledBigDecimal(1.5922))));
    }

    @Test
    void shouldReturnSamePointsAsPointsCalculatedByCristinForAcademicChapterFrom2022() throws IOException {
        mockCristinApiResponsesForAllSubUnitsInAcademicChapter();
        mockCustomerApi(NTNU_TOP_LEVEL_ORG_ID);
        mockCustomerApi(SINTEF_TOP_LEVEL_ORG_ID);

        handler.handleRequest(setUpS3Event("cristin_candidate_2022_academicChapter.json"), output, context);
        var body = getCandidateEvaluatedMessage();
        var institutionPoints = ((NviCandidate) body.candidate()).institutionPoints();
        assertThat(institutionPoints.get(NTNU_TOP_LEVEL_ORG_ID), is(equalTo(scaledBigDecimal(0.8660))));
        assertThat(institutionPoints.get(SINTEF_TOP_LEVEL_ORG_ID), is(equalTo(scaledBigDecimal(0.5000))));
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

    private CandidateEvaluatedMessage getCandidateEvaluatedMessage() {
        var message = sqsClient.getSentMessages().get(0);
        return attempt(
            () -> objectMapper.readValue(message.messageBody(), CandidateEvaluatedMessage.class)).orElseThrow();
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

    private InputStream setUpS3Event(String path) throws IOException {
        var content = IoUtils.inputStreamFromResources(path);
        var fileUri = s3Driver.insertFile(UnixPath.of(path), content);
        return createS3Event(fileUri);
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
                                                                                     + ".13.0.0"), uriRetriever);
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
        when(uriRetriever.fetchResponse(eq(createCustomerApiUri(topLevelOrgId.toString())), any())).thenReturn(
            Optional.of(customerApiResponse));
    }
}