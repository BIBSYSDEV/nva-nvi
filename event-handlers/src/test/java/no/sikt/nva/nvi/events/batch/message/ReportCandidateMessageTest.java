package no.sikt.nva.nvi.events.batch.message;

import static no.sikt.nva.nvi.common.UpsertRequestFixtures.createUpsertCandidateRequestWithSingleAffiliation;
import static no.sikt.nva.nvi.common.db.PeriodRepositoryFixtures.setupClosedPeriod;
import static no.sikt.nva.nvi.common.db.PeriodRepositoryFixtures.setupOpenPeriod;
import static no.sikt.nva.nvi.common.model.OrganizationFixtures.randomOrganizationId;
import static no.sikt.nva.nvi.test.TestUtils.CURRENT_YEAR;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.net.URI;
import java.time.Instant;
import java.util.UUID;
import no.sikt.nva.nvi.common.TestScenario;
import no.sikt.nva.nvi.common.service.CandidateService;
import no.sikt.nva.nvi.common.service.exception.IllegalCandidateUpdateException;
import no.sikt.nva.nvi.common.service.model.ApprovalStatus;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class ReportCandidateMessageTest {

  private TestScenario scenario;
  private CandidateService candidateService;

  @BeforeEach
  void setUp() {
    scenario = new TestScenario();
    candidateService = scenario.getCandidateService();
    setupOpenPeriod(scenario, CURRENT_YEAR);
  }

  @Test
  void shouldReportApprovedCandidateInClosedPeriod() {
    var institution = randomOrganizationId();
    var candidateId = createApprovedCandidate(institution);
    setupClosedPeriod(scenario, CURRENT_YEAR);

    var message = new ReportCandidateMessage(candidateId);
    message.execute(candidateService);

    var reportedCandidate = candidateService.getCandidateByIdentifier(candidateId);
    assertThat(reportedCandidate.isReported()).isTrue();
  }

  @Test
  void shouldUseCurrentTimeWhenSettingReportedDate() {
    var institution = randomOrganizationId();
    var candidateId = createApprovedCandidate(institution);
    setupClosedPeriod(scenario, CURRENT_YEAR);

    var startTime = Instant.now();
    var message = new ReportCandidateMessage(candidateId);
    message.execute(candidateService);
    var endTime = Instant.now();

    var reportedCandidate = candidateService.getCandidateByIdentifier(candidateId);
    assertThat(reportedCandidate.reportedDate()).isBetween(startTime, endTime);
  }

  @Test
  void shouldThrowWhenCandidateIsNotApproved() {
    var institution = randomOrganizationId();
    var request = createUpsertCandidateRequestWithSingleAffiliation(institution, institution);
    var candidate = scenario.upsertCandidate(request);
    setupClosedPeriod(scenario, CURRENT_YEAR);

    var message = new ReportCandidateMessage(candidate.identifier());
    assertThatThrownBy(() -> message.execute(candidateService))
        .isInstanceOf(IllegalCandidateUpdateException.class);
  }

  @Test
  void shouldThrowWhenPeriodIsNotClosed() {
    var institution = randomOrganizationId();
    var candidateId = createApprovedCandidate(institution);

    var message = new ReportCandidateMessage(candidateId);
    assertThatThrownBy(() -> message.execute(candidateService))
        .isInstanceOf(IllegalCandidateUpdateException.class);
  }

  @Test
  void shouldThrowWhenCandidateIsAlreadyReported() {
    var institution = randomOrganizationId();
    var candidateId = createApprovedCandidate(institution);
    setupClosedPeriod(scenario, CURRENT_YEAR);

    var message = new ReportCandidateMessage(candidateId);
    message.execute(candidateService);

    assertThatThrownBy(() -> message.execute(candidateService))
        .isInstanceOf(IllegalCandidateUpdateException.class);
  }

  private UUID createApprovedCandidate(URI institution) {
    var request = createUpsertCandidateRequestWithSingleAffiliation(institution, institution);
    var candidate = scenario.upsertCandidate(request);
    scenario.updateApprovalStatus(candidate.identifier(), ApprovalStatus.APPROVED, institution);
    return candidate.identifier();
  }
}
