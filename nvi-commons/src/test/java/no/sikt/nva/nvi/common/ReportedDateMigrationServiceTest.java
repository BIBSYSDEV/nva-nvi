package no.sikt.nva.nvi.common;

import static no.sikt.nva.nvi.common.db.CandidateDaoFixtures.createCandidateInRepository;
import static no.sikt.nva.nvi.common.db.DbCandidateFixtures.randomCandidateBuilder;
import static no.sikt.nva.nvi.common.db.PeriodRepositoryFixtures.setupOpenPeriod;
import static no.sikt.nva.nvi.test.TestUtils.CURRENT_YEAR;
import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.util.UUID;
import no.sikt.nva.nvi.common.db.CandidateRepository;
import no.sikt.nva.nvi.common.db.ReportStatus;
import no.sikt.nva.nvi.common.service.CandidateService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class ReportedDateMigrationServiceTest {

  private CandidateService candidateService;
  private CandidateRepository candidateRepository;
  private ReportedDateMigrationService migrationService;

  @BeforeEach
  void setUp() {
    var scenario = new TestScenario();
    candidateService = scenario.getCandidateService();
    candidateRepository = scenario.getCandidateRepository();
    migrationService = new ReportedDateMigrationService(candidateService);
    setupOpenPeriod(scenario, CURRENT_YEAR);
  }

  @Test
  void shouldSetReportedDateBasedOnPeriodIfMissingFromReportedCandidate() {
    var candidateId = createReportedCandidateWithoutReportedDate();

    migrationService.migrateCandidate(candidateId);

    var updatedCandidate = candidateService.getCandidateByIdentifier(candidateId);
    assertThat(updatedCandidate.reportedDate())
        .isEqualTo(updatedCandidate.period().reportingDate());
  }

  @Test
  void shouldNotModifyCandidateAlreadyHavingReportedDate() {
    var existingReportedDate = Instant.parse("2024-01-15T10:00:00Z");
    var candidateId = createReportedCandidateWithReportedDate(existingReportedDate);

    migrationService.migrateCandidate(candidateId);

    var candidate = candidateService.getCandidateByIdentifier(candidateId);
    assertThat(candidate.reportedDate()).isEqualTo(existingReportedDate);
  }

  @Test
  void shouldNotModifyNonReportedCandidate() {
    var candidateId = createNonReportedCandidate();

    migrationService.migrateCandidate(candidateId);

    var candidate = candidateService.getCandidateByIdentifier(candidateId);
    assertThat(candidate.reportedDate()).isNull();
    assertThat(candidate.reportStatus()).isNull();
  }

  @Test
  void shouldUpdateModifiedDateWhenMigrating() {
    var candidateId = createReportedCandidateWithoutReportedDate();
    var candidateBeforeMigration = candidateService.getCandidateByIdentifier(candidateId);

    migrationService.migrateCandidate(candidateId);

    var updatedCandidate = candidateService.getCandidateByIdentifier(candidateId);
    assertThat(updatedCandidate.modifiedDate())
        .isAfterOrEqualTo(candidateBeforeMigration.modifiedDate());
  }

  private UUID createReportedCandidateWithoutReportedDate() {
    var dbCandidate =
        randomCandidateBuilder(true).reportStatus(ReportStatus.REPORTED).reportedDate(null).build();
    return createCandidateInRepository(candidateRepository, dbCandidate);
  }

  private UUID createReportedCandidateWithReportedDate(Instant reportedDate) {
    var dbCandidate =
        randomCandidateBuilder(true)
            .reportStatus(ReportStatus.REPORTED)
            .reportedDate(reportedDate)
            .build();
    return createCandidateInRepository(candidateRepository, dbCandidate);
  }

  private UUID createNonReportedCandidate() {
    var dbCandidate = randomCandidateBuilder(true).reportStatus(null).reportedDate(null).build();
    return createCandidateInRepository(candidateRepository, dbCandidate);
  }
}
