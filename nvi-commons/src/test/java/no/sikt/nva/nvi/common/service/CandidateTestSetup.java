package no.sikt.nva.nvi.common.service;

import static no.sikt.nva.nvi.test.TestUtils.periodRepositoryReturningOpenedPeriod;
import static no.sikt.nva.nvi.test.TestUtils.randomBigDecimal;
import static no.sikt.nva.nvi.test.UpsertRequestBuilder.randomUpsertRequestBuilder;
import static no.unit.nva.testutils.RandomDataGenerator.randomUri;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.net.URI;
import java.time.ZonedDateTime;
import java.util.List;
import java.util.Map;
import no.sikt.nva.nvi.common.db.CandidateRepository;
import no.sikt.nva.nvi.common.db.PeriodRepository;
import no.sikt.nva.nvi.common.service.dto.VerifiedNviCreatorDto;
import no.sikt.nva.nvi.common.service.model.InstitutionPoints;
import no.sikt.nva.nvi.common.service.model.InstitutionPoints.CreatorAffiliationPoints;
import no.sikt.nva.nvi.common.service.requests.UpsertCandidateRequest;
import no.sikt.nva.nvi.test.LocalDynamoTest;
import nva.commons.core.Environment;
import nva.commons.core.paths.UriWrapper;
import org.junit.jupiter.api.BeforeEach;

public class CandidateTestSetup extends LocalDynamoTest {

  protected static final int EXPECTED_SCALE = 4;
  protected static final RoundingMode EXPECTED_ROUNDING_MODE = RoundingMode.HALF_UP;
  private static final Environment ENVIRONMENT = new Environment();
  private static final String BASE_PATH = ENVIRONMENT.readEnv("CUSTOM_DOMAIN_BASE_PATH");
  private static final String API_DOMAIN = ENVIRONMENT.readEnv("API_HOST");
  public static final URI CONTEXT_URI =
      UriWrapper.fromHost(API_DOMAIN).addChild(BASE_PATH, "context").getUri();
  protected CandidateRepository candidateRepository;
  protected PeriodRepository periodRepository;

  protected static UpsertCandidateRequest createUpsertRequestWithDecimalScale(
      int scale, URI institutionId) {
    var creatorId = randomUri();
    var creators = Map.of(creatorId, List.of(institutionId));
    var verifiedCreator = new VerifiedNviCreatorDto(creatorId, List.of(institutionId));
    var points =
        List.of(createInstitutionPoints(institutionId, randomBigDecimal(scale), creatorId));

    return randomUpsertRequestBuilder()
        .withPoints(points)
        .withCreators(creators)
        .withVerifiedCreators(List.of(verifiedCreator))
        .withCollaborationFactor(randomBigDecimal(scale))
        .withBasePoints(randomBigDecimal(scale))
        .withTotalPoints(randomBigDecimal(scale))
        .build();
  }

  @BeforeEach
  void setup() {
    localDynamo = initializeTestDatabase();
    candidateRepository = new CandidateRepository(localDynamo);
    periodRepository = periodRepositoryReturningOpenedPeriod(ZonedDateTime.now().getYear());
  }

  private static InstitutionPoints createInstitutionPoints(
      URI institutionId, BigDecimal institutionPoints, URI creatorId) {
    return new InstitutionPoints(
        institutionId,
        institutionPoints,
        List.of(new CreatorAffiliationPoints(creatorId, institutionId, institutionPoints)));
  }
}
