package no.sikt.nva.nvi.common.service;

import static no.sikt.nva.nvi.common.EnvironmentFixtures.getGlobalEnvironment;
import static no.sikt.nva.nvi.common.UpsertRequestBuilder.randomUpsertRequestBuilder;
import static no.sikt.nva.nvi.common.db.PeriodRepositoryFixtures.setupOpenPeriod;
import static no.sikt.nva.nvi.common.dto.NviCreatorDtoFixtures.verifiedNviCreatorDtoFrom;
import static no.sikt.nva.nvi.common.dto.PointCalculationDtoBuilder.randomPointCalculationDtoBuilder;
import static no.sikt.nva.nvi.common.model.OrganizationFixtures.getAsOrganizationLeafNode;
import static no.sikt.nva.nvi.test.TestUtils.CURRENT_YEAR;
import static no.sikt.nva.nvi.test.TestUtils.randomBigDecimal;

import java.math.RoundingMode;
import java.net.URI;
import no.sikt.nva.nvi.common.TestScenario;
import no.sikt.nva.nvi.common.db.CandidateRepository;
import no.sikt.nva.nvi.common.db.PeriodRepository;
import no.sikt.nva.nvi.common.dto.UpsertNviCandidateRequest;
import no.sikt.nva.nvi.common.service.model.Candidate;
import no.unit.nva.auth.uriretriever.UriRetriever;
import nva.commons.core.Environment;
import nva.commons.core.paths.UriWrapper;
import org.junit.jupiter.api.BeforeEach;

public class CandidateTestSetup {

  protected static final int EXPECTED_SCALE = 4;
  protected static final RoundingMode EXPECTED_ROUNDING_MODE = RoundingMode.HALF_UP;
  private static final Environment ENVIRONMENT = getGlobalEnvironment();
  private static final String BASE_PATH = ENVIRONMENT.readEnv("CUSTOM_DOMAIN_BASE_PATH");
  private static final String API_DOMAIN = ENVIRONMENT.readEnv("API_HOST");
  public static final URI CONTEXT_URI =
      UriWrapper.fromHost(API_DOMAIN).addChild(BASE_PATH, "context").getUri();
  protected TestScenario scenario;
  protected CandidateService candidateService;
  protected NoteService noteService;
  protected CandidateRepository candidateRepository;
  protected PeriodRepository periodRepository;
  protected UriRetriever mockUriRetriever;

  protected static UpsertNviCandidateRequest createUpsertRequestWithDecimalScale(
      int scale, URI institutionId) {
    var creator = verifiedNviCreatorDtoFrom(institutionId);
    var pointValue = randomBigDecimal(scale);
    var pointCalculation =
        randomPointCalculationDtoBuilder()
            .withCollaborationFactor(randomBigDecimal(scale))
            .withBasePoints(pointValue)
            .withTotalPoints(pointValue)
            .withAdditionalPointFor(institutionId, institutionId, pointValue, creator.id())
            .build();

    return randomUpsertRequestBuilder()
        .withPointCalculation(pointCalculation)
        .withNviCreators(creator)
        .withTopLevelOrganizations(getAsOrganizationLeafNode(institutionId))
        .build();
  }

  @BeforeEach
  void setup() {
    scenario = new TestScenario();
    candidateRepository = scenario.getCandidateRepository();
    periodRepository = scenario.getPeriodRepository();
    candidateService = new CandidateService(ENVIRONMENT, periodRepository, candidateRepository);
    noteService = new NoteService(candidateRepository);
    mockUriRetriever = scenario.getMockedUriRetriever();
    setupOpenPeriod(scenario, CURRENT_YEAR);
  }

  /**
   * Get the organization ID for an arbitrary organization affiliated with the Candidate. This is
   * needed because certain operations must be done in the context of a user, but the tests use
   * randomly generated data.
   */
  protected static URI getAnyOrganizationId(Candidate candidate) {
    return candidate.getInstitutionPoints().getFirst().institutionId();
  }
}
