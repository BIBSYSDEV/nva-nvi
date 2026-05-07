package no.sikt.nva.nvi.common;

import static no.sikt.nva.nvi.common.db.CandidateDaoFixtures.createCandidateInRepository;
import static no.sikt.nva.nvi.common.db.DbCandidateFixtures.randomCandidateBuilder;
import static no.sikt.nva.nvi.common.db.PeriodRepositoryFixtures.setupOpenPeriod;
import static no.sikt.nva.nvi.common.dto.CustomerDtoFixtures.createCustomer;
import static no.sikt.nva.nvi.common.model.PointCalculationFixtures.randomPointCalculation;
import static no.sikt.nva.nvi.common.model.Sector.UHI;
import static no.sikt.nva.nvi.test.TestUtils.CURRENT_YEAR;
import static nva.commons.core.attempt.Try.attempt;
import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.Collections;
import java.util.List;
import no.sikt.nva.nvi.common.db.CandidateDao.DbCandidate;
import no.sikt.nva.nvi.common.db.CandidateDao.DbInstitutionPoints;
import no.sikt.nva.nvi.common.db.CandidateRepository;
import no.sikt.nva.nvi.common.service.CandidateService;
import no.unit.nva.clients.CustomerList;
import no.unit.nva.clients.IdentityServiceClient;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class RboInstitutionMigrationServiceTest {

  private CandidateService candidateService;
  private CandidateRepository candidateRepository;
  private IdentityServiceClient identityServiceClient;

  @BeforeEach
  void setUp() {
    var scenario = new TestScenario();
    candidateService = scenario.getCandidateService();
    candidateRepository = scenario.getCandidateRepository();
    identityServiceClient = mock(IdentityServiceClient.class);
    setupOpenPeriod(scenario, CURRENT_YEAR);
  }

  @Test
  void shouldUpdateExistingNviCandidateWithRboInstitutionFalseToTrue() {
    var dbCandidate = candidateWithRboInstitutionSetToFalse();
    var candidateIdentifier = createCandidateInRepository(candidateRepository, dbCandidate);
    var institutionPoints = getInstitutionPoints(dbCandidate);
    var service = migrationServiceWithRboCustomers(institutionPoints, true);

    assertThat(dbCandidate.pointCalculation().institutionPoints())
        .allSatisfy(points -> assertThat(points.rboInstitution()).isFalse());

    service.migrateCandidate(candidateIdentifier);

    var migratedCandidate =
        candidateRepository.findCandidateById(candidateIdentifier).orElseThrow();

    assertThat(migratedCandidate.candidate().pointCalculation().institutionPoints())
        .allSatisfy(points -> assertThat(points.rboInstitution()).isTrue());
  }

  @Test
  void shouldNotUpdateExistingNviCandidateWhenRboInstitutionAlreadyMatchingCustomer() {
    var dbCandidate = candidateWithRboInstitutionSetToFalse();
    var candidateIdentifier = createCandidateInRepository(candidateRepository, dbCandidate);
    var institutionPoints = getInstitutionPoints(dbCandidate);
    var service = migrationServiceWithRboCustomers(institutionPoints, false);

    var candidate = candidateService.getCandidateByIdentifier(candidateIdentifier);

    service.migrateCandidate(candidateIdentifier);

    var migratedCandidate = candidateService.getCandidateByIdentifier(candidateIdentifier);

    assertThat(migratedCandidate).isEqualTo(candidate);
  }

  @Test
  void shouldSetRboInstitutionFalseWhenInstitutionIsNotPresentInCustomers() {
    var dbCandidate = candidateWithRboInstitutionSetToFalse();
    var candidateIdentifier = createCandidateInRepository(candidateRepository, dbCandidate);
    var service = migrationServiceWithEmptyCustomers();

    service.migrateCandidate(candidateIdentifier);

    var migratedCandidate =
        candidateRepository.findCandidateById(candidateIdentifier).orElseThrow();

    assertThat(migratedCandidate.candidate().pointCalculation().institutionPoints())
        .allSatisfy(points -> assertThat(points.rboInstitution()).isFalse());
  }

  @Test
  void shouldThrowExceptionWhenFailingFetchingCustomers() {
    when(attempt(identityServiceClient::getAllCustomers).orElseThrow())
        .thenThrow(RuntimeException.class);

    assertThrows(
        RuntimeException.class,
        () -> new RboInstitutionMigrationService(candidateService, identityServiceClient));
  }

  private RboInstitutionMigrationService migrationServiceWithRboCustomers(
      List<DbInstitutionPoints> institutionPointsList, boolean rboInstitution) {
    var customers =
        institutionPointsList.stream()
            .map(points -> createCustomer(points.institutionId(), true, rboInstitution, UHI))
            .toList();
    when(attempt(identityServiceClient::getAllCustomers).orElseThrow())
        .thenReturn(new CustomerList(customers));
    return new RboInstitutionMigrationService(candidateService, identityServiceClient);
  }

  private RboInstitutionMigrationService migrationServiceWithEmptyCustomers() {
    when(attempt(identityServiceClient::getAllCustomers).orElseThrow())
        .thenReturn(new CustomerList(Collections.emptyList()));
    return new RboInstitutionMigrationService(candidateService, identityServiceClient);
  }

  private static List<DbInstitutionPoints> getInstitutionPoints(DbCandidate dbCandidate) {
    return dbCandidate.pointCalculation().institutionPoints().stream().toList();
  }

  private static DbCandidate candidateWithRboInstitutionSetToFalse() {
    return randomCandidateBuilder(true)
        .pointCalculation(randomPointCalculation(2, UHI, false).toDbPointCalculation())
        .build();
  }
}
