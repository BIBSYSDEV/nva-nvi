package no.sikt.nva.nvi.common.service;

import static no.sikt.nva.nvi.test.TestUtils.createNoteRequest;
import static no.sikt.nva.nvi.test.TestUtils.createUpdateStatusRequest;
import static no.sikt.nva.nvi.test.TestUtils.createUpsertCandidateRequest;
import static no.sikt.nva.nvi.test.TestUtils.periodRepositoryReturningOpenedPeriod;
import static no.unit.nva.testutils.RandomDataGenerator.randomString;
import static no.unit.nva.testutils.RandomDataGenerator.randomUri;
import static nva.commons.core.paths.UriWrapper.HTTPS;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.not;
import static org.hamcrest.Matchers.notNullValue;
import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertThrows;
import java.net.URI;
import java.time.ZonedDateTime;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;
import no.sikt.nva.nvi.common.db.ApprovalStatusDao.DbStatus;
import no.sikt.nva.nvi.common.db.CandidateRepository;
import no.sikt.nva.nvi.common.db.PeriodRepository;
import no.sikt.nva.nvi.common.db.PeriodStatus;
import no.sikt.nva.nvi.common.db.PeriodStatus.Status;
import no.sikt.nva.nvi.common.db.model.InstanceType;
import no.sikt.nva.nvi.common.model.UpdateAssigneeRequest;
import no.sikt.nva.nvi.common.service.dto.ApprovalStatus;
import no.sikt.nva.nvi.common.service.dto.NviApprovalStatus;
import no.sikt.nva.nvi.common.service.dto.PeriodStatusDto;
import no.sikt.nva.nvi.common.service.exception.CandidateNotFoundException;
import no.sikt.nva.nvi.common.service.exception.IllegalOperationException;
import no.sikt.nva.nvi.test.LocalDynamoTest;
import nva.commons.core.Environment;
import nva.commons.core.paths.UriWrapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class CandidateBOTest extends LocalDynamoTest {

    private static final Environment ENVIRONMENT = new Environment();
    private static final String BASE_PATH = ENVIRONMENT.readEnv("CUSTOM_DOMAIN_BASE_PATH");
    private static final String API_DOMAIN = ENVIRONMENT.readEnv("API_HOST");
    private CandidateRepository candidateRepository;
    private PeriodRepository periodRepository;

    @BeforeEach
    void setup() {
        localDynamo = initializeTestDatabase();
        candidateRepository = new CandidateRepository(localDynamo);
        periodRepository = periodRepositoryReturningOpenedPeriod(ZonedDateTime.now().getYear());
    }

    @Test
    void shouldThrowNotFoundExceptionWhenCandidateDoesNotExist() {
        assertThrows(CandidateNotFoundException.class,
                     () -> CandidateBO.fromRequest(UUID::randomUUID, candidateRepository, periodRepository));
    }

    @Test
    void shouldReturnCandidateWhenExists() {
        var upsertCandidateRequest = createUpsertCandidateRequest(randomUri());
        var candidate = CandidateBO.fromRequest(upsertCandidateRequest, candidateRepository, periodRepository);
        var fetchedCandidate = CandidateBO.fromRequest(candidate::identifier, candidateRepository, periodRepository);
        assertThat(fetchedCandidate.identifier(), is(equalTo(candidate.identifier())));
    }

    @Test
    void shouldFetchCandidateByPublicationId() {
        var upsertCandidateRequest = createUpsertCandidateRequest(randomUri());
        var candidate = CandidateBO.fromRequest(upsertCandidateRequest, candidateRepository, periodRepository);
        var fetchedCandidate = CandidateBO.fromRequest(candidate::publicationId, candidateRepository, periodRepository);
        assertThat(fetchedCandidate.identifier(), is(equalTo(candidate.identifier())));
    }

    @Test
    void shouldThrowIllegalOperationWhenNonApplicableNonCandidateIsAttemptedInserted() {
        var updateRequest = createUpsertCandidateRequest(randomUri(), false, 2,
                                                         InstanceType.NON_CANDIDATE, randomUri());
        assertThrows(IllegalOperationException.class,
                     () -> CandidateBO.fromRequest(updateRequest, candidateRepository, periodRepository));
    }

    @Test
    void dontMindMeJustTestingToDto() {
        var institutionToReject = randomUri();
        var institutionToApprove = randomUri();
        var createRequest = createUpsertCandidateRequest(randomUri(), true, 4,
                                                         InstanceType.ACADEMIC_MONOGRAPH, institutionToApprove,
                                                         randomUri(), institutionToReject);
        var candidateBO = CandidateBO.fromRequest(createRequest, candidateRepository, periodRepository);
        candidateBO.createNote(createNoteRequest(randomString(), randomString()))
            .createNote(createNoteRequest(randomString(), randomString()))
            .updateApproval(createUpdateStatusRequest(DbStatus.APPROVED, institutionToApprove, randomString()))
            .updateApproval(createUpdateStatusRequest(DbStatus.REJECTED, institutionToReject, randomString()));
        var dto = candidateBO.toDto();
        var approvalMap = dto.approvalStatuses()
                              .stream()
                              .collect(Collectors.toMap(ApprovalStatus::institutionId, Function.identity()));

        assertAll(() -> {
            assertThat(dto.publicationId(), is(equalTo(createRequest.publicationId())));
            assertThat(dto.approvalStatuses().size(), is(equalTo(createRequest.points().size())));
            assertThat(dto.notes().size(), is(2));
            var note = dto.notes().get(0);
            assertThat(note.text(), is(notNullValue()));
            assertThat(note.user(), is(notNullValue()));
            assertThat(note.createdDate(), is(notNullValue()));
            assertThat(dto.id(), is(equalTo(constructId(candidateBO.identifier()))));
            assertThat(dto.identifier(), is(equalTo(candidateBO.identifier())));
            var periodStatus = getDefaultPeriodStatus();
            assertThat(dto.periodStatus().status(), is(equalTo(periodStatus.status())));
            assertThat(approvalMap.get(institutionToApprove).status(), is(equalTo(NviApprovalStatus.APPROVED)));
            var rejectedAP = approvalMap.get(institutionToReject);
            assertThat(rejectedAP.status(), is(equalTo(NviApprovalStatus.REJECTED)));
            assertThat(rejectedAP.reason(), is(notNullValue()));
            assertThat(rejectedAP.points(), is(createRequest.points().get(rejectedAP.institutionId())));
        });
    }

    @Test
    void shouldNotOverrideAssigneeWhenAssigneeAlreadyIsSet() {
        var institutionId = randomUri();
        var assignee = randomString();
        var createCandidateRequest = createUpsertCandidateRequest(institutionId);
        var candidate = CandidateBO.fromRequest(createCandidateRequest, candidateRepository, periodRepository)
                            .updateApproval(new UpdateAssigneeRequest(institutionId, assignee))
                            .updateApproval(createUpdateStatusRequest(DbStatus.APPROVED, institutionId, randomString()))
                            .updateApproval(createUpdateStatusRequest(DbStatus.REJECTED, institutionId, randomString()))
                            .toDto();
        assertThat(candidate.approvalStatuses().get(0).assignee(), is(equalTo(assignee)));
        assertThat(candidate.approvalStatuses().get(0).finalizedBy(), is(not(equalTo(assignee))));
    }

    private static PeriodStatusDto getDefaultPeriodStatus() {
        return PeriodStatusDto.fromPeriodStatus(
            PeriodStatus.builder().withStatus(Status.OPEN_PERIOD).build());
    }

    //TODO; add xDto to DTO classes

    private static URI constructId(UUID identifier) {
        return new UriWrapper(HTTPS, API_DOMAIN).addChild(BASE_PATH, "candidate", identifier.toString()).getUri();
    }
}