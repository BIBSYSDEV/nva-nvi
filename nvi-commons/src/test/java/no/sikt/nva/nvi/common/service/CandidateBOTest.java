package no.sikt.nva.nvi.common.service;

import static no.sikt.nva.nvi.test.TestUtils.randomBigDecimal;
import static no.unit.nva.testutils.RandomDataGenerator.randomString;
import static no.unit.nva.testutils.RandomDataGenerator.randomUri;
import static nva.commons.core.paths.UriWrapper.HTTPS;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.not;
import static org.hamcrest.Matchers.notNullValue;
import static org.hamcrest.Matchers.nullValue;
import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertThrows;
import java.math.BigDecimal;
import java.net.URI;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import no.sikt.nva.nvi.common.db.ApprovalStatusDao.DbStatus;
import no.sikt.nva.nvi.common.db.CandidateDao.DbLevel;
import no.sikt.nva.nvi.common.db.CandidateRepository;
import no.sikt.nva.nvi.common.db.PeriodRepository;
import no.sikt.nva.nvi.common.db.PeriodStatus;
import no.sikt.nva.nvi.common.db.PeriodStatus.Status;
import no.sikt.nva.nvi.common.db.model.InstanceType;
import no.sikt.nva.nvi.common.model.InvalidNviCandidateException;
import no.sikt.nva.nvi.common.model.UpdateAssigneeRequest;
import no.sikt.nva.nvi.common.model.UpdateStatusRequest;
import no.sikt.nva.nvi.common.service.dto.ApprovalStatus;
import no.sikt.nva.nvi.common.service.dto.Candidate;
import no.sikt.nva.nvi.common.service.dto.NviApprovalStatus;
import no.sikt.nva.nvi.common.service.exception.IllegalOperationException;
import no.sikt.nva.nvi.common.service.exception.NotFoundException;
import no.sikt.nva.nvi.common.service.requests.CreateNoteRequest;
import no.sikt.nva.nvi.common.service.requests.PublicationDate;
import no.sikt.nva.nvi.common.service.requests.UpsertCandidateRequest;
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
        periodRepository = new PeriodRepository(localDynamo);
    }

    @Test
    void shouldThrowNotFoundExceptionWhenCandidateDoesNotExist() {
        assertThrows(NotFoundException.class,
                     () -> CandidateBO.fromRequest(UUID::randomUUID, candidateRepository, periodRepository));
    }

    @Test
    void shouldReturnCandidateWhenExists() {
        var upsertCandidateRequest = createUpsertCandidateRequest(randomUri());
        var candidateBO = CandidateBO.fromRequest(upsertCandidateRequest, candidateRepository, periodRepository);
        var sameCand = CandidateBO.fromRequest(candidateBO::identifier, candidateRepository, periodRepository);
        assertThat(sameCand.identifier(), is(equalTo(candidateBO.identifier())));
    }

    @Test
    void shouldBeAbleToFetchWhenSendingInPublicationId() {
        var upsertCandidateRequest = createUpsertCandidateRequest(randomUri());
        var candidateBO = CandidateBO.fromRequest(upsertCandidateRequest, candidateRepository, periodRepository);
        var sameCand = CandidateBO.fromRequest(candidateBO::publicationId, candidateRepository, periodRepository);
        assertThat(sameCand.identifier(), is(equalTo(candidateBO.identifier())));
    }

    @Test
    void shouldUpdateCandidateWhenChangingPoints() {
        var upsertCandidateRequest = createUpsertCandidateRequest(randomUri());
        var candidateBO = CandidateBO.fromRequest(upsertCandidateRequest, candidateRepository, periodRepository);
        var updateRequest = createUpsertCandidateRequest(candidateBO.identifier(), candidateBO.toDto().publicationId(),
                                                         true, 2, InstanceType.ACADEMIC_MONOGRAPH, randomUri(),
                                                         randomUri(), randomUri());
        var updatedCandidate = CandidateBO.fromRequest(updateRequest, candidateRepository, periodRepository);
        assertThat(updatedCandidate.identifier(), is(equalTo(candidateBO.identifier())));
        assertThat(updatedCandidate.toDto().approvalStatuses().size(), is(equalTo(3)));
    }

    @Test
    void shouldNotHaveApprovalsWhenNotApplicable() {
        var upsertCandidateRequest = createUpsertCandidateRequest(randomUri());
        var candidateBO = CandidateBO.fromRequest(upsertCandidateRequest, candidateRepository, periodRepository);
        var updateRequest = createUpsertCandidateRequest(candidateBO.identifier(), candidateBO.toDto().publicationId(),
                                                         false, 2, InstanceType.ACADEMIC_MONOGRAPH, randomUri());
        var updatedCandidate = CandidateBO.fromRequest(updateRequest, candidateRepository, periodRepository);
        assertThat(updatedCandidate.identifier(), is(equalTo(candidateBO.identifier())));
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

    @Test
    void shouldThrowIllegalOperationWhenNonApplicableNonCandidateIsAttemptedInserted() {
        var updateRequest = createUpsertCandidateRequest(UUID.randomUUID(), randomUri(), false, 2,
                                                         InstanceType.NON_CANDIDATE, randomUri());
        assertThrows(IllegalOperationException.class,
                     () -> CandidateBO.fromRequest(updateRequest, candidateRepository, periodRepository));
    }

    @Test
    void shouldResetApprovalWhenChangingToPending() {
        var institutionId = randomUri();
        var upsertCandidateRequest = createUpsertCandidateRequest(UUID.randomUUID(), randomUri(), true, 1,
                                                                  InstanceType.ACADEMIC_MONOGRAPH, institutionId);
        var candidateBO = CandidateBO.fromRequest(upsertCandidateRequest, candidateRepository, periodRepository);
        var assignee = randomString();
        candidateBO.updateStatus(new UpdateAssigneeRequest(institutionId, assignee))
            .updateStatus(createUpdateStatusRequest(DbStatus.APPROVED, null, institutionId))
            .updateStatus(createUpdateStatusRequest(DbStatus.PENDING, null, institutionId));
        var approvalStatus = candidateBO.toDto().approvalStatuses().get(0);
        assertThat(approvalStatus.status(), is(equalTo(NviApprovalStatus.PENDING)));
        assertThat(approvalStatus.assignee(), is(assignee));
        assertThat(approvalStatus.finalizedBy(), is(nullValue()));
        assertThat(approvalStatus.finalizedDate(), is(nullValue()));
    }

    @Test
    void dontMindMeJustTestingToDto() {
        var institutionToReject = randomUri();
        var institutionToApprove = randomUri();
        var createRequest = createUpsertCandidateRequest(UUID.randomUUID(), randomUri(), true, 4,
                                                         InstanceType.ACADEMIC_MONOGRAPH, institutionToApprove,
                                                         randomUri(), institutionToReject);
        var candidateBO = CandidateBO.fromRequest(createRequest, candidateRepository, periodRepository);
        candidateBO.createNote(createNoteRequest(randomString(), randomString()))
            .createNote(createNoteRequest(randomString(), randomString()))
            .updateStatus(createUpdateStatusRequest(DbStatus.APPROVED, null, institutionToApprove))
            .updateStatus(createUpdateStatusRequest(DbStatus.REJECTED, randomString(), institutionToReject));
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
            var periodStatus = getDefaultPeriodstatus();
            assertThat(dto.periodStatus().status(), is(equalTo(periodStatus.status())));
            assertThat(dto.periodStatus().periodClosesAt(), is(nullValue()));
            assertThat(approvalMap.get(institutionToApprove).status(), is(equalTo(NviApprovalStatus.APPROVED)));
            var rejectedAP = approvalMap.get(institutionToReject);
            assertThat(rejectedAP.status(), is(equalTo(NviApprovalStatus.REJECTED)));
            assertThat(rejectedAP.reason(), is(notNullValue()));
            assertThat(rejectedAP.points(), is(createRequest.points().get(rejectedAP.institutionId())));
        });
    }

    @Test
    void shouldThrowUnsupportedOperationWhenMissingRejectionReason() {
        var institutionId = randomUri();
        var createRequest = createUpsertCandidateRequest(institutionId);
        CandidateBO candidateBO = CandidateBO.fromRequest(createRequest, candidateRepository, periodRepository);
        assertThrows(UnsupportedOperationException.class,
                     () -> candidateBO.updateStatus(createUpdateStatusRequest(DbStatus.REJECTED, null, institutionId)));
    }

    @Test
    void shouldUpdateStatusWhenRequestingAnUpdate() {
        var institutionId = randomUri();
        var upsertCandidateRequest = createUpsertCandidateRequest(institutionId);
        var candidateBO = CandidateBO.fromRequest(upsertCandidateRequest, candidateRepository, periodRepository);
        CandidateBO updatedCandidate = candidateBO.updateStatus(
            createUpdateStatusRequest(DbStatus.APPROVED, null, institutionId));
        NviApprovalStatus status = updatedCandidate.toDto().approvalStatuses().get(0).status();

        assertThat(status, is(equalTo(NviApprovalStatus.APPROVED)));
    }

    @Test
    void shouldPersistStatusChangeWhenRequestingAndUpdate() {
        var institutionId = randomUri();
        var upsertCandidateRequest = createUpsertCandidateRequest(institutionId);
        var candidateBO = CandidateBO.fromRequest(upsertCandidateRequest, candidateRepository, periodRepository);
        candidateBO.updateStatus(createUpdateStatusRequest(DbStatus.APPROVED, null, institutionId));

        NviApprovalStatus status = CandidateBO.fromRequest(candidateBO::identifier, candidateRepository,
                                                           periodRepository).toDto().approvalStatuses().get(0).status();

        assertThat(status, is(equalTo(NviApprovalStatus.APPROVED)));
    }

    @Test
    void shouldChangeAssigneWhenRequestingChange() {
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
    void shouldCreateNoteWhenRequestingIt() {
        var upsertCandidateRequest = createUpsertCandidateRequest(randomUri());
        var candidateBO = CandidateBO.fromRequest(upsertCandidateRequest, candidateRepository, periodRepository);
        int size = candidateBO.createNote(createNoteRequest(randomString(), randomString())).toDto().notes().size();

        assertThat(size, is(1));
    }

    @Test
    void shouldDeleteNoteWhenAskedTo() {
        var upsertCandidateRequest = createUpsertCandidateRequest(randomUri());
        var candidateBO = CandidateBO.fromRequest(upsertCandidateRequest, candidateRepository, periodRepository);
        candidateBO.createNote(createNoteRequest(randomString(), randomString()));
        candidateBO.createNote(createNoteRequest(randomString(), randomString()));
        candidateBO.createNote(createNoteRequest(randomString(), randomString()));
        var deletedNoteIdentifier = candidateBO.toDto().notes().get(0).identifier();
        var latestCandidate = candidateBO.deleteNote(() -> deletedNoteIdentifier);
        boolean anyNotesWithDeletedIdentifier = latestCandidate.toDto()
                                                    .notes()
                                                    .stream()
                                                    .anyMatch(note -> note.identifier() == deletedNoteIdentifier);
        assertThat(latestCandidate.toDto().notes().size(), is(2));
        assertThat(anyNotesWithDeletedIdentifier, is(false));
    }

    @Test
    void shouldNotAllowUpdateApprovalStatusWhenTryingToPassAnonymousImplementations() {
        var institutionId = randomUri();
        var upsertCandidateRequest = createUpsertCandidateRequest(institutionId);
        var candidateBO = CandidateBO.fromRequest(upsertCandidateRequest, candidateRepository, periodRepository);
        assertThrows(IllegalArgumentException.class, () -> candidateBO.updateStatus(() -> institutionId));
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
        var createCandidateRequest = createUpsertCandidateRequest(randomUri());
        var candidateBO = CandidateBO.fromRequest(createCandidateRequest, candidateRepository, periodRepository);
        var nonCandidateRequest = createUpsertCandidateRequest(candidateBO.identifier(),
                                                               createCandidateRequest.publicationId(), false, 1,
                                                               InstanceType.ACADEMIC_MONOGRAPH);
        var nonCandidate = CandidateBO.fromRequest(nonCandidateRequest, candidateRepository, periodRepository);
        Candidate dto = nonCandidate.toDto();
        assertThat(dto.approvalStatuses().size(), is(0));
    }

    @Test
    void shouldNotOverrideAssigneeWhenAssigneeAlreadyIsSet() {

        var institutionId = randomUri();
        var assignee = randomString();
        var createCandidateRequest = createUpsertCandidateRequest(institutionId);
        var candidate = CandidateBO.fromRequest(createCandidateRequest, candidateRepository, periodRepository)
                            .updateStatus(new UpdateAssigneeRequest(institutionId, assignee))
                            .updateStatus(createUpdateStatusRequest(DbStatus.APPROVED, null, institutionId))
                            .updateStatus(createUpdateStatusRequest(DbStatus.REJECTED, randomString(), institutionId))
                            .toDto();
        assertThat(candidate.approvalStatuses().get(0).assignee(), is(equalTo(assignee)));
        assertThat(candidate.approvalStatuses().get(0).finalizedBy(), is(not(equalTo(assignee))));
    }

    private static no.sikt.nva.nvi.common.service.dto.PeriodStatus getDefaultPeriodstatus() {
        return no.sikt.nva.nvi.common.service.dto.PeriodStatus.fromPeriodStatus(
            PeriodStatus.builder().withStatus(Status.NO_PERIOD).build());
    }

    private static URI constructId(UUID identifier) {
        return new UriWrapper(HTTPS, API_DOMAIN).addChild(BASE_PATH, "candidate", identifier.toString()).getUri();
    }

    private static UpdateStatusRequest createUpdateStatusRequest(DbStatus approved, String reason, URI institutionId) {
        return UpdateStatusRequest.builder()
                   .withInstitutionId(institutionId)
                   .withApprovalStatus(approved)
                   .withReason(reason)
                   .withUsername(randomString())
                   .build();
    }

    private static CreateNoteRequest createNoteRequest(String text, String username) {
        return new CreateNoteRequest() {
            @Override
            public String text() {
                return text;
            }

            @Override
            public String username() {
                return username;
            }
        };
    }

    private static UpsertCandidateRequest createUpsertCandidateRequest(URI... institutions) {
        return createUpsertCandidateRequest(UUID.randomUUID(), randomUri(), true, 1, InstanceType.ACADEMIC_MONOGRAPH,
                                            institutions);
    }

    private static UpsertCandidateRequest createUpsertCandidateRequest(UUID identifier, URI publicationId,
                                                                       boolean isApplicable, int creatorCount,
                                                                       final InstanceType instanceType,
                                                                       URI... institutions) {
        var creators = IntStream.of(creatorCount)
                           .mapToObj(i -> randomUri())
                           .collect(Collectors.toMap(Function.identity(), e -> List.of(institutions)));
        var points = Arrays.stream(institutions)
                         .collect(Collectors.toMap(Function.identity(), e -> randomBigDecimal()));
        return new UpsertCandidateRequest() {
            @Override
            public UUID identifier() {
                return identifier;
            }

            @Override
            public URI publicationBucketUri() {
                return randomUri();
            }

            @Override
            public URI publicationId() {
                return publicationId;
            }

            @Override
            public boolean isApplicable() {
                return isApplicable;
            }

            @Override
            public boolean isInternationalCooperation() {
                return false;
            }

            @Override
            public Map<URI, List<URI>> creators() {
                return creators;
            }

            @Override
            public String level() {
                return DbLevel.LEVEL_TWO.getValue();
            }

            @Override
            public String instanceType() {
                return instanceType.getValue();
            }

            @Override
            public PublicationDate publicationDate() {
                return new PublicationDate("2023", null, null);
            }

            @Override
            public Map<URI, BigDecimal> points() {
                return points;
            }

            @Override
            public int creatorCount() {
                return creatorCount;
            }
        };
    }
}