package no.sikt.nva.nvi.events.evaluator;

import static no.sikt.nva.nvi.common.EnvironmentFixtures.getEvaluateNviCandidateHandlerEnvironment;
import static no.sikt.nva.nvi.common.db.PeriodRepositoryFixtures.setupOpenPeriod;
import static no.sikt.nva.nvi.common.db.PeriodRepositoryFixtures.updateRequestFromPeriod;
import static no.sikt.nva.nvi.common.dto.CustomerDtoFixtures.createNonNviCustomer;
import static no.sikt.nva.nvi.common.dto.CustomerDtoFixtures.createNviCustomer;
import static no.sikt.nva.nvi.common.model.PublicationDateFixtures.randomPublicationDateInYear;
import static no.sikt.nva.nvi.events.evaluator.TestUtils.createEvent;
import static no.sikt.nva.nvi.test.TestConstants.COUNTRY_CODE_NORWAY;
import static no.sikt.nva.nvi.test.TestConstants.HARDCODED_JSON_PUBLICATION_DATE;
import static no.unit.nva.testutils.RandomDataGenerator.objectMapper;
import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.amazonaws.services.lambda.runtime.Context;
import com.fasterxml.jackson.core.JsonProcessingException;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.net.URI;
import java.util.List;
import java.util.Optional;
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
import no.sikt.nva.nvi.common.service.exception.CandidateNotFoundException;
import no.sikt.nva.nvi.common.service.model.Candidate;
import no.sikt.nva.nvi.common.service.model.InstitutionPoints;
import no.sikt.nva.nvi.events.model.CandidateEvaluatedMessage;
import no.sikt.nva.nvi.events.model.PersistedResourceMessage;
import no.sikt.nva.nvi.test.SampleExpandedPublication;
import no.unit.nva.auth.uriretriever.UriRetriever;
import no.unit.nva.clients.CustomerDto;
import no.unit.nva.clients.CustomerList;
import no.unit.nva.clients.IdentityServiceClient;
import no.unit.nva.s3.S3Driver;
import no.unit.nva.stubs.FakeContext;
import nva.commons.apigateway.exceptions.ApiGatewayException;
import nva.commons.core.Environment;
import org.junit.jupiter.api.BeforeEach;

class EvaluationTest {

  protected static final RoundingMode ROUNDING_MODE = RoundingMode.HALF_UP;
  protected static final Context CONTEXT = new FakeContext();
  protected static final Environment ENVIRONMENT = getEvaluateNviCandidateHandlerEnvironment();
  protected static final int SCALE = 4;
  private static final String EVALUATED_CANDIDATE_QUEUE_URL = "CANDIDATE_QUEUE_URL";
  private static final URI EXAMPLE_NVI_CUSTOMER_ORG =
      URI.create("https://api.dev.nva.aws.unit.no/cristin/organization/194.0.0.0");
  private static final URI EXAMPLE_NON_NVI_CUSTOMER_ORG =
      URI.create("https://api.dev.nva.aws.unit.no/cristin/organization/150.0.0.0");
  private static final URI SIKT_CRISTIN_ORG_ID =
      URI.create("https://api.dev.nva.aws.unit.no/cristin/organization/20754.0.0.0");

  protected TestScenario scenario;
  protected S3Driver s3Driver;
  protected EvaluateNviCandidateHandler handler;
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

  private static List<CustomerDto> getDefaultCustomers() {
    return List.of(
        createNviCustomer(SIKT_CRISTIN_ORG_ID),
        createNviCustomer(EXAMPLE_NVI_CUSTOMER_ORG),
        createNonNviCustomer(EXAMPLE_NON_NVI_CUSTOMER_ORG));
  }

  protected void mockGetAllCustomersResponse(List<CustomerDto> customers) {
    try {
      when(identityServiceClient.getAllCustomers()).thenReturn(new CustomerList(customers));
    } catch (ApiGatewayException exception) {
      throw new RuntimeException(exception);
    }
  }

  @BeforeEach
  void commonSetup() {
    identityServiceClient = mock(IdentityServiceClient.class);
    mockGetAllCustomersResponse(getDefaultCustomers());

    scenario = new TestScenario();
    uriRetriever = scenario.getMockedUriRetriever();
    setupOpenPeriod(scenario, HARDCODED_JSON_PUBLICATION_DATE.year());

    queueClient = new FakeSqsClient();
    s3Driver = scenario.getS3DriverForExpandedResourcesBucket();
    storageReader = scenario.getS3StorageReaderForExpandedResourcesBucket();

    handler = createHandler();
  }

  protected void assertThatPublicationIsValidCandidate(URI publicationId) {
    var candidate = candidateService.getCandidateByPublicationId(publicationId);
    assertThat(candidate.getTotalPoints()).isPositive();
    assertThat(candidate.isApplicable()).isTrue();
  }

  protected void assertThatPublicationIsNonCandidate(URI publicationId) {
    var candidate = candidateService.getCandidateByPublicationId(publicationId);
    assertThat(candidate.approvals()).isEmpty();
    assertThat(candidate.isApplicable()).isFalse();
    assertThat(candidate.isReported()).isFalse();
  }

  protected void assertThatNoCandidateExistsForPublication(URI publicationId) {
    assertThrows(
        CandidateNotFoundException.class,
        () -> candidateService.getCandidateByPublicationId(publicationId));
  }

  protected EvaluateNviCandidateHandler createHandler() {
    var evaluationEnvironment = getEvaluateNviCandidateHandlerEnvironment();
    var periodRepository = scenario.getPeriodRepository();
    candidateRepository = scenario.getCandidateRepository();
    periodService = new NviPeriodService(evaluationEnvironment, periodRepository);
    candidateService =
        new CandidateService(evaluationEnvironment, periodRepository, candidateRepository);
    evaluatorService = new EvaluatorService(identityServiceClient, storageReader, candidateService);
    return new EvaluateNviCandidateHandler(evaluatorService, queueClient, evaluationEnvironment);
  }

  protected void evaluate(SampleExpandedPublication publication) {
    var fileUri = scenario.setupExpandedPublicationInS3(publication);
    var event = createEvent(new PersistedResourceMessage(fileUri));
    handler.handleRequest(event, CONTEXT);
  }

  protected CandidateType getEvaluationResult(SampleExpandedPublication publication) {
    evaluate(publication);
    return getMessageFromUpsertQueue().get().candidate();
  }

  protected UpsertNviCandidateRequest getEvaluatedCandidate(SampleExpandedPublication publication) {
    return (UpsertNviCandidateRequest) getEvaluationResult(publication);
  }

  /**
   * Evaluates a publication as if it was stored in S3 and returns the candidate from the database.
   * This wrapper is an abstraction of the whole processing chain in `event-handlers`, including
   * parsing, evaluation, and upsert.
   */
  protected void evaluatePublication(String publicationJson) {
    var fileUri = scenario.setupExpandedPublicationInS3(publicationJson);
    var evaluationEvent = createEvent(new PersistedResourceMessage(fileUri));
    handler.handleRequest(evaluationEvent, CONTEXT);

    getMessageFromUpsertQueue().ifPresent(this::upsertEvaluationResult);
  }

  private void upsertEvaluationResult(CandidateEvaluatedMessage evaluationResult) {
    switch (evaluationResult.candidate()) {
      case UpsertNviCandidateRequest candidate -> candidateService.upsertCandidate(candidate);
      case UpsertNonNviCandidateRequest nonCandidate ->
          candidateService.updateCandidate(nonCandidate);
    }
  }

  protected void evaluatePublication(SampleExpandedPublicationFactory publicationFactory) {
    mockGetAllCustomersResponse(publicationFactory.getCustomerOrganizations());
    evaluatePublication(publicationFactory.getExpandedPublication().toJsonString());
  }

  protected SampleExpandedPublicationFactory createApplicablePublication(String publicationYear) {
    var factory =
        new SampleExpandedPublicationFactory(scenario)
            .withPublicationDate(randomPublicationDateInYear(publicationYear));
    var nviOrganization = factory.setupTopLevelOrganization(COUNTRY_CODE_NORWAY, true);
    mockGetAllCustomersResponse(factory.getCustomerOrganizations());
    return factory.withCreatorAffiliatedWith(nviOrganization);
  }

  protected Candidate setupExistingCandidateForPublication(
      SampleExpandedPublicationFactory publicationFactory) {
    mockGetAllCustomersResponse(publicationFactory.getCustomerOrganizations());
    var publication = publicationFactory.getExpandedPublication();
    var year = publication.publicationDate().year();
    var period =
        scenario
            .getPeriodService()
            .findByPublishingYear(year)
            .orElse(setupOpenPeriod(scenario, year));

    if (period.isOpen()) {
      evaluatePublication(publication.toJsonString());
    } else {
      setupOpenPeriod(scenario, year);
      evaluatePublication(publication.toJsonString());
      scenario.getPeriodService().update(updateRequestFromPeriod(period));
    }
    return candidateService.getCandidateByPublicationId(publication.id());
  }

  private Optional<CandidateEvaluatedMessage> getMessageFromUpsertQueue() {
    var upsertQueue = ENVIRONMENT.readEnv(EVALUATED_CANDIDATE_QUEUE_URL);
    try {
      var sentMessages = queueClient.receiveMessage(upsertQueue, 1);
      if (sentMessages.messages().isEmpty()) {
        return Optional.empty();
      }
      var message = sentMessages.messages().getFirst();
      queueClient.deleteMessage(upsertQueue, message.receiptHandle());
      return Optional.of(objectMapper.readValue(message.body(), CandidateEvaluatedMessage.class));
    } catch (JsonProcessingException e) {
      throw new RuntimeException(e);
    }
  }
}
