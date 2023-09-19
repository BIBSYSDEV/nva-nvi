package no.sikt.nva.nvi.evaluator;

import static no.sikt.nva.nvi.evaluator.TestUtils.createResponse;
import static no.sikt.nva.nvi.evaluator.TestUtils.createS3Event;
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
import java.net.URI;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.Optional;
import no.sikt.nva.nvi.evaluator.aws.SqsMessageClient;
import no.sikt.nva.nvi.evaluator.calculator.CandidateCalculator;
import no.sikt.nva.nvi.evaluator.model.CandidateEvaluatedMessage;
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

public class EvaluateNviCandidateCristinTest {

    public static final Environment ENVIRONMENT = new Environment();
    public static final String ORGANIZATION_RESPONSE = "generalCristinApiSubUnitOrganizationResponse.json";
    public static final String CRISTIN_ORG_RESPONSE = IoUtils.stringFromResources(Path.of(ORGANIZATION_RESPONSE));
    public static final String REPLACE_SUB_UNIT_STRING = "__REPLACE_SUB_UNIT_ID__";
    public static final String REPLACE_TOP_LEVEL_STRING = "__REPLACE_TOP_LEVEL_ORG_ID__";
    public static final URI NTNU_TOP_LEVEL_ORG_ID = URI.create(
        ("https://api.sandbox.nva.aws.unit.no/cristin/organization/194.0.0.0"));
    public static final URI ST_OLAVS_TOP_LEVEL_ORG_ID = URI.create(
        "https://api.sandbox.nva.aws.unit.no/cristin/organization/1920.0.0.0");
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
        var s3Client = new FakeS3Client();
        s3Driver = new S3Driver(s3Client, BUCKET_NAME);
        sqsClient = new FakeSqsClient();
        SqsMessageClient queueClient = new SqsMessageClient(sqsClient);
        var secretsManagerClient = new FakeSecretsManagerClient();
        var credentials = new BackendClientCredentials("id", "secret");
        secretsManagerClient.putPlainTextSecret("secret", credentials.toString());
        uriRetriever = mock(AuthorizedBackendUriRetriever.class);
        var calculator = new CandidateCalculator(uriRetriever);
        var storageReader = new FakeStorageReader(s3Client);
        var evaluatorService = new EvaluatorService(storageReader, calculator);
        handler = new EvaluateNviCandidateHandler(evaluatorService, queueClient);
        output = new ByteArrayOutputStream();
    }

    @Test
    void shouldCalculatePointsOnValidCristinImportAcademicArticleFrom2022() throws IOException {
        mockCristinApiResponsesForAllSubUnits();
        mockCustomerApi();

        handler.handleRequest(setUpS3Event("cristin_candidate_2022_academicArticle.json"), output, context);
        var message = sqsClient.getSentMessages().get(0);
        var body =
            attempt(() -> objectMapper.readValue(message.messageBody(), CandidateEvaluatedMessage.class))
                .orElseThrow();
        assertThat(body.institutionPoints().get(NTNU_TOP_LEVEL_ORG_ID), is(equalTo(BigDecimal.valueOf(0.8165))));
        assertThat(body.institutionPoints().get(ST_OLAVS_TOP_LEVEL_ORG_ID), is(equalTo(BigDecimal.valueOf(0.5774))));
    }

    private static URI createCustomerApiUri(String institutionId) {
        var getCustomerEndpoint = UriWrapper.fromHost(API_HOST).addChild(CUSTOMER).addChild(CRISTIN_ID).getUri();
        return URI.create(getCustomerEndpoint + "/" + URLEncoder.encode(institutionId, StandardCharsets.UTF_8));
    }

    private InputStream setUpS3Event(String path) throws IOException {
        var content = IoUtils.inputStreamFromResources(path);
        var fileUri = s3Driver.insertFile(UnixPath.of(path), content);
        var event = createS3Event(fileUri);
        return event;
    }

    private void mockCristinApiResponsesForAllSubUnits() {
        mockCristinApiForSubUnit(URI.create("https://api.sandbox.nva.aws.unit.no/cristin/organization/194.65.0.0"),
                                 NTNU_TOP_LEVEL_ORG_ID);
        mockCristinApiForSubUnit(URI.create("https://api.sandbox.nva.aws.unit.no/cristin/organization/194.63.10.0"),
                                 NTNU_TOP_LEVEL_ORG_ID);
        mockCristinApiForSubUnit(URI.create("https://api.sandbox.nva.aws.unit.no/cristin/organization/1920.13.0.0"),
                                 ST_OLAVS_TOP_LEVEL_ORG_ID);
        mockCristinApiForSubUnit(URI.create("https://api.sandbox.nva.aws.unit.no/cristin/organization/194.65.25.0"),
                                 NTNU_TOP_LEVEL_ORG_ID);
        mockCristinApiForTopLevelOrg(ST_OLAVS_TOP_LEVEL_ORG_ID);
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

    private void mockCristinApiForSubUnit(URI subUnitId, URI topLevelOrgId) {
        var cristinOrgResponse = createResponse(200, CRISTIN_ORG_RESPONSE
                                                         .replace(REPLACE_SUB_UNIT_STRING, subUnitId.toString())
                                                         .replace(REPLACE_TOP_LEVEL_STRING, topLevelOrgId.toString()));
        when(uriRetriever.fetchResponse(eq(subUnitId), any())).thenReturn(Optional.of(cristinOrgResponse));
    }

    private void mockCristinApiForTopLevelOrg(URI topLevelOrgId) {
        var cristinOrgResponse = createResponse(200, CRISTIN_ORG_RESPONSE
                                                         .replace(REPLACE_TOP_LEVEL_STRING, topLevelOrgId.toString()));
        when(uriRetriever.fetchResponse(eq(topLevelOrgId), any())).thenReturn(Optional.of(cristinOrgResponse));
    }
}
