package no.sikt.nva.nvi.events.evaluator;

import static no.sikt.nva.nvi.common.db.PeriodRepositoryFixtures.setupOpenPeriod;
import static no.sikt.nva.nvi.test.TestConstants.HARDCODED_JSON_PUBLICATION_DATE;
import static no.sikt.nva.nvi.test.TestUtils.createResponse;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.amazonaws.services.lambda.runtime.Context;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.net.URI;
import java.net.http.HttpResponse;
import no.sikt.nva.nvi.common.S3StorageReader;
import no.sikt.nva.nvi.common.TestScenario;
import no.sikt.nva.nvi.common.db.CandidateRepository;
import no.sikt.nva.nvi.common.db.PeriodRepository;
import no.sikt.nva.nvi.common.queue.FakeSqsClient;
import no.sikt.nva.nvi.common.service.model.InstitutionPoints;
import no.sikt.nva.nvi.events.evaluator.calculator.CreatorVerificationUtil;
import no.sikt.nva.nvi.events.model.NviCandidate;
import no.unit.nva.auth.uriretriever.AuthorizedBackendUriRetriever;
import no.unit.nva.auth.uriretriever.BackendClientCredentials;
import no.unit.nva.auth.uriretriever.UriRetriever;
import no.unit.nva.s3.S3Driver;
import no.unit.nva.stubs.FakeS3Client;
import no.unit.nva.stubs.FakeSecretsManagerClient;
import nva.commons.core.Environment;
import nva.commons.core.StringUtils;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;

public class EvaluationTest {

  protected static final RoundingMode ROUNDING_MODE = RoundingMode.HALF_UP;
  protected static final Environment ENVIRONMENT = mock(Environment.class);
  protected static final Context CONTEXT = mock(Context.class);
  protected static final int SCALE = 4;

  protected static final String BUCKET_NAME = "ignoredBucket";
  protected static final String CUSTOMER_API_NVI_RESPONSE =
      "{" + "\"nviInstitution\" : \"true\"" + "}";
  protected TestScenario scenario;
  protected HttpResponse<String> notFoundResponse;
  protected HttpResponse<String> internalServerErrorResponse;
  protected HttpResponse<String> okResponse;
  protected S3Driver s3Driver;
  protected EvaluateNviCandidateHandler handler;
  protected AuthorizedBackendUriRetriever authorizedBackendUriRetriever;
  protected UriRetriever uriRetriever;
  protected FakeSqsClient queueClient;
  protected CandidateRepository candidateRepository;
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

  @BeforeAll
  static void init() {
    when(ENVIRONMENT.readEnv("CANDIDATE_QUEUE_URL")).thenReturn("My test candidate queue url");
    when(ENVIRONMENT.readEnv("CANDIDATE_DLQ_URL")).thenReturn("My test candidate dlq url");
  }

  @BeforeEach
  void commonSetup() {
    scenario = new TestScenario();
    uriRetriever = scenario.getUriRetriever();
    candidateRepository = scenario.getCandidateRepository();
    periodRepository = scenario.getPeriodRepository();
    setupOpenPeriod(scenario, HARDCODED_JSON_PUBLICATION_DATE.year());

    setupHttpResponses();
    mockSecretManager();
    var s3Client = new FakeS3Client();
    authorizedBackendUriRetriever = mock(AuthorizedBackendUriRetriever.class);
    queueClient = new FakeSqsClient();
    s3Driver = new S3Driver(s3Client, BUCKET_NAME);
    storageReader = new S3StorageReader(s3Client, BUCKET_NAME);
    var creatorVerificationUtil = new CreatorVerificationUtil(authorizedBackendUriRetriever);
    evaluatorService =
        new EvaluatorService(
            storageReader, creatorVerificationUtil, candidateRepository, periodRepository);
    handler = new EvaluateNviCandidateHandler(evaluatorService, queueClient, ENVIRONMENT);
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
