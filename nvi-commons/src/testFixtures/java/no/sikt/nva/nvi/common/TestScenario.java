package no.sikt.nva.nvi.common;

import static no.sikt.nva.nvi.common.UpsertRequestFixtures.createUpdateStatusRequest;
import static no.sikt.nva.nvi.common.model.OrganizationFixtures.mockOrganizationResponseForAffiliations;
import static no.sikt.nva.nvi.test.TestUtils.randomUriWithSuffix;
import static no.unit.nva.testutils.RandomDataGenerator.randomString;
import static org.mockito.Mockito.mock;

import java.net.URI;
import java.util.List;
import no.sikt.nva.nvi.common.client.OrganizationRetriever;
import no.sikt.nva.nvi.common.client.model.Organization;
import no.sikt.nva.nvi.common.db.CandidateRepository;
import no.sikt.nva.nvi.common.db.PeriodRepository;
import no.sikt.nva.nvi.common.db.PeriodRepositoryFixtures;
import no.sikt.nva.nvi.common.service.model.ApprovalStatus;
import no.sikt.nva.nvi.common.service.model.Candidate;
import no.sikt.nva.nvi.common.service.requests.UpsertCandidateRequest;
import no.unit.nva.auth.uriretriever.UriRetriever;
import software.amazon.awssdk.services.dynamodb.DynamoDbClient;

public class TestScenario extends LocalDynamoTestSetup {

  private final DynamoDbClient localDynamo = initializeTestDatabase();
  private final CandidateRepository candidateRepository;
  private final PeriodRepository periodRepository;
  private final UriRetriever mockUriRetriever = mock(UriRetriever.class);
  private final OrganizationRetriever mockOrganizationRetriever =
      new OrganizationRetriever(mockUriRetriever);
  private final Organization defaultOrganization;

  public TestScenario() {
    super();
    this.candidateRepository = new CandidateRepository(localDynamo);
    this.periodRepository = new PeriodRepository(localDynamo);
    this.defaultOrganization = setupTopLevelOrganizationWithSubUnits();
  }

  public final Organization setupTopLevelOrganizationWithSubUnits() {
    var topLevelId = randomUriWithSuffix("topLevel");
    var subUnits = List.of(randomUriWithSuffix("subUnit1"), randomUriWithSuffix("subUnit2"));

    mockOrganizationResponseForAffiliations(topLevelId, subUnits, mockUriRetriever);
    return mockOrganizationRetriever.fetchOrganization(topLevelId);
  }

  public CandidateRepository getCandidateRepository() {
    return candidateRepository;
  }

  public PeriodRepository getPeriodRepository() {
    return periodRepository;
  }

  public Organization getDefaultOrganization() {
    return defaultOrganization;
  }

  public void setupFuturePeriod(String year) {
    PeriodRepositoryFixtures.setupFuturePeriod(year, periodRepository);
  }

  public void setupOpenPeriod(String year) {
    PeriodRepositoryFixtures.setupOpenPeriod(year, periodRepository);
  }

  public void setupClosedPeriod(String year) {
    PeriodRepositoryFixtures.setupClosedPeriod(year, periodRepository);
  }

  public Candidate upsertCandidate(UpsertCandidateRequest request) {
    Candidate.upsert(request, candidateRepository, periodRepository);
    return Candidate.fetchByPublicationId(
        request::publicationId, candidateRepository, periodRepository);
  }

  public Candidate updateApprovalStatus(
      Candidate candidate, ApprovalStatus status, URI topLevelOrganizationId) {
    var updateRequest = createUpdateStatusRequest(status, topLevelOrganizationId, randomString());
    return candidate.updateApprovalStatus(updateRequest, mockOrganizationRetriever);
  }
}
