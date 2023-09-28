package no.sikt.nva.nvi.common.service;

import static no.sikt.nva.nvi.test.TestUtils.createUpdateStatusRequest;
import static no.sikt.nva.nvi.test.TestUtils.createUpsertCandidateRequest;
import static no.unit.nva.testutils.RandomDataGenerator.randomString;
import static no.unit.nva.testutils.RandomDataGenerator.randomUri;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.nullValue;
import static org.junit.jupiter.api.Assertions.assertThrows;
import java.net.URI;
import java.util.UUID;
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

    @BeforeEach
    void setup() {
        localDynamo = initializeTestDatabase();
        candidateRepository = new CandidateRepository(localDynamo);
        periodRepository = new PeriodRepository(localDynamo);
    }

    @Test
    void shouldCreatePendingApprovalsForNewCandidate() {
        var institutionId = randomUri();
        var upsertCandidateRequest = createUpsertCandidateRequest(institutionId);
        var candidate = CandidateBO.fromRequest(upsertCandidateRequest, candidateRepository, periodRepository).toDto();
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
                                    .updateStatus(createUpdateStatusRequest(oldStatus, institutionId, randomString()));
        var updatedCandidate = existingCandidate.updateStatus(createUpdateStatusRequest(newStatus, institutionId,
                                                                                        randomString()));

        var actualNewStatus = updatedCandidate.toDto().approvalStatuses().get(0).status();
        assertThat(actualNewStatus, is(equalTo(mapToNviApprovalStatus(newStatus))));
    }

    @ParameterizedTest
    @EnumSource(value = DbStatus.class, names = {"REJECTED", "APPROVED"})
    void shouldResetApprovalWhenChangingToPending(DbStatus oldStatus) {
        var institutionId = randomUri();
        var upsertCandidateRequest = createUpsertCandidateRequest(UUID.randomUUID(), randomUri(), true, 1,
                                                                  InstanceType.ACADEMIC_MONOGRAPH, institutionId);
        var candidateBO = CandidateBO.fromRequest(upsertCandidateRequest, candidateRepository, periodRepository);
        var assignee = randomString();
        candidateBO.updateStatus(new UpdateAssigneeRequest(institutionId, assignee))
            .updateStatus(createUpdateStatusRequest(oldStatus, institutionId, randomString()))
            .updateStatus(createUpdateStatusRequest(DbStatus.PENDING, institutionId, randomString()));
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
                                    .updateStatus(createUpdateStatusRequest(DbStatus.REJECTED, institutionId,
                                                                            randomString()));

        var updatedCandidate = rejectedCandidate.updateStatus(createUpdateStatusRequest(newStatus, institutionId,
                                                                                        randomString())).toDto();
        assertThat(updatedCandidate.approvalStatuses().size(), is(equalTo(1)));
        assertThat(updatedCandidate.approvalStatuses().get(0).status(), is(equalTo(mapToNviApprovalStatus(newStatus))));
        assertThat(updatedCandidate.approvalStatuses().get(0).reason(), is(nullValue()));
    }

    @Test
    void shouldUpdateCandidateApprovalsWhenChangingPoints() {
        var upsertCandidateRequest = createUpsertCandidateRequest(randomUri());
        var candidate = CandidateBO.fromRequest(upsertCandidateRequest, candidateRepository, periodRepository);
        var updateRequest = createUpsertCandidateRequest(candidate.identifier(), candidate.toDto().publicationId(),
                                                         true, 2, InstanceType.ACADEMIC_MONOGRAPH, randomUri(),
                                                         randomUri(), randomUri());
        var updatedCandidate = CandidateBO.fromRequest(updateRequest, candidateRepository, periodRepository);
        assertThat(updatedCandidate.identifier(), is(equalTo(candidate.identifier())));
        assertThat(updatedCandidate.toDto().approvalStatuses().size(), is(equalTo(3)));
    }

    @Test
    void shouldRemoveOldInstitutionsWhenUpdatingCandidate() {
        var keepInstitutionId = randomUri();
        var deleteInstitutionId = randomUri();
        var createCandidateRequest = createUpsertCandidateRequest(keepInstitutionId, deleteInstitutionId, randomUri());
        CandidateBO.fromRequest(createCandidateRequest, candidateRepository, periodRepository);
        var updateRequest = createUpsertCandidateRequest(createCandidateRequest.identifier(),
                                                         createCandidateRequest.publicationId(), true, 2,
                                                         InstanceType.ACADEMIC_MONOGRAPH, keepInstitutionId,
                                                         randomUri());
        var updatedCandidate = CandidateBO.fromRequest(updateRequest, candidateRepository, periodRepository);
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
        var candidate = CandidateBO.fromRequest(upsertCandidateRequest, candidateRepository, periodRepository);
        var updateRequest = createUpsertCandidateRequest(candidate.identifier(), candidate.toDto().publicationId(),
                                                         false, 2, InstanceType.ACADEMIC_MONOGRAPH, randomUri());
        var updatedCandidate = CandidateBO.fromRequest(updateRequest, candidateRepository, periodRepository);
        assertThat(updatedCandidate.identifier(), is(equalTo(candidate.identifier())));
        assertThat(updatedCandidate.toDto().approvalStatuses().size(), is(equalTo(0)));
    }

    @Test
    void shouldThrowExceptionWhenApplicableAndNonCandidate() {
        var upsertCandidateRequest = createUpsertCandidateRequest(randomUri());
        var candidateBO = CandidateBO.fromRequest(upsertCandidateRequest, candidateRepository, periodRepository);
        var updateRequest = createUpsertCandidateRequest(candidateBO.identifier(), candidateBO.toDto().publicationId(),
                                                         true, 2, InstanceType.NON_CANDIDATE, randomUri());
        assertThrows(InvalidNviCandidateException.class,
                     () -> CandidateBO.fromRequest(updateRequest, candidateRepository, periodRepository));
    }

    @ParameterizedTest
    @EnumSource(value = DbStatus.class, names = {"PENDING", "APPROVED"})
    void shouldThrowUnsupportedOperationWhenRejectingWithoutReason(DbStatus oldStatus) {
        var institutionId = randomUri();
        var createRequest = createUpsertCandidateRequest(institutionId);
        var candidate =
            CandidateBO.fromRequest(createRequest, candidateRepository, periodRepository)
                .updateStatus(createUpdateStatusRequest(oldStatus, institutionId, randomString()));
        assertThrows(UnsupportedOperationException.class,
                     () -> candidate.updateStatus(
                         createRejectionRequestWithoutReason(institutionId, randomString())));
    }

    @ParameterizedTest
    @EnumSource(value = DbStatus.class)
    void shouldThrowIllegalArgumentExceptionWhenUpdateStatusWithoutUsername(DbStatus newStatus) {
        var institutionId = randomUri();
        var createRequest = createUpsertCandidateRequest(institutionId);
        var candidate = CandidateBO.fromRequest(createRequest, candidateRepository, periodRepository);

        assertThrows(IllegalArgumentException.class,
                     () -> candidate.updateStatus(createUpdateStatusRequest(newStatus, institutionId, null)));
    }

    @Test
    void shouldPersistStatusChangeWhenRequestingAndUpdate() {
        var institutionId = randomUri();
        var upsertCandidateRequest = createUpsertCandidateRequest(institutionId);
        var candidateBO = CandidateBO.fromRequest(upsertCandidateRequest, candidateRepository, periodRepository);
        candidateBO.updateStatus(createUpdateStatusRequest(DbStatus.APPROVED, institutionId, randomString()));

        var status = CandidateBO.fromRequest(candidateBO::identifier, candidateRepository,
                                             periodRepository).toDto().approvalStatuses().get(0).status();

        assertThat(status, is(equalTo(NviApprovalStatus.APPROVED)));
    }

    @Test
    void shouldChangeAssigneeWhenValidUpdateAssigneeRequest() {
        var institutionId = randomUri();
        var upsertCandidateRequest = createUpsertCandidateRequest(institutionId);
        var candidateBO = CandidateBO.fromRequest(upsertCandidateRequest, candidateRepository, periodRepository);
        var newUsername = randomString();
        candidateBO.updateStatus(new UpdateAssigneeRequest(institutionId, newUsername));

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
        var candidateBO = CandidateBO.fromRequest(upsertCandidateRequest, candidateRepository, periodRepository);
        assertThrows(IllegalArgumentException.class, () -> candidateBO.updateStatus(() -> institutionId));
    }

    private static UpdateStatusRequest createRejectionRequestWithoutReason(URI institutionId,
                                                                           String username) {
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