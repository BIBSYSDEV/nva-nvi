package no.sikt.nva.nvi.common.db;

import static no.sikt.nva.nvi.common.UpsertRequestFixtures.createUpsertCandidateRequest;
import static no.sikt.nva.nvi.common.db.PeriodRepositoryFixtures.setupOpenPeriod;
import static no.sikt.nva.nvi.common.model.InstanceType.ACADEMIC_ARTICLE;
import static no.sikt.nva.nvi.common.model.InstanceType.ACADEMIC_MONOGRAPH;
import static no.sikt.nva.nvi.common.model.OrganizationFixtures.randomOrganizationId;
import static no.sikt.nva.nvi.common.model.PublicationDateFixtures.randomPublicationDate;
import static no.unit.nva.testutils.RandomDataGenerator.randomString;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatNoException;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.net.URI;
import java.time.Instant;
import java.util.UUID;
import no.sikt.nva.nvi.common.TestScenario;
import no.sikt.nva.nvi.common.UpsertRequestBuilder;
import no.sikt.nva.nvi.common.db.model.Username;
import no.sikt.nva.nvi.common.model.InstanceType;
import no.sikt.nva.nvi.common.model.PublicationDate;
import no.sikt.nva.nvi.common.service.model.ApprovalStatus;
import no.sikt.nva.nvi.common.service.model.Candidate;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import software.amazon.awssdk.services.dynamodb.model.ConditionalCheckFailedException;
import software.amazon.awssdk.services.dynamodb.model.TransactionCanceledException;

/**
 * Tests for scenarios with concurrent read/write operations against DynamoDB, which may cause
 * conflicts. The testing should be done as close to the database as possible, but some scenarios
 * are impractical to recreate with direct table-level access and must be done through
 * business/domain objects.
 */
class ConcurrencyHandlingTests {
  private static final URI ORGANIZATION_1 = randomOrganizationId();
  private static final URI ORGANIZATION_2 = randomOrganizationId();
  private static final PublicationDate PUBLICATION_DATE = randomPublicationDate();
  private TestScenario scenario;
  private CandidateRepository candidateRepository;
  private UpsertRequestBuilder upsertRequestBuilder;
  private UUID candidateIdentifier;
  private Instant testStartedAt;

  @BeforeEach
  void init() {
    testStartedAt = Instant.now();
    scenario = new TestScenario();
    candidateRepository = scenario.getCandidateRepository();
    upsertRequestBuilder =
        createUpsertCandidateRequest(ORGANIZATION_1, ORGANIZATION_2)
            .withInstanceType(InstanceType.ACADEMIC_ARTICLE)
            .withPublicationDate(PUBLICATION_DATE.toDtoPublicationDate());
    candidateIdentifier = prepareCandidateWithNotesAndApprovals();
  }

  @Nested
  @DisplayName("Table-level operations")
  class TableUpdateTests {

    @Test
    void shouldIncrementRevisionOnUpdate() {
      var first = getCandidateDao(candidateIdentifier);
      candidateRepository.candidateTable.putItem(first);
      var second = getCandidateDao(candidateIdentifier);
      assertThat(second.revision()).isNotNull().isGreaterThan(first.revision());
    }

    @Test
    void shouldHandleExistingCandidatesWithoutRevision() {
      // TODO
      var first = getCandidateDao(candidateIdentifier);
    }

    @Test
    void shouldHandleExistingCandidatesWithoutTimestamp() {
      // TODO
    }

    @Test
    void shouldAllowWriteAfterFetch() {
      var first = getCandidateDao(candidateIdentifier);
      candidateRepository.candidateTable.putItem(first);
      var second = getCandidateDao(candidateIdentifier);
      var third = copyAndMutateCandidate(second);
      assertThatNoException().isThrownBy(() -> candidateRepository.candidateTable.putItem(third));
    }

    @Test
    void shouldFailOnConcurrentWrite() {
      var first = getCandidateDao(candidateIdentifier);
      candidateRepository.candidateTable.putItem(first);
      var second = copyAndMutateCandidate(first);
      assertThatThrownBy(() -> candidateRepository.candidateTable.putItem(second))
          .isInstanceOf(ConditionalCheckFailedException.class)
          .hasMessageContaining("The conditional request failed");
    }

    @Test
    void shouldHaveLastWrittenTimestamp() {
      var candidateDao = getCandidateDao(candidateIdentifier);
      assertThat(candidateDao.lastWrittenAt())
          .isNotNull()
          .isAfterOrEqualTo(testStartedAt)
          .isBeforeOrEqualTo(Instant.now());
    }

    @Test
    void shouldUpdateLastWrittenTimestampForCandidate() {
      var first = getCandidateDao(candidateIdentifier);
      candidateRepository.candidateTable.putItem(first);
      var second = getCandidateDao(candidateIdentifier);
      assertThat(second.lastWrittenAt())
          .isNotNull()
          .isAfter(first.lastWrittenAt())
          .isBeforeOrEqualTo(Instant.now());
    }

    @Test
    void shouldUpdateLastWrittenTimestampForApprovals() {
      var initialState = scenario.getAllRelatedData(candidateIdentifier);
      var first = initialState.approvals().getFirst();
      var organization = first.approvalStatus().institutionId();

      var checkpoint = Instant.now();
      candidateRepository.approvalStatusTable.putItem(copyAndMutateApproval(first));

      var updatedState = scenario.getAllRelatedData(candidateIdentifier);
      var second =
          updatedState.approvals().stream()
              .filter(dao -> organization.equals(dao.approvalStatus().institutionId()))
              .findFirst()
              .orElseThrow();

      assertThat(second.lastWrittenAt())
          .isNotNull()
          .isAfter(checkpoint)
          .isBeforeOrEqualTo(Instant.now());
    }
  }

  @Nested
  @DisplayName("Repository-level operations")
  class RepositoryUpdateTests {

    @Test
    void shouldNotChangeVersionOnConsecutiveReads() {
      var first = getCandidateDao(candidateIdentifier);
      var second = getCandidateDao(candidateIdentifier);
      assertThat(first)
          .extracting(CandidateDao::lastWrittenAt, CandidateDao::revision, CandidateDao::version)
          .containsExactly(second.lastWrittenAt(), second.revision(), second.version());
    }

    @Test
    void shouldHaveDefaultRevisionOnCreate() {
      var candidateDao = getCandidateDao(candidateIdentifier);
      assertThat(candidateDao.revision()).isNotNull().isOne();
    }
  }

  @Nested
  @DisplayName("Domain-level operations")
  class DomainObjectUpdateTests {

    @Test
    void shouldAllowConcurrentWritesOfSeparateApprovals() {
      var readCandidate = scenario.getCandidateByIdentifier(candidateIdentifier);
      assertThatNoException()
          .isThrownBy(() -> changeApprovalStatusForOrganization(readCandidate, ORGANIZATION_1));
      assertThatNoException()
          .isThrownBy(() -> changeApprovalStatusForOrganization(readCandidate, ORGANIZATION_2));
    }

    @Test
    void shouldAllowWriteAfterFetch() {
      var readCandidate1 = scenario.getCandidateByIdentifier(candidateIdentifier);
      var originalStatus = readCandidate1.getApprovalStatus(ORGANIZATION_1);
      var firstUpdate = getOtherStatus(originalStatus);
      var secondUpdate = getOtherStatus(firstUpdate);

      scenario.updateApprovalStatusDangerously(readCandidate1, firstUpdate, ORGANIZATION_1);
      var readCandidate2 = scenario.getCandidateByIdentifier(candidateIdentifier);

      assertThatNoException()
          .isThrownBy(
              () ->
                  scenario.updateApprovalStatusDangerously(
                      readCandidate2, secondUpdate, ORGANIZATION_1));
    }

    @Test
    void shouldFailOnConcurrentWrite() {
      var readCandidate = scenario.getCandidateByIdentifier(candidateIdentifier);
      var originalStatus = readCandidate.getApprovalStatus(ORGANIZATION_1);
      var firstUpdate = getOtherStatus(originalStatus);
      var secondUpdate = getOtherStatus(firstUpdate);

      scenario.updateApprovalStatusDangerously(readCandidate, firstUpdate, ORGANIZATION_1);

      assertThatThrownBy(
              () ->
                  scenario.updateApprovalStatusDangerously(
                      readCandidate, secondUpdate, ORGANIZATION_1))
          .isInstanceOf(ConditionalCheckFailedException.class)
          .hasMessageContaining("The conditional request failed");
    }

    @Test
    void shouldFailOnWriteAfterCandidateUpdate() {
      var readCandidate = scenario.getCandidateByIdentifier(candidateIdentifier);
      updateCandidate(readCandidate);

      var originalStatus = readCandidate.getApprovalStatus(ORGANIZATION_1);
      var newStatus = getOtherStatus(originalStatus);
      assertThatThrownBy(
              () ->
                  scenario.updateApprovalStatusDangerously(
                      readCandidate, newStatus, ORGANIZATION_1))
          .isInstanceOf(TransactionCanceledException.class)
          .hasMessageContaining("Transaction cancelled due to version conflict");
    }
  }

  /**
   * Set up a Candidate in a valid state, with Notes, Approvals and an open Period.
   *
   * @return identifier of the Candidate
   */
  private UUID prepareCandidateWithNotesAndApprovals() {
    setupOpenPeriod(scenario, PUBLICATION_DATE.year());
    var candidate = scenario.upsertCandidate(upsertRequestBuilder.build());
    var candidateId = candidate.getIdentifier();

    scenario.updateApprovalStatus(candidateId, ApprovalStatus.APPROVED, ORGANIZATION_1);
    scenario.updateApprovalStatus(candidateId, ApprovalStatus.REJECTED, ORGANIZATION_2);
    scenario.createNote(candidateId, "Note 1", ORGANIZATION_1);
    scenario.createNote(candidateId, "Note 2", ORGANIZATION_2);
    scenario.createNote(candidateId, "Note 3", ORGANIZATION_1);

    return candidateId;
  }

  private CandidateDao getCandidateDao(UUID candidateIdentifier) {
    return scenario.getCandidateRepository().findCandidateById(candidateIdentifier).orElseThrow();
  }

  /** Copy and mutate a CandidateDao so that the data is different. */
  private CandidateDao copyAndMutateCandidate(CandidateDao original) {
    var originalCreatorCount = original.candidate().creatorCount();
    var updatedData = original.candidate().copy().creatorCount(originalCreatorCount + 1).build();
    return original.copy().candidate(updatedData).build();
  }

  /** Copy and mutate an ApprovalStatusDao so that the data is different. */
  private ApprovalStatusDao copyAndMutateApproval(ApprovalStatusDao original) {
    var updatedUsername = new Username(randomString());
    var updatedDbApprovalStatus =
        original.approvalStatus().copy().assignee(updatedUsername).build();
    return original.copy().approvalStatus(updatedDbApprovalStatus).build();
  }

  /** Force an upsert of a Candidate by changing the publication instance type. */
  private void updateCandidate(Candidate candidate) {
    var oldType = candidate.getPublicationType();
    var newType = ACADEMIC_MONOGRAPH.equals(oldType) ? ACADEMIC_ARTICLE : ACADEMIC_MONOGRAPH;
    scenario.upsertCandidate(upsertRequestBuilder.withInstanceType(newType).build());
  }

  private void changeApprovalStatusForOrganization(Candidate readCandidate, URI organization) {
    var originalStatus = readCandidate.getApprovalStatus(organization);
    var newStatus = getOtherStatus(originalStatus);
    scenario.updateApprovalStatusDangerously(readCandidate, newStatus, organization);
  }

  private static ApprovalStatus getOtherStatus(ApprovalStatus originalStatus) {
    return ApprovalStatus.APPROVED.equals(originalStatus)
        ? ApprovalStatus.PENDING
        : ApprovalStatus.REJECTED;
  }
}
