package no.sikt.nva.nvi.common.service;

import static no.sikt.nva.nvi.test.TestUtils.createUpdateStatusRequest;
import static no.sikt.nva.nvi.test.TestUtils.createUpsertCandidateRequest;
import static no.sikt.nva.nvi.test.TestUtils.periodRepositoryReturningClosedPeriod;
import static no.sikt.nva.nvi.test.TestUtils.periodRepositoryReturningNotOpenedPeriod;
import static no.sikt.nva.nvi.test.TestUtils.periodRepositoryReturningOpenedPeriod;
import static no.sikt.nva.nvi.test.TestUtils.randomBigDecimal;
import static no.unit.nva.testutils.RandomDataGenerator.randomString;
import static no.unit.nva.testutils.RandomDataGenerator.randomUri;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.nullValue;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import java.net.URI;
import java.time.ZonedDateTime;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import no.sikt.nva.nvi.common.db.ApprovalStatusDao.DbStatus;
import no.sikt.nva.nvi.common.db.CandidateRepository;
import no.sikt.nva.nvi.common.db.PeriodRepository;
import no.sikt.nva.nvi.common.db.model.InstanceType;
import no.sikt.nva.nvi.common.model.InvalidNviCandidateException;
import no.sikt.nva.nvi.common.model.UpdateAssigneeRequest;
import no.sikt.nva.nvi.common.model.UpdateStatusRequest;
import no.sikt.nva.nvi.common.service.dto.ApprovalStatus;
import no.sikt.nva.nvi.common.service.dto.NviApprovalStatus;
import no.sikt.nva.nvi.test.LocalDynamoTest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.MethodSource;

public class CandidateBOApprovalStatusTest extends LocalDynamoTest {

    private CandidateRepository candidateRepository;
    private PeriodRepository periodRepository;

    public static Stream<Arguments> statusProvider() {
        return Stream.of(Arguments.of(DbStatus.PENDING, DbStatus.REJECTED),
                         Arguments.of(DbStatus.PENDING, DbStatus.APPROVED),
                         Arguments.of(DbStatus.APPROVED, DbStatus.PENDING),
                         Arguments.of(DbStatus.APPROVED, DbStatus.REJECTED),
                         Arguments.of(DbStatus.REJECTED, DbStatus.PENDING),
                         Arguments.of(DbStatus.REJECTED, DbStatus.APPROVED));
    }

    public static Stream<PeriodRepository> periodRepositoryProvider() {
        var year = ZonedDateTime.now().getYear();
        return Stream.of(periodRepositoryReturningClosedPeriod(year), periodRepositoryReturningNotOpenedPeriod(year),
                         mock(PeriodRepository.class));
    }

    @BeforeEach
    void setup() {
        localDynamo = initializeTestDatabase();
        candidateRepository = new CandidateRepository(localDynamo);
        periodRepository = periodRepositoryReturningOpenedPeriod(ZonedDateTime.now().getYear());
    }

    @Test
    void shouldCreatePendingApprovalsForNewCandidate() {
        var institutionId = randomUri();
        var upsertCandidateRequest = createUpsertCandidateRequest(institutionId);
        var candidate = CandidateBO.fromRequest(upsertCandidateRequest, candidateRepository, periodRepository)
                            .orElseThrow()
                            .toDto();
        assertThat(candidate.approvalStatuses().size(), is(equalTo(1)));
        assertThat(candidate.approvalStatuses().get(0).status(), is(equalTo(NviApprovalStatus.PENDING)));
        assertThat(candidate.approvalStatuses().get(0).institutionId(), is(equalTo(institutionId)));
    }

    @ParameterizedTest(name = "Should update from old status {0} to new status {1}")
    @MethodSource("statusProvider")
    void shouldUpdateStatusWhenUpdateStatusRequestValid(DbStatus oldStatus, DbStatus newStatus) {
        var institutionId = randomUri();
        var upsertCandidateRequest = createUpsertCandidateRequest(institutionId);
        var existingCandidate = CandidateBO.fromRequest(upsertCandidateRequest, candidateRepository, periodRepository)
                                    .orElseThrow()
                                    .updateApproval(
                                        createUpdateStatusRequest(oldStatus, institutionId, randomString()));
        var updatedCandidate = existingCandidate.updateApproval(
            createUpdateStatusRequest(newStatus, institutionId, randomString()));

        var actualNewStatus = updatedCandidate.toDto().approvalStatuses().get(0).status();
        assertThat(actualNewStatus, is(equalTo(mapToNviApprovalStatus(newStatus))));
    }

    @ParameterizedTest
    @EnumSource(value = DbStatus.class, names = {"REJECTED", "APPROVED"})
    void shouldResetApprovalWhenChangingToPending(DbStatus oldStatus) {
        var institutionId = randomUri();
        var upsertCandidateRequest = createUpsertCandidateRequest(randomUri(), randomUri(), true,
                                                                  InstanceType.ACADEMIC_MONOGRAPH, 1,
                                                                  randomBigDecimal(),
                                                                  institutionId);
        var candidateBO = CandidateBO.fromRequest(upsertCandidateRequest, candidateRepository, periodRepository)
                              .orElseThrow();
        var assignee = randomString();
        candidateBO.updateApproval(new UpdateAssigneeRequest(institutionId, assignee))
            .updateApproval(createUpdateStatusRequest(oldStatus, institutionId, randomString()))
            .updateApproval(createUpdateStatusRequest(DbStatus.PENDING, institutionId, randomString()));
        var approvalStatus = candidateBO.toDto().approvalStatuses().get(0);
        assertThat(approvalStatus.status(), is(equalTo(NviApprovalStatus.PENDING)));
        assertThat(approvalStatus.assignee(), is(assignee));
        assertThat(approvalStatus.finalizedBy(), is(nullValue()));
        assertThat(approvalStatus.finalizedDate(), is(nullValue()));
    }

    @ParameterizedTest
    @EnumSource(value = DbStatus.class, names = {"PENDING", "APPROVED"})
    void shouldRemoveReasonWhenUpdatingFromRejectionStatusToNewStatus(DbStatus newStatus) {
        var institutionId = randomUri();
        var createRequest = createUpsertCandidateRequest(institutionId);
        var rejectedCandidate = CandidateBO.fromRequest(createRequest, candidateRepository, periodRepository)
                                    .orElseThrow()
                                    .updateApproval(
                                        createUpdateStatusRequest(DbStatus.REJECTED, institutionId, randomString()));

        var updatedCandidate = rejectedCandidate.updateApproval(
            createUpdateStatusRequest(newStatus, institutionId, randomString())).toDto();
        assertThat(updatedCandidate.approvalStatuses().size(), is(equalTo(1)));
        assertThat(updatedCandidate.approvalStatuses().get(0).status(), is(equalTo(mapToNviApprovalStatus(newStatus))));
        assertThat(updatedCandidate.approvalStatuses().get(0).reason(), is(nullValue()));
    }

    @Test
    void shouldUpdateCandidateApprovalsWhenChangingPoints() {
        var upsertCandidateRequest = createUpsertCandidateRequest(randomUri());
        var candidate = CandidateBO.fromRequest(upsertCandidateRequest, candidateRepository, periodRepository)
                            .orElseThrow();
        var updateRequest = createUpsertCandidateRequest(candidate.toDto().publicationId(),
                                                         randomUri(), true, InstanceType.ACADEMIC_MONOGRAPH, 2,
                                                         randomBigDecimal(),
                                                         randomUri(),
                                                         randomUri(), randomUri());
        var updatedCandidate = CandidateBO.fromRequest(updateRequest, candidateRepository, periodRepository)
                                   .orElseThrow();
        assertThat(updatedCandidate.identifier(), is(equalTo(candidate.identifier())));
        assertThat(updatedCandidate.toDto().approvalStatuses().size(), is(equalTo(3)));
    }

    @Test
    void shouldRemoveOldInstitutionsWhenUpdatingCandidate() {
        var keepInstitutionId = randomUri();
        var deleteInstitutionId = randomUri();
        var createCandidateRequest = createUpsertCandidateRequest(keepInstitutionId, deleteInstitutionId, randomUri());
        CandidateBO.fromRequest(createCandidateRequest, candidateRepository, periodRepository);
        var updateRequest = createUpsertCandidateRequest(
            createCandidateRequest.publicationId(), randomUri(), true, InstanceType.ACADEMIC_MONOGRAPH, 2,
            randomBigDecimal(),
            keepInstitutionId,
            randomUri());
        var updatedCandidate = CandidateBO.fromRequest(updateRequest, candidateRepository, periodRepository)
                                   .orElseThrow();
        var dto = updatedCandidate.toDto();
        var approvalMap = dto.approvalStatuses()
                              .stream()
                              .collect(Collectors.toMap(ApprovalStatus::institutionId, Function.identity()));

        assertThat(approvalMap.containsKey(deleteInstitutionId), is(false));
        assertThat(approvalMap.containsKey(keepInstitutionId), is(true));
        assertThat(approvalMap.size(), is(2));
    }

    @Test
    void shouldRemoveApprovalsWhenBecomingNonCandidate() {
        var upsertCandidateRequest = createUpsertCandidateRequest(randomUri());
        var candidate = CandidateBO.fromRequest(upsertCandidateRequest, candidateRepository, periodRepository)
                            .orElseThrow();
        var updateRequest = createUpsertCandidateRequest(candidate.toDto().publicationId(),
                                                         randomUri(), false, InstanceType.ACADEMIC_MONOGRAPH, 2,
                                                         randomBigDecimal(), randomUri());
        var updatedCandidate = CandidateBO.fromRequest(updateRequest, candidateRepository, periodRepository)
                                   .orElseThrow();
        assertThat(updatedCandidate.identifier(), is(equalTo(candidate.identifier())));
        assertThat(updatedCandidate.getApprovals().size(), is(equalTo(0)));
    }

    @Test
    void shouldThrowExceptionWhenApplicableAndNonCandidate() {
        var upsertCandidateRequest = createUpsertCandidateRequest(randomUri());
        var candidateBO = CandidateBO.fromRequest(upsertCandidateRequest, candidateRepository, periodRepository)
                              .orElseThrow();
        var updateRequest = createUpsertCandidateRequest(candidateBO.toDto().publicationId(),
                                                         randomUri(), true, InstanceType.NON_CANDIDATE, 2,
                                                         randomBigDecimal(), randomUri());
        assertThrows(InvalidNviCandidateException.class,
                     () -> CandidateBO.fromRequest(updateRequest, candidateRepository, periodRepository));
    }

    @ParameterizedTest
    @EnumSource(value = DbStatus.class, names = {"PENDING", "APPROVED"})
    void shouldThrowUnsupportedOperationWhenRejectingWithoutReason(DbStatus oldStatus) {
        var institutionId = randomUri();
        var createRequest = createUpsertCandidateRequest(institutionId);
        var candidate = CandidateBO.fromRequest(createRequest, candidateRepository, periodRepository).orElseThrow()
                            .updateApproval(createUpdateStatusRequest(oldStatus, institutionId, randomString()));
        assertThrows(UnsupportedOperationException.class, () -> candidate.updateApproval(
            createRejectionRequestWithoutReason(institutionId, randomString())));
    }

    @ParameterizedTest
    @EnumSource(value = DbStatus.class)
    void shouldThrowIllegalArgumentExceptionWhenUpdateStatusWithoutUsername(DbStatus newStatus) {
        var institutionId = randomUri();
        var createRequest = createUpsertCandidateRequest(institutionId);
        var candidate = CandidateBO.fromRequest(createRequest, candidateRepository, periodRepository).orElseThrow();

        assertThrows(IllegalArgumentException.class,
                     () -> candidate.updateApproval(createUpdateStatusRequest(newStatus, institutionId, null)));
    }

    @Test
    void shouldPersistStatusChangeWhenRequestingAndUpdate() {
        var institutionId = randomUri();
        var upsertCandidateRequest = createUpsertCandidateRequest(institutionId);
        var candidateBO = CandidateBO.fromRequest(upsertCandidateRequest, candidateRepository, periodRepository)
                              .orElseThrow();
        candidateBO.updateApproval(createUpdateStatusRequest(DbStatus.APPROVED, institutionId, randomString()));

        var status = CandidateBO.fromRequest(candidateBO::identifier, candidateRepository, periodRepository)
                         .toDto()
                         .approvalStatuses()
                         .get(0)
                         .status();

        assertThat(status, is(equalTo(NviApprovalStatus.APPROVED)));
    }

    @Test
    void shouldChangeAssigneeWhenValidUpdateAssigneeRequest() {
        var institutionId = randomUri();
        var upsertCandidateRequest = createUpsertCandidateRequest(institutionId);
        var candidateBO = CandidateBO.fromRequest(upsertCandidateRequest, candidateRepository, periodRepository)
                              .orElseThrow();
        var newUsername = randomString();
        candidateBO.updateApproval(new UpdateAssigneeRequest(institutionId, newUsername));

        var assignee = CandidateBO.fromRequest(candidateBO::identifier, candidateRepository, periodRepository)
                           .toDto()
                           .approvalStatuses()
                           .get(0)
                           .assignee();

        assertThat(assignee, is(equalTo(newUsername)));
    }

    @Test
    void shouldNotAllowUpdateApprovalStatusWhenTryingToPassAnonymousImplementations() {
        var institutionId = randomUri();
        var upsertCandidateRequest = createUpsertCandidateRequest(institutionId);
        var candidateBO = CandidateBO.fromRequest(upsertCandidateRequest, candidateRepository, periodRepository)
                              .orElseThrow();
        assertThrows(IllegalArgumentException.class, () -> candidateBO.updateApproval(() -> institutionId));
    }

    @ParameterizedTest()
    @MethodSource("periodRepositoryProvider")
    void shouldNotAllowToUpdateApprovalWhenCandidateIsNotWithinPeriod(PeriodRepository periodRepository) {
        var candidate = CandidateBO.fromRequest(createUpsertCandidateRequest(randomUri()), candidateRepository,
                                                periodRepository).orElseThrow();
        assertThrows(IllegalStateException.class,
                     () -> candidate.updateApproval(new UpdateAssigneeRequest(randomUri(), randomString())));
    }

    private static UpdateStatusRequest createRejectionRequestWithoutReason(URI institutionId, String username) {
        return UpdateStatusRequest.builder()
                   .withApprovalStatus(DbStatus.REJECTED)
                   .withInstitutionId(institutionId)
                   .withUsername(username)
                   .build();
    }

    private static NviApprovalStatus mapToNviApprovalStatus(DbStatus newStatus) {
        return NviApprovalStatus.parse(newStatus.getValue());
    }
}
