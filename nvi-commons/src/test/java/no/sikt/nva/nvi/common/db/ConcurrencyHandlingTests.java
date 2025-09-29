package no.sikt.nva.nvi.common.db;

import static java.util.Collections.emptyList;
import static no.sikt.nva.nvi.common.UpsertRequestFixtures.createUpdateStatusRequest;
import static no.sikt.nva.nvi.common.UpsertRequestFixtures.createUpsertCandidateRequest;
import static no.sikt.nva.nvi.common.db.PeriodRepositoryFixtures.setupOpenPeriod;
import static no.sikt.nva.nvi.common.model.InstanceType.ACADEMIC_ARTICLE;
import static no.sikt.nva.nvi.common.model.InstanceType.ACADEMIC_MONOGRAPH;
import static no.sikt.nva.nvi.common.model.OrganizationFixtures.randomOrganizationId;
import static no.sikt.nva.nvi.common.model.PublicationDateFixtures.randomPublicationDate;
import static no.sikt.nva.nvi.common.model.UserInstanceFixtures.createCuratorUserInstance;
import static no.unit.nva.testutils.RandomDataGenerator.randomString;
import static no.unit.nva.testutils.RandomDataGenerator.randomUri;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatNoException;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.net.URI;
import java.time.Instant;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import no.sikt.nva.nvi.common.TestScenario;
import no.sikt.nva.nvi.common.UpsertRequestBuilder;
import no.sikt.nva.nvi.common.client.model.Organization;
import no.sikt.nva.nvi.common.db.model.Username;
import no.sikt.nva.nvi.common.exceptions.TransactionException;
import no.sikt.nva.nvi.common.model.PublicationDate;
import no.sikt.nva.nvi.common.service.model.ApprovalStatus;
import no.sikt.nva.nvi.common.service.model.Candidate;
import org.assertj.core.api.ThrowableAssert;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import software.amazon.awssdk.services.dynamodb.model.AttributeValue;
import software.amazon.awssdk.services.dynamodb.model.ConditionalCheckFailedException;
import software.amazon.awssdk.services.dynamodb.model.UpdateItemRequest;

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
            .withInstanceType(ACADEMIC_ARTICLE)
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
    void shouldAllowWriteAfterFetch() {
      var first = getCandidateDao(candidateIdentifier);
      candidateRepository.candidateTable.putItem(first);
      var second = getCandidateDao(candidateIdentifier);
      var third = copyAndMutateCandidate(second);
      assertThatNoException().isThrownBy(() -> candidateRepository.candidateTable.putItem(third));
    }

    @Test
    void shouldFailOnConcurrentWriteOfCandidate() {
      var first = getCandidateDao(candidateIdentifier);
      candidateRepository.candidateTable.putItem(first);
      var second = copyAndMutateCandidate(first);
      assertThatThrownBy(() -> candidateRepository.candidateTable.putItem(second))
          .isInstanceOf(ConditionalCheckFailedException.class)
          .hasMessageContaining("The conditional request failed");
    }

    @Test
    void shouldFailOnDuplicateWriteOfApproval() {
      var initialState = scenario.getAllRelatedData(candidateIdentifier);
      var first = initialState.approvals().getFirst();

      var updatedApproval = copyAndMutateApproval(first);
      candidateRepository.approvalStatusTable.putItem(updatedApproval);

      assertThatThrownBy(() -> candidateRepository.approvalStatusTable.putItem(updatedApproval))
          .isInstanceOf(ConditionalCheckFailedException.class)
          .hasMessageContaining("The conditional request failed");
    }

    @Test
    void shouldFailOnWriteOfApprovalForUnknownCandidate() {
      var initialState = scenario.getAllRelatedData(candidateIdentifier);
      var first = initialState.approvals().getFirst();

      var updatedApproval =
          copyAndMutateApproval(first).copy().identifier(UUID.randomUUID()).build();

      assertThatThrownBy(() -> candidateRepository.approvalStatusTable.putItem(updatedApproval))
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
    void shouldHandleExistingCandidatesWithoutRevision() {
      removeRevisionFromCandidate(candidateIdentifier);
      var first = scenario.getCandidateByIdentifier(candidateIdentifier);
      assertThat(first.getRevisionRead()).isNull();

      candidateRepository.candidateTable.putItem(getCandidateDao(candidateIdentifier));
      var second = scenario.getCandidateByIdentifier(candidateIdentifier);

      assertThat(second.getRevisionRead()).isEqualTo(1L);
    }

    @Test
    void shouldHandleExistingCandidatesWithoutTimestamp() {
      removeTimestampFromCandidate(candidateIdentifier);
      var first = getCandidateDao(candidateIdentifier);
      assertThat(first.lastWrittenAt()).isNull();

      var beforeWrite = Instant.now();
      candidateRepository.candidateTable.putItem(first);
      var afterWrite = Instant.now();

      var second = getCandidateDao(candidateIdentifier);
      assertThat(second.lastWrittenAt()).isBetween(beforeWrite, afterWrite);
    }

    @Test
    void shouldHandleExistingCandidatesWithoutRevisionOrTimestamp() {
      removeRevisionFromCandidate(candidateIdentifier);
      removeTimestampFromCandidate(candidateIdentifier);
      var first = getCandidateDao(candidateIdentifier);
      assertThat(first.revision()).isNull();
      assertThat(first.lastWrittenAt()).isNull();

      var beforeWrite = Instant.now();
      candidateRepository.candidateTable.putItem(first);
      var afterWrite = Instant.now();

      var secondDao = getCandidateDao(candidateIdentifier);
      assertThat(secondDao.lastWrittenAt()).isBetween(beforeWrite, afterWrite);

      var second = scenario.getCandidateByIdentifier(candidateIdentifier);
      assertThat(second.getRevisionRead()).isEqualTo(1L);
    }

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
    void shouldAllowSequentialUpsertRequests() {
      var firstRequest = upsertRequestBuilder.withPublicationBucketUri(randomUri()).build();
      var secondRequest = upsertRequestBuilder.withPublicationBucketUri(randomUri()).build();

      var first = scenario.upsertCandidate(firstRequest);
      var second = scenario.upsertCandidate(secondRequest);

      assertThat(first.getIdentifier()).isEqualTo(second.getIdentifier());
      assertThat(first.getPublicationDetails().publicationBucketUri())
          .isNotEqualTo(second.getPublicationDetails().publicationBucketUri());
    }

    @Test
    void shouldAllowWriteAfterFetch() {
      var readCandidate1 =
          scenario.updateApprovalStatus(
              candidateIdentifier, ApprovalStatus.PENDING, ORGANIZATION_1);

      var user = createCuratorUserInstance(ORGANIZATION_1);
      var firstUpdate = createUpdateStatusRequest(ApprovalStatus.APPROVED, user);
      var secondUpdate = createUpdateStatusRequest(ApprovalStatus.APPROVED, user);

      assertThatNoException()
          .isThrownBy(
              () -> {
                readCandidate1.updateApprovalStatus(firstUpdate, user);
                var readCandidate2 = scenario.getCandidateByIdentifier(candidateIdentifier);
                readCandidate2.updateApprovalStatus(secondUpdate, user);
              });
    }

    @Test
    void shouldAllowConcurrentWritesOfSeparateApprovals() {
      var candidate =
          scenario.updateApprovalStatus(
              candidateIdentifier, ApprovalStatus.PENDING, ORGANIZATION_1);

      var firstUser = createCuratorUserInstance(ORGANIZATION_1);
      var firstUpdate = createUpdateStatusRequest(ApprovalStatus.APPROVED, firstUser);
      var secondUser = createCuratorUserInstance(ORGANIZATION_2);
      var secondUpdate = createUpdateStatusRequest(ApprovalStatus.APPROVED, secondUser);

      assertThatNoException()
          .isThrownBy(
              () -> {
                candidate.updateApprovalStatus(firstUpdate, firstUser);
                candidate.updateApprovalStatus(secondUpdate, secondUser);
              });
    }

    @Test
    void shouldFailOnConcurrentConflictingChangeOfApproval() {
      var candidate =
          scenario.updateApprovalStatus(
              candidateIdentifier, ApprovalStatus.PENDING, ORGANIZATION_1);

      var firstUser = createCuratorUserInstance(ORGANIZATION_1);
      var firstUpdate = createUpdateStatusRequest(ApprovalStatus.APPROVED, firstUser);
      var secondUser = createCuratorUserInstance(ORGANIZATION_1);
      var secondUpdate = createUpdateStatusRequest(ApprovalStatus.APPROVED, secondUser);

      assertThatNoException()
          .isThrownBy(() -> candidate.updateApprovalStatus(firstUpdate, firstUser));
      assertThrowsConcurrencyException(
          () -> candidate.updateApprovalStatus(secondUpdate, secondUser));
    }

    @Test
    void shouldFailOnConcurrentDuplicateChangeOfApproval() {
      var user = createCuratorUserInstance(ORGANIZATION_1);
      var candidate =
          scenario.updateApprovalStatus(
              candidateIdentifier, ApprovalStatus.PENDING, ORGANIZATION_1);
      var updateRequest = createUpdateStatusRequest(ApprovalStatus.APPROVED, user);

      assertThatNoException().isThrownBy(() -> candidate.updateApprovalStatus(updateRequest, user));
      assertThrowsConcurrencyException(() -> candidate.updateApprovalStatus(updateRequest, user));
    }

    @Test
    void shouldFailOnWriteAfterCandidateUpdate() {
      var readCandidate = scenario.getCandidateByIdentifier(candidateIdentifier);
      updateCandidate(readCandidate);
      var originalStatus = readCandidate.getApprovalStatus(ORGANIZATION_1);
      var newStatus = getOtherStatus(originalStatus);

      var user = createCuratorUserInstance(ORGANIZATION_1);
      var updateRequest = createUpdateStatusRequest(newStatus, user);

      assertThrowsConcurrencyException(
          () -> readCandidate.updateApprovalStatus(updateRequest, user));
    }

    @Test
    void shouldNotFailWhenUpdatingApprovalForCandidateWithNullRevision() {
      removeRevisionFromCandidate(candidateIdentifier);

      var legacyCandidate = scenario.getCandidateByIdentifier(candidateIdentifier);
      var user = createCuratorUserInstance(ORGANIZATION_1);
      var originalStatus = legacyCandidate.getApprovalStatus(ORGANIZATION_1);
      var newStatus = getOtherStatus(originalStatus);
      var updateRequest = createUpdateStatusRequest(newStatus, user);

      assertThatNoException()
          .isThrownBy(() -> legacyCandidate.updateApprovalStatus(updateRequest, user));
    }

    @Disabled
    @ParameterizedTest
    @ValueSource(ints = {1, 75, 150, 250})
    void shouldBeAbleToUpsertCandidateWithManyApprovals(int numberOfNviOrganizations) {
      var organizations = createOrganizations(numberOfNviOrganizations);
      var request =
          createUpsertCandidateRequest(organizations)
              .withInstanceType(ACADEMIC_ARTICLE)
              .withPublicationDate(PUBLICATION_DATE.toDtoPublicationDate())
              .build();

      assertThatNoException().isThrownBy(() -> scenario.upsertCandidate(request));
    }

    @Disabled
    @ParameterizedTest
    @ValueSource(ints = {1, 75, 150, 250})
    void shouldBeAbleToResetCandidateWithManyApprovals(int numberOfNviOrganizations) {
      var organizations = createOrganizations(numberOfNviOrganizations);
      var requestBuilder =
          createUpsertCandidateRequest(organizations)
              .withInstanceType(ACADEMIC_ARTICLE)
              .withPublicationDate(PUBLICATION_DATE.toDtoPublicationDate());
      var candidateId = scenario.upsertCandidate(requestBuilder.build()).getIdentifier();

      var approvingOrganization = organizations.iterator().next().id();
      scenario.updateApprovalStatus(candidateId, ApprovalStatus.APPROVED, approvingOrganization);

      var updateRequest = requestBuilder.withInstanceType(ACADEMIC_MONOGRAPH).build();
      var updatedCandidate = scenario.upsertCandidate(updateRequest);
      assertThat(updatedCandidate.getApprovalStatus(approvingOrganization))
          .isEqualTo(ApprovalStatus.PENDING);
    }

    @Test
    void shouldBeAbleToResetCandidateWithNoApprovals() {
      var requestBuilder =
          createUpsertCandidateRequest(emptyList())
              .withInstanceType(ACADEMIC_ARTICLE)
              .withPublicationDate(PUBLICATION_DATE.toDtoPublicationDate());
      var originalCandidate = scenario.upsertCandidate(requestBuilder.build());

      var updateRequest = requestBuilder.withInstanceType(ACADEMIC_MONOGRAPH).build();
      var updatedCandidate = scenario.upsertCandidate(updateRequest);
      assertThat(updatedCandidate.getRevisionRead())
          .isEqualTo(originalCandidate.getRevisionRead() + 1);
      assertThat(updatedCandidate.getApprovals().size()).isZero();
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

  private Set<Organization> createOrganizations(int numberOfNviOrganizations) {
    return IntStream.range(0, numberOfNviOrganizations)
        .mapToObj(i -> randomOrganizationId())
        .map(Organization.builder()::withId)
        .map(Organization.Builder::build)
        .collect(Collectors.toSet());
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

  private static ApprovalStatus getOtherStatus(ApprovalStatus originalStatus) {
    return ApprovalStatus.APPROVED.equals(originalStatus)
        ? ApprovalStatus.PENDING
        : ApprovalStatus.REJECTED;
  }

  private static void assertThrowsConcurrencyException(ThrowableAssert.ThrowingCallable operation) {
    assertThatThrownBy(operation)
        .isInstanceOf(TransactionException.class)
        .hasMessageContaining("condition revision = :expectedCandidateRevision")
        .hasMessageContaining("ConditionalCheckFailed");
  }

  private void removeRevisionFromCandidate(UUID candidateId) {
    var initialCandidate = getCandidateDao(candidateId);
    updateDirectlyWithLowLevelClient(initialCandidate, "REMOVE revision");

    var legacyCandidate = getCandidateDao(candidateId);
    assertThat(legacyCandidate.revision()).isNull();
  }

  private void removeTimestampFromCandidate(UUID candidateId) {
    var initialCandidate = getCandidateDao(candidateId);
    updateDirectlyWithLowLevelClient(initialCandidate, "REMOVE lastWrittenAt");

    var legacyCandidate = getCandidateDao(candidateId);
    assertThat(legacyCandidate.lastWrittenAt()).isNull();
  }

  /**
   * Updates a CandidateDao directly using the low-level defaultClient, bypassing safeguards and
   * rules in the EnhancedClient and repository.
   */
  private void updateDirectlyWithLowLevelClient(CandidateDao current, String updateExpression) {
    candidateRepository.defaultClient.updateItem(
        UpdateItemRequest.builder()
            .tableName(System.getenv("NVI_TABLE_NAME"))
            .key(
                Map.of(
                    "PrimaryKeyHashKey",
                    AttributeValue.builder().s(current.primaryKeyHashKey()).build(),
                    "PrimaryKeyRangeKey",
                    AttributeValue.builder().s(current.primaryKeyRangeKey()).build()))
            .updateExpression(updateExpression)
            .build());
  }
}
