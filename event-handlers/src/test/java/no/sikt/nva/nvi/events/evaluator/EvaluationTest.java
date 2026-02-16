package no.sikt.nva.nvi.events.evaluator;

import static no.sikt.nva.nvi.common.EnvironmentFixtures.getEvaluateNviCandidateHandlerEnvironment;
import static no.sikt.nva.nvi.common.db.PeriodRepositoryFixtures.setupOpenPeriod;
import static no.sikt.nva.nvi.common.db.PeriodRepositoryFixtures.updateRequestFromPeriod;
import static no.sikt.nva.nvi.common.dto.CustomerDtoFixtures.getDefaultCustomers;
import static no.sikt.nva.nvi.common.model.PublicationDateFixtures.randomPublicationDateInYear;
import static no.sikt.nva.nvi.events.evaluator.TestUtils.createEvent;
import static no.sikt.nva.nvi.test.TestConstants.COUNTRY_CODE_NORWAY;
import static no.sikt.nva.nvi.test.TestConstants.HARDCODED_JSON_PUBLICATION_DATE;
import static no.sikt.nva.nvi.test.TestConstants.HARDCODED_PUBLICATION_ID;
import static nva.commons.core.ioutils.IoUtils.stringFromResources;
import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.amazonaws.services.lambda.runtime.Context;
import java.math.RoundingMode;
import java.net.URI;
import java.nio.file.Path;
import java.util.List;
import no.sikt.nva.nvi.common.SampleExpandedPublicationFactory;
import no.sikt.nva.nvi.common.TestScenario;
import no.sikt.nva.nvi.common.queue.FakeSqsClient;
import no.sikt.nva.nvi.common.service.CandidateService;
import no.sikt.nva.nvi.common.service.exception.CandidateNotFoundException;
import no.sikt.nva.nvi.common.service.model.Candidate;
import no.sikt.nva.nvi.events.model.PersistedResourceMessage;
import no.unit.nva.clients.CustomerDto;
import no.unit.nva.clients.CustomerList;
import no.unit.nva.clients.IdentityServiceClient;
import no.unit.nva.identifiers.SortableIdentifier;
import no.unit.nva.s3.S3Driver;
import no.unit.nva.stubs.FakeContext;
import nva.commons.apigateway.exceptions.ApiGatewayException;
import org.junit.jupiter.api.BeforeEach;

class EvaluationTest {

  protected static final RoundingMode ROUNDING_MODE = RoundingMode.HALF_UP;
  protected static final Context CONTEXT = new FakeContext();
  protected static final int SCALE = 4;

  protected TestScenario scenario;
  protected S3Driver s3Driver;
  protected EvaluateNviCandidateHandler handler;
  protected IdentityServiceClient identityServiceClient;
  protected FakeSqsClient queueClient;
  protected CandidateService candidateService;

  protected static String getPublicationFromFile(String path, URI publicationId) {
    var identifier = SortableIdentifier.fromUri(publicationId);
    return stringFromResources(Path.of(path))
        .replace("__REPLACE_WITH_PUBLICATION_ID__", publicationId.toString())
        .replace("__REPLACE_WITH_PUBLICATION_IDENTIFIER__", identifier.toString());
  }

  protected static String getPublicationFromFile(String path) {
    return getPublicationFromFile(path, HARDCODED_PUBLICATION_ID);
  }

  @BeforeEach
  void commonSetup() {
    scenario = new TestScenario();
    queueClient = new FakeSqsClient();
    s3Driver = scenario.getS3DriverForExpandedResourcesBucket();
    identityServiceClient = mock(IdentityServiceClient.class);

    var evaluationEnvironment = getEvaluateNviCandidateHandlerEnvironment();
    candidateService =
        new CandidateService(
            evaluationEnvironment,
            scenario.getPeriodRepository(),
            scenario.getCandidateRepository());
    var evaluatorService =
        new EvaluatorService(
            identityServiceClient,
            scenario.getS3StorageReaderForExpandedResourcesBucket(),
            candidateService);
    handler =
        new EvaluateNviCandidateHandler(
            candidateService, evaluatorService, queueClient, evaluationEnvironment);

    mockGetAllCustomersResponse(getDefaultCustomers());
    setupOpenPeriod(scenario, HARDCODED_JSON_PUBLICATION_DATE.year());
  }

  protected void mockGetAllCustomersResponse(List<CustomerDto> customers) {
    try {
      when(identityServiceClient.getAllCustomers()).thenReturn(new CustomerList(customers));
    } catch (ApiGatewayException exception) {
      throw new RuntimeException(exception);
    }
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

  /**
   * Evaluates a publication as if it was stored in S3 and returns the candidate from the database.
   * This wrapper is an abstraction of the whole processing chain in `event-handlers`, including
   * parsing, evaluation, and upsert.
   */
  protected void handleEvaluation(String publicationJson) {
    var fileUri = scenario.setupExpandedPublicationInS3(publicationJson);
    var evaluationEvent = createEvent(new PersistedResourceMessage(fileUri));
    handler.handleRequest(evaluationEvent, CONTEXT);
  }

  protected void handleEvaluation(SampleExpandedPublicationFactory publicationFactory) {
    mockGetAllCustomersResponse(publicationFactory.getCustomerOrganizations());
    handleEvaluation(publicationFactory.getExpandedPublication().toJsonString());
  }

  protected SampleExpandedPublicationFactory createApplicablePublication(String publicationYear) {
    var factory =
        new SampleExpandedPublicationFactory()
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
      handleEvaluation(publication.toJsonString());
    } else {
      setupOpenPeriod(scenario, year);
      handleEvaluation(publication.toJsonString());
      scenario.getPeriodService().update(updateRequestFromPeriod(period));
    }
    return candidateService.getCandidateByPublicationId(publication.id());
  }
}
