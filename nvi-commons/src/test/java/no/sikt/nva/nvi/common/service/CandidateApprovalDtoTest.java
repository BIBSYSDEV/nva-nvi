package no.sikt.nva.nvi.common.service;

import static no.sikt.nva.nvi.test.TestUtils.createUpdateStatusRequest;
import static no.sikt.nva.nvi.test.TestUtils.createUpsertCandidateRequest;
import static no.sikt.nva.nvi.test.TestUtils.createUpsertNonCandidateRequest;
import static no.sikt.nva.nvi.test.TestUtils.periodRepositoryReturningClosedPeriod;
import static no.sikt.nva.nvi.test.TestUtils.periodRepositoryReturningNotOpenedPeriod;
import static no.sikt.nva.nvi.test.TestUtils.periodRepositoryReturningOpenedPeriod;
import static no.sikt.nva.nvi.test.TestUtils.randomApplicableCandidate;
import static no.sikt.nva.nvi.test.TestUtils.randomBigDecimal;
import static no.sikt.nva.nvi.test.TestUtils.randomLevelExcluding;
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
import no.sikt.nva.nvi.common.db.CandidateDao.DbLevel;
import no.sikt.nva.nvi.common.db.CandidateRepository;
import no.sikt.nva.nvi.common.db.PeriodRepository;
import no.sikt.nva.nvi.common.model.InvalidNviCandidateException;
import no.sikt.nva.nvi.common.model.UpdateAssigneeRequest;
import no.sikt.nva.nvi.common.model.UpdateStatusRequest;
import no.sikt.nva.nvi.common.service.dto.ApprovalDto;
import no.sikt.nva.nvi.common.service.model.ApprovalStatus;
import no.sikt.nva.nvi.common.service.model.Candidate;
import no.sikt.nva.nvi.common.service.model.InstanceType;
import no.sikt.nva.nvi.common.service.requests.UpsertCandidateRequest;
import no.sikt.nva.nvi.test.LocalDynamoTest;
import no.sikt.nva.nvi.test.TestUtils;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.MethodSource;

class CandidateApprovalDtoTest extends LocalDynamoTest {

    private static final String APPROVED = "APPROVED";
    private CandidateRepository candidateRepository;
    private PeriodRepository periodRepository;

    public static Stream<Arguments> statusProvider() {
        return Stream.of(Arguments.of(ApprovalStatus.PENDING, ApprovalStatus.REJECTED),
                         Arguments.of(ApprovalStatus.PENDING, ApprovalStatus.APPROVED),
                         Arguments.of(ApprovalStatus.APPROVED, ApprovalStatus.PENDING),
                         Arguments.of(ApprovalStatus.APPROVED, ApprovalStatus.REJECTED),
                         Arguments.of(ApprovalStatus.REJECTED, ApprovalStatus.PENDING),
                         Arguments.of(ApprovalStatus.REJECTED, ApprovalStatus.APPROVED));
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
        var candidate = upsert(upsertCandidateRequest);
        assertThat(candidate.getApprovals().size(), is(equalTo(1)));
        assertThat(candidate.getApprovals().get(institutionId).getStatus(), is(equalTo(ApprovalStatus.PENDING)));
    }

    @ParameterizedTest(name = "Should update from old status {0} to new status {1}")
    @MethodSource("statusProvider")
    void shouldUpdateStatusWhenUpdateStatusRequestValid(ApprovalStatus oldStatus, ApprovalStatus newStatus) {
        var institutionId = randomUri();
        var upsertCandidateRequest = createUpsertCandidateRequest(institutionId);
        Candidate.upsert(upsertCandidateRequest, candidateRepository, periodRepository);
        var existingCandidate = Candidate.fetchByPublicationId(upsertCandidateRequest::publicationId,
                                                               candidateRepository,
                                                               periodRepository)
                                    .updateApproval(
                                        createUpdateStatusRequest(oldStatus, institutionId, randomString()));
        var updatedCandidate = existingCandidate.updateApproval(
            createUpdateStatusRequest(newStatus, institutionId, randomString()));

        var actualNewStatus = updatedCandidate.getApprovals().get(institutionId).getStatus();
        assertThat(actualNewStatus, is(equalTo(newStatus)));
    }

    @ParameterizedTest
    @EnumSource(value = ApprovalStatus.class, names = {"REJECTED", APPROVED})
    void shouldResetApprovalWhenChangingToPending(ApprovalStatus oldStatus) {
        var institutionId = randomUri();
        var upsertCandidateRequest = createUpsertCandidateRequest(randomUri(), randomUri(), true,
                                                                  InstanceType.ACADEMIC_MONOGRAPH, 1,
                                                                  randomBigDecimal(),
                                                                  randomLevelExcluding(
                                                                      DbLevel.NON_CANDIDATE).getValue(),
                                                                  TestUtils.CURRENT_YEAR,
                                                                  institutionId);
        var candidateBO = upsert(upsertCandidateRequest);
        var assignee = randomString();
        candidateBO.updateApproval(new UpdateAssigneeRequest(institutionId, assignee))
            .updateApproval(createUpdateStatusRequest(oldStatus, institutionId, randomString()))
            .updateApproval(createUpdateStatusRequest(ApprovalStatus.PENDING, institutionId, randomString()));
        var approvalStatus = candidateBO.getApprovals().get(institutionId);
        assertThat(approvalStatus.getStatus(), is(equalTo(ApprovalStatus.PENDING)));
        assertThat(approvalStatus.getAssignee().value(), is(assignee));
        assertThat(approvalStatus.getFinalizedBy(), is(nullValue()));
        assertThat(approvalStatus.getFinalizedDate(), is(nullValue()));
    }

    @ParameterizedTest
    @EnumSource(value = ApprovalStatus.class, names = {"PENDING", APPROVED})
    void shouldRemoveReasonWhenUpdatingFromRejectionStatusToNewStatus(ApprovalStatus newStatus) {
        var institutionId = randomUri();
        var createRequest = createUpsertCandidateRequest(institutionId);
        Candidate.upsert(createRequest, candidateRepository, periodRepository);
        var rejectedCandidate = Candidate.fetchByPublicationId(createRequest::publicationId, candidateRepository,
                                                               periodRepository)
                                    .updateApproval(
                                        createUpdateStatusRequest(ApprovalStatus.REJECTED, institutionId,
                                                                  randomString()));

        var updatedCandidate = rejectedCandidate.updateApproval(
            createUpdateStatusRequest(newStatus, institutionId, randomString()));
        assertThat(updatedCandidate.getApprovals().size(), is(equalTo(1)));
        assertThat(updatedCandidate.getApprovals().get(institutionId).getStatus(), is(equalTo(newStatus)));
        assertThat(updatedCandidate.getApprovals().get(institutionId).getReason(), is(nullValue()));
    }

    @Test
    void shouldRemoveOldInstitutionsWhenUpdatingCandidate() {
        var keepInstitutionId = randomUri();
        var deleteInstitutionId = randomUri();
        var createCandidateRequest = createUpsertCandidateRequest(keepInstitutionId, deleteInstitutionId, randomUri());
        Candidate.upsert(createCandidateRequest, candidateRepository, periodRepository);
        var updateRequest = createUpsertCandidateRequest(
            createCandidateRequest.publicationId(), randomUri(), true, InstanceType.ACADEMIC_MONOGRAPH, 2,
            randomBigDecimal(), randomLevelExcluding(DbLevel.NON_CANDIDATE).getValue(),
            TestUtils.CURRENT_YEAR,
            keepInstitutionId,
            randomUri());
        var updatedCandidate = upsert(updateRequest);
        var dto = updatedCandidate.toDto();
        var approvalMap = dto.approvals()
                              .stream()
                              .collect(Collectors.toMap(ApprovalDto::institutionId, Function.identity()));

        assertThat(approvalMap.containsKey(deleteInstitutionId), is(false));
        assertThat(approvalMap.containsKey(keepInstitutionId), is(true));
        assertThat(approvalMap.size(), is(2));
    }

    @Test
    void shouldRemoveApprovalsWhenBecomingNonCandidate() {
        var candidate = randomApplicableCandidate(candidateRepository, periodRepository);
        var updateRequest = createUpsertNonCandidateRequest(candidate.toDto().publicationId());
        var updatedCandidate = Candidate.updateNonCandidate(updateRequest, candidateRepository)
                                   .orElseThrow();
        assertThat(updatedCandidate.getIdentifier(), is(equalTo(candidate.getIdentifier())));
        assertThat(updatedCandidate.getApprovals().size(), is(equalTo(0)));
    }

    @Test
    void shouldThrowExceptionWhenApplicableAndNonCandidate() {
        var candidateBO = randomApplicableCandidate(candidateRepository, periodRepository);
        var updateRequest = createUpsertCandidateRequest(candidateBO.toDto().publicationId(),
                                                         randomUri(),
                                                         true,
                                                         null,
                                                         2,
                                                         randomBigDecimal(),
                                                         randomLevelExcluding(DbLevel.NON_CANDIDATE)
                                                             .getValue(), TestUtils.CURRENT_YEAR,
                                                         randomUri());
        assertThrows(InvalidNviCandidateException.class,
                     () -> Candidate.upsert(updateRequest, candidateRepository, periodRepository));
    }

    @ParameterizedTest
    @EnumSource(value = ApprovalStatus.class, names = {"PENDING", APPROVED})
    void shouldThrowUnsupportedOperationWhenRejectingWithoutReason(ApprovalStatus oldStatus) {
        var institutionId = randomUri();
        var createRequest = createUpsertCandidateRequest(institutionId);
        Candidate.upsert(createRequest, candidateRepository, periodRepository);
        var candidate = Candidate.fetchByPublicationId(createRequest::publicationId, candidateRepository,
                                                       periodRepository)
                            .updateApproval(createUpdateStatusRequest(oldStatus, institutionId, randomString()));
        assertThrows(UnsupportedOperationException.class, () -> candidate.updateApproval(
            createRejectionRequestWithoutReason(institutionId, randomString())));
    }

    @ParameterizedTest
    @EnumSource(value = ApprovalStatus.class, names = {"PENDING", APPROVED, "REJECTED"})
    void shouldThrowIllegalArgumentExceptionWhenUpdateStatusWithoutUsername(ApprovalStatus newStatus) {
        var institutionId = randomUri();
        var createRequest = createUpsertCandidateRequest(institutionId);
        var candidate = upsert(createRequest);

        assertThrows(IllegalArgumentException.class,
                     () -> candidate.updateApproval(createUpdateStatusRequest(newStatus, institutionId, null)));
    }

    @Test
    void shouldPersistStatusChangeWhenRequestingAndUpdate() {
        var institutionId = randomUri();
        var upsertCandidateRequest = createUpsertCandidateRequest(institutionId);
        var candidateBO = upsert(upsertCandidateRequest);
        candidateBO.updateApproval(createUpdateStatusRequest(ApprovalStatus.APPROVED, institutionId, randomString()));

        var status = Candidate.fetch(candidateBO::getIdentifier, candidateRepository, periodRepository)
                         .getApprovals().get(institutionId).getStatus();
        assertThat(status, is(equalTo(ApprovalStatus.APPROVED)));
    }

    @Test
    void shouldChangeAssigneeWhenValidUpdateAssigneeRequest() {
        var institutionId = randomUri();
        var upsertCandidateRequest = createUpsertCandidateRequest(institutionId);
        var candidateBO = upsert(upsertCandidateRequest);
        var newUsername = randomString();
        candidateBO.updateApproval(new UpdateAssigneeRequest(institutionId, newUsername));

        var assignee = Candidate.fetch(candidateBO::getIdentifier, candidateRepository, periodRepository)
                           .toDto()
                           .approvals()
                           .get(0)
                           .assignee();

        assertThat(assignee, is(equalTo(newUsername)));
    }

    @Test
    void shouldNotAllowUpdateApprovalStatusWhenTryingToPassAnonymousImplementations() {
        var institutionId = randomUri();
        var upsertCandidateRequest = createUpsertCandidateRequest(institutionId);
        var candidateBO = upsert(upsertCandidateRequest);
        assertThrows(IllegalArgumentException.class, () -> candidateBO.updateApproval(() -> institutionId));
    }

    @ParameterizedTest()
    @MethodSource("periodRepositoryProvider")
    void shouldNotAllowToUpdateApprovalWhenCandidateIsNotWithinPeriod(PeriodRepository periodRepository) {
        var candidate = randomApplicableCandidate(candidateRepository, periodRepository);
        assertThrows(IllegalStateException.class,
                     () -> candidate.updateApproval(new UpdateAssigneeRequest(randomUri(), randomString())));
    }

    private static UpdateStatusRequest createRejectionRequestWithoutReason(URI institutionId, String username) {
        return UpdateStatusRequest.builder()
                   .withApprovalStatus(ApprovalStatus.REJECTED)
                   .withInstitutionId(institutionId)
                   .withUsername(username)
                   .build();
    }

    private Candidate upsert(UpsertCandidateRequest request) {
        Candidate.upsert(request, candidateRepository, periodRepository);
        return Candidate.fetchByPublicationId(request::publicationId, candidateRepository, periodRepository);
    }
}
