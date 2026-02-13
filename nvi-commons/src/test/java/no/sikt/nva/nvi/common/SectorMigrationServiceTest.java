package no.sikt.nva.nvi.common;

import static no.sikt.nva.nvi.common.db.CandidateDaoFixtures.createCandidateInRepository;
import static no.sikt.nva.nvi.common.db.DbCandidateFixtures.randomCandidateBuilder;
import static no.sikt.nva.nvi.common.db.PeriodRepositoryFixtures.setupOpenPeriod;
import static no.sikt.nva.nvi.common.dto.CustomerDtoFixtures.createCustomer;
import static no.sikt.nva.nvi.common.model.PointCalculationFixtures.randomPointCalculation;
import static no.sikt.nva.nvi.common.model.Sector.HEALTH;
import static no.sikt.nva.nvi.common.model.Sector.INSTITUTE;
import static no.sikt.nva.nvi.common.model.Sector.UHI;
import static no.sikt.nva.nvi.common.model.Sector.UNKNOWN;
import static no.sikt.nva.nvi.test.TestUtils.CURRENT_YEAR;
import static no.unit.nva.testutils.RandomDataGenerator.randomBoolean;
import static no.unit.nva.testutils.RandomDataGenerator.randomElement;
import static nva.commons.core.attempt.Try.attempt;
import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.Collections;
import java.util.List;
import no.sikt.nva.nvi.common.db.CandidateDao;
import no.sikt.nva.nvi.common.db.CandidateDao.DbCandidate;
import no.sikt.nva.nvi.common.db.CandidateDao.DbInstitutionPoints;
import no.sikt.nva.nvi.common.db.CandidateRepository;
import no.sikt.nva.nvi.common.model.Sector;
import no.sikt.nva.nvi.common.service.CandidateService;
import no.sikt.nva.nvi.common.validator.SectorMigrationService;
import no.unit.nva.clients.CustomerDto;
import no.unit.nva.clients.CustomerList;
import no.unit.nva.clients.IdentityServiceClient;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class SectorMigrationServiceTest {

  private CandidateService candidateService;
  private CandidateRepository candidateRepository;
  private IdentityServiceClient identityServiceClient;
  private SectorMigrationService service;

  @BeforeEach
  void setUp() {
    var scenario = new TestScenario();
    candidateService = scenario.getCandidateService();
    candidateRepository = scenario.getCandidateRepository();
    identityServiceClient = mock(IdentityServiceClient.class);
    service = new SectorMigrationService(candidateService, identityServiceClient);
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

    service.migrateCandidate(candidateIdentifier);

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

    service.migrateCandidate(candidateIdentifier);

    var migratedCandidate = candidateService.getCandidateByIdentifier(candidateIdentifier);

    assertEquals(candidate, migratedCandidate);
  }

  @Test
  void shouldSetUnknownSectorWhenInstitutionIsNotPresentInCustomers() {
    var dbCandidate = candidateWithInstitutionPointsWithoutSector();
    var candidateIdentifier = createCandidateInRepository(candidateRepository, dbCandidate);
    mockMigrationServiceWithoutCustomers();

    service.migrateCandidate(candidateIdentifier);

    var migratedCandidate =
        candidateRepository.findCandidateById(candidateIdentifier).orElseThrow();

    assertAllPointsAreSetToUnknown(migratedCandidate);
  }

  private static void assertAllPointsAreSetToUnknown(CandidateDao migratedCandidate) {
    assertThat(migratedCandidate.candidate().pointCalculation().institutionPoints())
        .allSatisfy(
            points -> {
              assertThat(points.sector()).isNotNull();
              assertThat(points.sector()).isEqualTo(UNKNOWN);
            });
  }

  private void mockMigrationServiceWithoutCustomers() {
    when(attempt(identityServiceClient::getAllCustomers).orElseThrow())
        .thenReturn(new CustomerList(Collections.emptyList()));
  }

  @Test
  void shouldThrowExceptionWhenFailingFetchingCustomers() {
    var dbCandidate = candidateWithInstitutionPointsWithoutSector();
    var candidateIdentifier = createCandidateInRepository(candidateRepository, dbCandidate);

    when(attempt(identityServiceClient::getAllCustomers).orElseThrow())
        .thenThrow(RuntimeException.class);

    assertThrows(RuntimeException.class, () -> service.migrateCandidate(candidateIdentifier));
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
    return createCustomer(
        institutionPoints.institutionId(),
        randomBoolean(),
        getSector(institutionPoints, customerSectorIsMatching));
  }

  private static Sector getSector(
      DbInstitutionPoints institutionPoints, boolean customerSectorIsMatching) {
    return customerSectorIsMatching
        ? institutionPoints.sector()
        : randomElement(UHI, HEALTH, INSTITUTE);
  }

  private static List<DbInstitutionPoints> getInstitutionPoints(DbCandidate dbCandidate) {
    return dbCandidate.pointCalculation().institutionPoints().stream().toList();
  }

  private static DbCandidate candidateWithInstitutionPointsWithoutSector() {
    return randomCandidateBuilder(true)
        .pointCalculation(randomPointCalculation(2, null).toDbPointCalculation())
        .build();
  }
}
