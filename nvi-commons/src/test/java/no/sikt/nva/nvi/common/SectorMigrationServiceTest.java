package no.sikt.nva.nvi.common;

import static java.util.UUID.randomUUID;
import static no.sikt.nva.nvi.common.db.CandidateDaoFixtures.createCandidateInRepository;
import static no.sikt.nva.nvi.common.db.DbCandidateFixtures.randomCandidateBuilder;
import static no.sikt.nva.nvi.common.db.PeriodRepositoryFixtures.setupOpenPeriod;
import static no.sikt.nva.nvi.common.model.PointCalculationFixtures.randomPointCalculation;
import static no.sikt.nva.nvi.common.model.Sector.HEALTH;
import static no.sikt.nva.nvi.common.model.Sector.INSTITUTE;
import static no.sikt.nva.nvi.common.model.Sector.UHI;
import static no.sikt.nva.nvi.common.model.Sector.UNKNOWN;
import static no.sikt.nva.nvi.test.TestUtils.CURRENT_YEAR;
import static no.unit.nva.testutils.RandomDataGenerator.randomBoolean;
import static no.unit.nva.testutils.RandomDataGenerator.randomElement;
import static no.unit.nva.testutils.RandomDataGenerator.randomString;
import static no.unit.nva.testutils.RandomDataGenerator.randomUri;
import static nva.commons.core.attempt.Try.attempt;
import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.List;
import no.sikt.nva.nvi.common.db.CandidateDao;
import no.sikt.nva.nvi.common.db.CandidateDao.DbCandidate;
import no.sikt.nva.nvi.common.db.CandidateDao.DbInstitutionPoints;
import no.sikt.nva.nvi.common.db.CandidateRepository;
import no.sikt.nva.nvi.common.service.CandidateService;
import no.sikt.nva.nvi.common.validator.SectorCandidateMigrationService;
import no.unit.nva.clients.CustomerDto;
import no.unit.nva.clients.CustomerDto.RightsRetentionStrategy;
import no.unit.nva.clients.CustomerList;
import no.unit.nva.clients.IdentityServiceClient;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class SectorMigrationServiceTest {

  private CandidateService candidateService;
  private CandidateRepository candidateRepository;
  private SectorCandidateMigrationService migrationService;
  private IdentityServiceClient identityServiceClient;

  @BeforeEach
  void setUp() {
    var scenario = new TestScenario();
    candidateService = scenario.getCandidateService();
    candidateRepository = scenario.getCandidateRepository();
    identityServiceClient = mock(IdentityServiceClient.class);
    migrationService = new SectorCandidateMigrationService(candidateService, identityServiceClient);
    setupOpenPeriod(scenario, CURRENT_YEAR);
  }

  @Test
  void shouldUpdateExistingNviCandidateWithoutSectorWithSectorValue() {
    var dbCandidate = candidateWithInstitutionPointsWithoutSector();
    var candidateIdentifier = createCandidateInRepository(candidateRepository, dbCandidate);
    var institutionsToMockInResponse = getInstitutionPoints(dbCandidate);
    mockFetchAllCustomersResponse(institutionsToMockInResponse, false);

    assertThat(dbCandidate.pointCalculation().institutionPoints())
        .allSatisfy(points -> assertThat(points.sector()).isEqualTo(UNKNOWN));

    migrationService.migrateCandidate(candidateIdentifier);

    var migratedCandidate =
        candidateRepository.findCandidateById(candidateIdentifier).orElseThrow();

    assertSectorIsPresentForAllInstitutionPoints(migratedCandidate);
  }

  @Test
  void shouldNotUpdateExistingNviCandidateWhenSectorIsAlreadyMatchingCustomerSector() {
    var dbCandidate = candidateWithInstitutionPointsWithoutSector();
    var candidateIdentifier = createCandidateInRepository(candidateRepository, dbCandidate);
    var institutionsToMockInResponse = getInstitutionPoints(dbCandidate);
    mockFetchAllCustomersResponse(institutionsToMockInResponse, true);

    var candidate = candidateService.getCandidateByIdentifier(candidateIdentifier);

    migrationService.migrateCandidate(candidateIdentifier);

    var migratedCandidate = candidateService.getCandidateByIdentifier(candidateIdentifier);

    assertEquals(candidate, migratedCandidate);
  }

  @Test
  void shouldThrowExceptionWhenFailingFetchingCustomers() {
    var dbCandidate = candidateWithInstitutionPoints();
    var candidateIdentifier = createCandidateInRepository(candidateRepository, dbCandidate);

    when(attempt(identityServiceClient::getAllCustomers).orElseThrow())
        .thenThrow(RuntimeException.class);

    assertThrows(
        RuntimeException.class, () -> migrationService.migrateCandidate(candidateIdentifier));
  }

  private static void assertSectorIsPresentForAllInstitutionPoints(CandidateDao migratedCandidate) {
    assertThat(migratedCandidate.candidate().pointCalculation().institutionPoints())
        .allSatisfy(
            points -> {
              assertThat(points.sector()).isNotNull();
              assertThat(points.sector()).isNotEqualTo(UNKNOWN);
            });
  }

  private void mockFetchAllCustomersResponse(
      List<DbInstitutionPoints> institutionPointsList, boolean customerSectorIsMatching) {
    var customers =
        institutionPointsList.stream()
            .map(
                institutionPoints ->
                    toCustomerWithSector(institutionPoints, customerSectorIsMatching))
            .toList();
    when(attempt(identityServiceClient::getAllCustomers).orElseThrow())
        .thenReturn(new CustomerList(customers));
  }

  private CustomerDto toCustomerWithSector(
      DbInstitutionPoints institutionPoints, boolean customerSectorIsMatching) {
    return new CustomerDto(
        randomUri(),
        randomUUID(),
        randomString(),
        randomString(),
        randomString(),
        institutionPoints.institutionId(),
        randomString(),
        randomBoolean(),
        randomBoolean(),
        randomBoolean(),
        List.of(),
        new RightsRetentionStrategy(randomString(), randomUri()),
        randomBoolean(),
        getSector(institutionPoints, customerSectorIsMatching));
  }

  private static String getSector(
      DbInstitutionPoints institutionPoints, boolean customerSectorIsMatching) {
    return customerSectorIsMatching
        ? institutionPoints.sector().toString()
        : randomElement(UHI, HEALTH, INSTITUTE).toString();
  }

  private static List<DbInstitutionPoints> getInstitutionPoints(DbCandidate dbCandidate) {
    return dbCandidate.pointCalculation().institutionPoints().stream().toList();
  }

  private static DbCandidate candidateWithInstitutionPoints() {
    return randomCandidateBuilder(true)
        .pointCalculation(randomPointCalculation(2).toDbPointCalculation())
        .build();
  }

  private static DbCandidate candidateWithInstitutionPointsWithoutSector() {
    return randomCandidateBuilder(true)
        .pointCalculation(randomPointCalculation(2, null).toDbPointCalculation())
        .build();
  }
}
