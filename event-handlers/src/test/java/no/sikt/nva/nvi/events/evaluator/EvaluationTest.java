package no.sikt.nva.nvi.events.evaluator;

import static no.sikt.nva.nvi.events.evaluator.TestUtils.createResponse;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import com.amazonaws.services.lambda.runtime.Context;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.net.URI;
import java.net.http.HttpResponse;
import no.sikt.nva.nvi.common.S3StorageReader;
import no.sikt.nva.nvi.common.client.OrganizationRetriever;
import no.sikt.nva.nvi.common.db.CandidateRepository;
import no.sikt.nva.nvi.common.db.PeriodRepository;
import no.sikt.nva.nvi.common.service.model.InstitutionPoints;
import no.sikt.nva.nvi.events.model.NviCandidate;
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
import org.junit.jupiter.api.BeforeEach;

public class EvaluationTest extends LocalDynamoTest {

    protected static final RoundingMode ROUNDING_MODE = RoundingMode.HALF_UP;

    protected static final int SCALE = 4;

    protected static final String BUCKET_NAME = "ignoredBucket";
    protected static final String CUSTOMER_API_NVI_RESPONSE = "{" + "\"nviInstitution\" : \"true\"" + "}";
    protected final Context context = mock(Context.class);
    protected HttpResponse<String> notFoundResponse;
    protected HttpResponse<String> internalServerErrorResponse;
    protected HttpResponse<String> okResponse;
    protected S3Driver s3Driver;
    protected EvaluateNviCandidateHandler handler;
    protected AuthorizedBackendUriRetriever authorizedBackendUriRetriever;
    protected UriRetriever uriRetriever;
    protected FakeSqsClient queueClient;
    protected CandidateRepository candidateRepository;
    protected Environment env;
    protected S3StorageReader storageReader;
    protected PeriodRepository periodRepository;
    protected EvaluatorService evaluatorService;

    protected BigDecimal getPointsForInstitution(NviCandidate candidate, URI institutionId) {
        return candidate.institutionPoints().stream()
                   .filter(institutionPoints -> institutionPoints.institutionId().equals(institutionId))
                   .map(InstitutionPoints::institutionPoints)
                   .findFirst()
                   .orElseThrow();
    }

    @BeforeEach
    void setup() {
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

    private static void mockSecretManager() {
        try (var secretsManagerClient = new FakeSecretsManagerClient()) {
            var credentials = new BackendClientCredentials("id", "secret");
            secretsManagerClient.putPlainTextSecret("secret", credentials.toString());
        }
    }

    private void setupHttpResponses() {
        notFoundResponse = createResponse(404, StringUtils.EMPTY_STRING);
        internalServerErrorResponse = createResponse(500, StringUtils.EMPTY_STRING);
        okResponse = createResponse(200, CUSTOMER_API_NVI_RESPONSE);
    }
}
