package no.sikt.nva.nvi.common.service;

import static nva.commons.core.attempt.Try.attempt;
import static nva.commons.core.paths.UriWrapper.HTTPS;
import java.math.BigDecimal;
import java.net.URI;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;
import no.sikt.nva.nvi.common.db.ApprovalStatusDao;
import no.sikt.nva.nvi.common.db.ApprovalStatusDao.DbApprovalStatus;
import no.sikt.nva.nvi.common.db.ApprovalStatusDao.DbStatus;
import no.sikt.nva.nvi.common.db.CandidateDao;
import no.sikt.nva.nvi.common.db.CandidateDao.DbCandidate;
import no.sikt.nva.nvi.common.db.CandidateDao.DbCreator;
import no.sikt.nva.nvi.common.db.CandidateDao.DbInstitutionPoints;
import no.sikt.nva.nvi.common.db.CandidateDao.DbLevel;
import no.sikt.nva.nvi.common.db.CandidateDao.DbPublicationDate;
import no.sikt.nva.nvi.common.db.CandidateRepository;
import no.sikt.nva.nvi.common.db.NoteDao;
import no.sikt.nva.nvi.common.db.PeriodRepository;
import no.sikt.nva.nvi.common.db.PeriodStatus;
import no.sikt.nva.nvi.common.db.PeriodStatus.Status;
import no.sikt.nva.nvi.common.db.model.InstanceType;
import no.sikt.nva.nvi.common.db.model.Username;
import no.sikt.nva.nvi.common.model.InvalidNviCandidateException;
import no.sikt.nva.nvi.common.model.UpdateApprovalRequest;
import no.sikt.nva.nvi.common.service.dto.ApprovalStatus;
import no.sikt.nva.nvi.common.service.dto.CandidateDto;
import no.sikt.nva.nvi.common.service.dto.Note;
import no.sikt.nva.nvi.common.service.dto.NviApprovalStatus;
import no.sikt.nva.nvi.common.service.dto.PeriodStatusDto;
import no.sikt.nva.nvi.common.service.exception.CandidateNotFoundException;
import no.sikt.nva.nvi.common.service.exception.IllegalOperationException;
import no.sikt.nva.nvi.common.service.requests.CreateNoteRequest;
import no.sikt.nva.nvi.common.service.requests.DeleteNoteRequest;
import no.sikt.nva.nvi.common.service.requests.FetchByPublicationRequest;
import no.sikt.nva.nvi.common.service.requests.FetchCandidateRequest;
import no.sikt.nva.nvi.common.service.requests.PublicationDate;
import no.sikt.nva.nvi.common.service.requests.UpsertCandidateRequest;
import nva.commons.core.Environment;
import nva.commons.core.JacocoGenerated;
import nva.commons.core.paths.UriWrapper;

public final class CandidateBO {

    public static final String PERIOD_CLOSED_MESSAGE = "Period is closed, perform actions on candidate is forbidden!";
    public static final String PERIOD_NOT_OPENED_MESSAGE = "Period is not opened yet, perform actions on candidate is"
                                                           + " forbidden!";
    public static final String CANDIDATE_NOT_APPLICABLE_MESSAGE = "Candidate is not applicable";
    private static final Environment ENVIRONMENT = new Environment();
    private static final String BASE_PATH = ENVIRONMENT.readEnv("CUSTOM_DOMAIN_BASE_PATH");
    private static final String API_DOMAIN = ENVIRONMENT.readEnv("API_HOST");
    private static final String CANDIDATE_PATH = "candidate";
    private static final String INVALID_CANDIDATE_MESSAGE = "Candidate is missing mandatory fields";
    private final CandidateRepository repository;
    private final UUID identifier;
    private final CandidateDao original;
    private final Map<URI, ApprovalBO> approvals;
    private final Map<UUID, NoteBO> notes;
    private final Map<URI, BigDecimal> points;
    private final PeriodStatus periodStatus;

    private CandidateBO(CandidateRepository repository,
                        CandidateDao candidateDao,
                        List<ApprovalStatusDao> approvals,
                        List<NoteDao> notes,
                        PeriodStatus periodStatus) {
        this.repository = repository;
        this.identifier = candidateDao.identifier();
        this.original = candidateDao;
        this.approvals = mapToApprovalsMap(repository, approvals);
        this.notes = mapToNotesMap(repository, notes);
        this.points = mapToPointsMap(candidateDao);
        this.periodStatus = periodStatus;
    }

    public static CandidateBO fromRequest(FetchByPublicationRequest request,
                                          CandidateRepository repository,
                                          PeriodRepository periodRepository) {
        var candidateDao = repository.findByPublicationIdDao(request.publicationId())
                               .orElseThrow(CandidateNotFoundException::new);
        var approvalDaoList = repository.fetchApprovals(candidateDao.identifier());
        var noteDaoList = repository.getNotes(candidateDao.identifier());
        var periodStatus = getPeriodStatus(periodRepository, candidateDao.candidate().publicationDate().year());
        return new CandidateBO(repository, candidateDao, approvalDaoList, noteDaoList, periodStatus);
    }

    public static CandidateBO fromRequest(FetchCandidateRequest request,
                                          CandidateRepository repository,
                                          PeriodRepository periodRepository) {
        var candidateDao = repository.findCandidateDaoById(request.identifier())
                               .orElseThrow(CandidateNotFoundException::new);
        if (isNotApplicable(candidateDao)) {
            throw new CandidateNotFoundException();
        }
        var approvalDaoList = repository.fetchApprovals(candidateDao.identifier());
        var noteDaoList = repository.getNotes(candidateDao.identifier());
        var periodStatus = getPeriodStatus(periodRepository, candidateDao.candidate().publicationDate().year());
        return new CandidateBO(repository, candidateDao, approvalDaoList, noteDaoList, periodStatus);
    }

    public static CandidateBO fromRequest(UpsertCandidateRequest request,
                                          CandidateRepository repository,
                                          PeriodRepository periodRepository) {
        if (request.isApplicable()) {
            if (!isExistingCandidate(request, repository)) {
                return createCandidate(request, repository, periodRepository);
            } else {
                return updateCandidate(request, repository, periodRepository);
            }
        } else if (isExistingCandidate(request, repository)) {
            return deleteCandidate(request, repository);
        }
        throw new IllegalOperationException();
    }

    public UUID identifier() {
        return identifier;
    }

    public URI publicationId() {
        return original.candidate().publicationId();
    }

    public CandidateDto toDto() {
        return CandidateDto.builder()
                   .withId(constructId(identifier))
                   .withIdentifier(identifier)
                   .withId(constructId(identifier))
                   .withPublicationId(original.candidate().publicationId())
                   .withApprovalStatuses(mapToApprovalDtos())
                   .withNotes(mapToNoteDtos())
                   .withPeriodStatus(mapToPeriodStatusDto())
                   .build();
    }

    public CandidateBO updateApproval(UpdateApprovalRequest input) {
        validateCandidateState();
        approvals.computeIfPresent(input.institutionId(), (uri, approvalBO) -> approvalBO.update(input));
        return this;
    }

    public CandidateBO createNote(CreateNoteRequest input) {
        var noteBO = NoteBO.fromRequest(input, identifier, repository);
        notes.put(noteBO.noteId(), noteBO);
        return this;
    }

    public CandidateBO deleteNote(DeleteNoteRequest input) {
        notes.computeIfPresent(input.noteId(), (uuid, noteBO) -> {
            noteBO.delete();
            return null;
        });
        return this;
    }

    private static CandidateBO deleteCandidate(UpsertCandidateRequest request,
                                               CandidateRepository repository) {
        var existingCandidateDao = repository.findByPublicationIdDao(request.publicationId())
                                       .orElseThrow(CandidateNotFoundException::new);
        var nonApplicableCandidate = updateCandidateDaoFromRequest(existingCandidateDao, request);
        repository.updateCandidateAndRemovingApprovals(request.identifier(), nonApplicableCandidate);

        return new CandidateBO(repository, nonApplicableCandidate, Collections.emptyList(),
                               Collections.emptyList(),
                               PeriodStatus.builder().withStatus(Status.NO_PERIOD).build());
    }

    private static CandidateBO updateCandidate(UpsertCandidateRequest request,
                                               CandidateRepository repository,
                                               PeriodRepository periodRepository) {
        validateCandidate(request);
        var existingCandidateDao = repository.findByPublicationIdDao(request.publicationId())
                                       .orElseThrow(CandidateNotFoundException::new);
        var newApprovals = mapToApprovals(request.points());
        var newCandidateDao = updateCandidateDaoFromRequest(existingCandidateDao, request);
        repository.updateCandidate(existingCandidateDao.identifier(), newCandidateDao, newApprovals);
        var notes = repository.getNotes(existingCandidateDao.identifier());
        var periodStatus = getPeriodStatus(periodRepository,
                                           existingCandidateDao.candidate().publicationDate().year());
        var approvals = mapToApprovalsDaos(newCandidateDao.identifier(), newApprovals);
        return new CandidateBO(repository, newCandidateDao, approvals, notes, periodStatus);
    }

    private static CandidateBO createCandidate(UpsertCandidateRequest request,
                                               CandidateRepository repository,
                                               PeriodRepository periodRepository) {
        validateCandidate(request);
        var candidateDao = repository.createDao(mapToCandidate(request), mapToApprovals(request.points()));
        var approvals1 = repository.fetchApprovals(candidateDao.identifier());
        var notes1 = repository.getNotes(candidateDao.identifier());
        var periodStatus1 = getPeriodStatus(periodRepository, candidateDao.candidate().publicationDate().year());

        return new CandidateBO(repository, candidateDao, approvals1, notes1, periodStatus1);
    }

    private static List<ApprovalStatusDao> mapToApprovalsDaos(UUID identifier, List<DbApprovalStatus> newApprovals) {
        return newApprovals.stream()
                   .map(app -> mapToApprovalDao(identifier, app))
                   .toList();
    }

    private static ApprovalStatusDao mapToApprovalDao(UUID identifier, DbApprovalStatus app) {
        return ApprovalStatusDao.builder()
                   .identifier(identifier)
                   .approvalStatus(app)
                   .build();
    }

    private static void validateCandidate(UpsertCandidateRequest candidate) {
        attempt(() -> {
            assertIsCandidate(candidate);
            Objects.requireNonNull(candidate.publicationBucketUri());
            Objects.requireNonNull(candidate.points());
            Objects.requireNonNull(candidate.publicationId());
            Objects.requireNonNull(candidate.creators());
            Objects.requireNonNull(candidate.level());
            Objects.requireNonNull(candidate.publicationDate());
            return candidate;
        }).orElseThrow(failure -> new InvalidNviCandidateException(INVALID_CANDIDATE_MESSAGE));
    }

    private static void assertIsCandidate(UpsertCandidateRequest candidate) {
        if (InstanceType.NON_CANDIDATE.getValue().equals(candidate.instanceType())) {
            throw new InvalidNviCandidateException("Can not update invalid candidate");
        }
    }

    private static Map<URI, BigDecimal> mapToPointsMap(CandidateDao candidateDao) {
        return candidateDao.candidate().points().stream()
                   .collect(Collectors.toMap(DbInstitutionPoints::institutionId,
                                             DbInstitutionPoints::points));
    }

    private static Map<UUID, NoteBO> mapToNotesMap(CandidateRepository repository, List<NoteDao> notes) {
        return notes.stream()
                   .map(dao -> new NoteBO(repository, dao.identifier(), dao))
                   .collect(Collectors.toMap(NoteBO::noteId, Function.identity()));
    }

    private static Map<URI, ApprovalBO> mapToApprovalsMap(CandidateRepository repository,
                                                          List<ApprovalStatusDao> approvals) {
        return approvals.stream()
                   .map(dao -> new ApprovalBO(repository, dao.identifier(), dao))
                   .collect(Collectors.toMap(ApprovalBO::institutionId, Function.identity()));
    }

    private static PeriodStatus getPeriodStatus(PeriodRepository periodRepository, String year) {
        return periodRepository.findByPublishingYear(year)
                   .map(PeriodStatus::fromPeriod)
                   .orElse(PeriodStatus.builder().withStatus(Status.NO_PERIOD).build());
    }

    private static URI constructId(UUID identifier) {
        return new UriWrapper(HTTPS, API_DOMAIN)
                   .addChild(BASE_PATH, CANDIDATE_PATH, identifier.toString())
                   .getUri();
    }

    private static Note mapToNoteDto(NoteBO bo) {
        var note = bo.note().note();
        return new Note(bo.noteId(),
                        mapToUsernameString(note.user()),
                        note.text(),
                        note.createdDate());
    }

    private static List<DbApprovalStatus> mapToApprovals(Map<URI, BigDecimal> points) {
        return points.keySet().stream().map(CandidateBO::mapToApproval).toList();
    }

    private static DbApprovalStatus mapToApproval(URI institutionId) {
        return DbApprovalStatus.builder()
                   .institutionId(institutionId)
                   .status(DbStatus.PENDING)
                   .build();
    }

    private static DbCandidate mapToCandidate(UpsertCandidateRequest request) {
        return DbCandidate.builder()
                   .publicationId(request.publicationId())
                   .publicationBucketUri(request.publicationBucketUri())
                   .applicable(request.isApplicable())
                   .creators(mapToCreators(request.creators()))
                   .level(DbLevel.parse(request.level()))
                   .instanceType(InstanceType.parse(request.instanceType()))
                   .publicationDate(mapToPublicationDate(request.publicationDate()))
                   .points(mapToPoints(request.points()))
                   .build();
    }

    private static List<DbInstitutionPoints> mapToPoints(Map<URI, BigDecimal> points) {
        return points
                   .entrySet()
                   .stream()
                   .map(e -> new DbInstitutionPoints(e.getKey(), e.getValue()))
                   .toList();
    }

    private static DbPublicationDate mapToPublicationDate(PublicationDate publicationDate) {
        return DbPublicationDate.builder()
                   .year(publicationDate.year())
                   .month(publicationDate.month())
                   .day(publicationDate.day())
                   .build();
    }

    private static List<DbCreator> mapToCreators(Map<URI, List<URI>> creators) {
        return creators
                   .entrySet()
                   .stream()
                   .map(e -> new DbCreator(e.getKey(),
                                           e.getValue()))
                   .toList();
    }

    private static boolean isExistingCandidate(UpsertCandidateRequest publicationId, CandidateRepository repository) {
        return repository.findByPublicationId(publicationId.publicationId()).isPresent();
    }

    private static CandidateDao updateCandidateDaoFromRequest(CandidateDao candidateDao,
                                                              UpsertCandidateRequest request) {
        return candidateDao.copy()
                   .candidate(candidateDao.candidate().copy()
                                  .creators(mapToCreators(request.creators()))
                                  .points(mapToPoints(request.points()))
                                  .publicationDate(mapToPublicationDate(request.publicationDate()))
                                  .instanceType(InstanceType.parse(request.instanceType()))
                                  .level(DbLevel.parse(request.level()))
                                  .applicable(request.isApplicable())
                                  .internationalCollaboration(request.isInternationalCooperation())
                                  .creatorCount(request.creatorCount())
                                  .build())
                   .build();
    }

    private static String mapToUsernameString(Username assignee) {
        return assignee != null ? assignee.value() : null;
    }

    private void validateCandidateState() {
        if (periodStatus.status().equals(Status.CLOSED_PERIOD)) {
            throw new IllegalStateException(PERIOD_CLOSED_MESSAGE);
        }
        if (Status.NO_PERIOD.equals(periodStatus.status()) || Status.UNOPENED_PERIOD.equals(periodStatus.status())) {
            throw new IllegalStateException(PERIOD_NOT_OPENED_MESSAGE);
        }
        if (isNotApplicable()) {
            throw new IllegalStateException(CANDIDATE_NOT_APPLICABLE_MESSAGE);
        }
    }

    private static boolean isNotApplicable(CandidateDao candidateDao) {
        return !isApplicable(candidateDao);
    }

    private boolean isNotApplicable() {
        return !isApplicable();
    }

    private static boolean isApplicable(CandidateDao candidateDao) {
        return candidateDao.candidate().applicable();
    }

    private boolean isApplicable() {
        return original.candidate().applicable();
    }

    private PeriodStatusDto mapToPeriodStatusDto() {
        return PeriodStatusDto.fromPeriodStatus(periodStatus);
    }

    private List<Note> mapToNoteDtos() {
        return this.notes.values()
                   .stream()
                   .map(CandidateBO::mapToNoteDto)
                   .toList();
    }

    private List<ApprovalStatus> mapToApprovalDtos() {
        return approvals.values().stream()
                   .map(this::mapToApprovalDto)
                   .toList();
    }

    private ApprovalStatus mapToApprovalDto(ApprovalBO bo) {
        var approval = bo.approval().approvalStatus();
        return ApprovalStatus.builder()
                   .withInstitutionId(approval.institutionId())
                   .withStatus(mapToNviApprovalStatus(approval.status()))
                   .withAssignee(mapToUsernameString(approval.assignee()))
                   .withFinalizedBy(mapToUsernameString(approval.finalizedBy()))
                   .withFinalizedDate(approval.finalizedDate())
                   .withPoints(points.get(approval.institutionId()))
                   .withReason(approval.reason())
                   .build();
    }

    @JacocoGenerated //TODO bug when no need for default
    private NviApprovalStatus mapToNviApprovalStatus(DbStatus status) {
        return switch (status) {
            case APPROVED -> NviApprovalStatus.APPROVED;
            case PENDING -> NviApprovalStatus.PENDING;
            case REJECTED -> NviApprovalStatus.REJECTED;
        };
    }
}
