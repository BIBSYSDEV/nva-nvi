package cucumber.contexts;

import static no.sikt.nva.nvi.common.LocalDynamoTestSetup.initializeTestDatabase;
import static org.mockito.Mockito.mock;

import no.sikt.nva.nvi.common.client.OrganizationRetriever;
import no.sikt.nva.nvi.common.db.CandidateRepository;
import no.sikt.nva.nvi.common.db.PeriodRepository;
import no.unit.nva.auth.uriretriever.UriRetriever;
import software.amazon.awssdk.services.dynamodb.DynamoDbClient;

public class ScenarioContext {
  private final DynamoDbClient localDynamo = initializeTestDatabase();
  private final CandidateRepository candidateRepository;
  private final PeriodRepository periodRepository;
  private final UriRetriever mockUriRetriever = mock(UriRetriever.class);
  private final OrganizationRetriever mockOrganizationRetriever =
      new OrganizationRetriever(mockUriRetriever);

  public ScenarioContext() {
    this.candidateRepository = new CandidateRepository(localDynamo);
    this.periodRepository = new PeriodRepository(localDynamo);
  }

  public CandidateRepository getCandidateRepository() {
    return candidateRepository;
  }

  public PeriodRepository getPeriodRepository() {
    return periodRepository;
  }

  public OrganizationRetriever getOrganizationRetriever() {
    return mockOrganizationRetriever;
  }

  public UriRetriever getUriRetriever() {
    return mockUriRetriever;
  }
}
