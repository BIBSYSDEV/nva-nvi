package no.sikt.nva.nvi.events.evaluator;

import static no.sikt.nva.nvi.common.EnvironmentFixtures.getEvaluateNviCandidateHandlerEnvironment;
import static no.sikt.nva.nvi.common.db.PeriodRepositoryFixtures.setupOpenPeriod;
import static no.sikt.nva.nvi.common.db.PeriodRepositoryFixtures.updateRequestFromPeriod;
import static no.sikt.nva.nvi.common.model.PublicationDateFixtures.randomPublicationDateInYear;
import static no.sikt.nva.nvi.events.evaluator.TestUtils.createEvent;
import static no.sikt.nva.nvi.test.TestConstants.COUNTRY_CODE_NORWAY;
import static no.sikt.nva.nvi.test.TestConstants.HARDCODED_JSON_PUBLICATION_DATE;
import static no.sikt.nva.nvi.test.TestUtils.createResponse;
import static no.unit.nva.testutils.RandomDataGenerator.objectMapper;
import static org.mockito.Mockito.mock;

import com.amazonaws.services.lambda.runtime.Context;
import com.fasterxml.jackson.core.JsonProcessingException;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.net.URI;
import java.net.http.HttpResponse;
import no.sikt.nva.nvi.common.S3StorageReader;
import no.sikt.nva.nvi.common.SampleExpandedPublicationFactory;
import no.sikt.nva.nvi.common.TestScenario;
import no.sikt.nva.nvi.common.db.CandidateRepository;
import no.sikt.nva.nvi.common.dto.CandidateType;
import no.sikt.nva.nvi.common.dto.UpsertNonNviCandidateRequest;
import no.sikt.nva.nvi.common.dto.UpsertNviCandidateRequest;
import no.sikt.nva.nvi.common.queue.FakeSqsClient;
import no.sikt.nva.nvi.common.service.CandidateService;
import no.sikt.nva.nvi.common.service.NviPeriodService;
import no.sikt.nva.nvi.common.service.model.Candidate;
import no.sikt.nva.nvi.common.service.model.InstitutionPoints;
import no.sikt.nva.nvi.events.evaluator.calculator.CreatorVerificationUtil;
import no.sikt.nva.nvi.events.model.CandidateEvaluatedMessage;
import no.sikt.nva.nvi.events.model.PersistedResourceMessage;
import no.sikt.nva.nvi.test.SampleExpandedPublication;
import no.unit.nva.auth.uriretriever.AuthorizedBackendUriRetriever;
import no.unit.nva.auth.uriretriever.BackendClientCredentials;
import no.unit.nva.auth.uriretriever.UriRetriever;
import no.unit.nva.clients.IdentityServiceClient;
import no.unit.nva.s3.S3Driver;
import no.unit.nva.stubs.FakeSecretsManagerClient;
import nva.commons.core.StringUtils;
import org.junit.jupiter.api.BeforeEach;

@SuppressWarnings("PMD.CouplingBetweenObjects")
class EvaluationTest {

  protected static final RoundingMode ROUNDING_MODE = RoundingMode.HALF_UP;
  protected static final Context CONTEXT = mock(Context.class);
  protected static final int SCALE = 4;

  protected static final String CUSTOMER_API_NVI_RESPONSE =
      "{" + "\"nviInstitution\" : \"true\"" + "}";
  protected TestScenario scenario;
  protected HttpResponse<String> notFoundResponse;
  protected HttpResponse<String> internalServerErrorResponse;
  protected HttpResponse<String> okResponse;
  protected S3Driver s3Driver;
  protected EvaluateNviCandidateHandler handler;
  protected AuthorizedBackendUriRetriever authorizedBackendUriRetriever;
  protected IdentityServiceClient identityServiceClient;
  protected UriRetriever uriRetriever;
  protected FakeSqsClient queueClient;
  protected CandidateRepository candidateRepository;
  protected S3StorageReader storageReader;
  protected EvaluatorService evaluatorService;
  protected NviPeriodService periodService;
  protected CandidateService candidateService;

  protected BigDecimal getPointsForInstitution(
      UpsertNviCandidateRequest candidate, URI institutionId) {
    return candidate.pointCalculation().institutionPoints().stream()
        .filter(institutionPoints -> institutionPoints.institutionId().equals(institutionId))
        .map(InstitutionPoints::institutionPoints)
        .findFirst()
        .orElseThrow();
  }

  @BeforeEach
  void commonSetup() {
    var evaluationEnvironment = getEvaluateNviCandidateHandlerEnvironment();
    scenario = new TestScenario();
    identityServiceClient = mock(IdentityServiceClient.class);
    uriRetriever = scenario.getMockedUriRetriever();
    setupOpenPeriod(scenario, HARDCODED_JSON_PUBLICATION_DATE.year());

    setupHttpResponses();
    mockSecretManager();
    authorizedBackendUriRetriever = scenario.getMockedAuthorizedBackendUriRetriever();
    queueClient = new FakeSqsClient();
    s3Driver = scenario.getS3DriverForExpandedResourcesBucket();
    storageReader = scenario.getS3StorageReaderForExpandedResourcesBucket();

    var creatorVerificationUtil =
        new CreatorVerificationUtil(authorizedBackendUriRetriever, evaluationEnvironment);
    var periodRepository = scenario.getPeriodRepository();
    candidateRepository = scenario.getCandidateRepository();
    periodService = new NviPeriodService(evaluationEnvironment, periodRepository);
    candidateService =
        new CandidateService(evaluationEnvironment, periodRepository, candidateRepository);
    evaluatorService =
        new EvaluatorService(
            identityServiceClient, storageReader, creatorVerificationUtil, candidateService);
    handler = new EvaluateNviCandidateHandler(evaluatorService, queueClient, evaluationEnvironment);
  }

  protected void evaluate(SampleExpandedPublication publication) {
    var fileUri = scenario.setupExpandedPublicationInS3(publication);
    var event = createEvent(new PersistedResourceMessage(fileUri));
    handler.handleRequest(event, CONTEXT);
  }

  protected CandidateType getEvaluationResult(SampleExpandedPublication publication) {
    evaluate(publication);
    return getMessageBody().candidate();
  }

  protected UpsertNviCandidateRequest getEvaluatedCandidate(SampleExpandedPublication publication) {
    return (UpsertNviCandidateRequest) getEvaluationResult(publication);
  }

  protected Candidate evaluatePublicationAndGetPersistedCandidate(
      URI publicationId, String publicationJson) {
    handleEvaluation(publicationJson);
    return candidateService.getCandidateByPublicationId(publicationId);
  }

  protected Candidate evaluatePublicationAndGetPersistedCandidate(
      SampleExpandedPublication publication) {
    return evaluatePublicationAndGetPersistedCandidate(
        publication.id(), publication.toJsonString());
  }

  /**
   * Evaluates a publication as if it was stored in S3 and returns the candidate from the database.
   * This wrapper is an abstraction of the whole processing chain in `event-handlers`, including
   * parsing, evaluation, and upsert.
   */
  protected void handleEvaluation(String publicationJson) {
    var fileUri = scenario.setupExpandedPublicationInS3(publicationJson);
    var evaluationEvent = createEvent(new PersistedResourceMessage(fileUri));
    handler.handleRequest(evaluationEvent, CONTEXT);

    var message = getMessageBody();
    switch (message.candidate()) {
      case UpsertNviCandidateRequest candidate -> candidateService.upsertCandidate(candidate);
      case UpsertNonNviCandidateRequest nonCandidate ->
          candidateService.updateCandidate(nonCandidate);
    }
  }

  protected SampleExpandedPublicationFactory createApplicablePublication(String publicationYear) {
    var factory =
        new SampleExpandedPublicationFactory(scenario)
            .withPublicationDate(randomPublicationDateInYear(publicationYear));
    var nviOrganization = factory.setupTopLevelOrganization(COUNTRY_CODE_NORWAY, true);
    return factory.withCreatorAffiliatedWith(nviOrganization);
  }

  protected Candidate setupExistingCandidateForPublication(
      SampleExpandedPublicationFactory publicationFactory) {
    var publication = publicationFactory.getExpandedPublication();
    var year = publication.publicationDate().year();
    var period =
        scenario
            .getPeriodService()
            .findByPublishingYear(year)
            .orElse(setupOpenPeriod(scenario, year));

    if (period.isOpen()) {
      handleEvaluation(publication.toJsonString());
    } else {
      setupOpenPeriod(scenario, year);
      handleEvaluation(publication.toJsonString());
      scenario.getPeriodService().update(updateRequestFromPeriod(period));
    }
    return candidateService.getCandidateByPublicationId(publication.id());
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

  private CandidateEvaluatedMessage getMessageBody() {
    try {
      var sentMessages = queueClient.getSentMessages();
      var message = sentMessages.removeFirst();
      return objectMapper.readValue(message.messageBody(), CandidateEvaluatedMessage.class);
    } catch (JsonProcessingException e) {
      throw new RuntimeException(e);
    }
  }
}
