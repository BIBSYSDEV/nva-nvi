package no.sikt.nva.nvi.common.model.business;

import static java.util.UUID.randomUUID;
import static nva.commons.core.attempt.Try.attempt;
import static nva.commons.core.paths.UriWrapper.HTTPS;
import java.math.BigDecimal;
import java.net.URI;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
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
import no.sikt.nva.nvi.common.service.dto.ApprovalStatus;
import no.sikt.nva.nvi.common.service.dto.CandidateDto;
import no.sikt.nva.nvi.common.service.dto.NoteDto;
import no.sikt.nva.nvi.common.service.dto.NviApprovalStatus;
import no.sikt.nva.nvi.common.service.dto.PeriodStatusDto;
import no.sikt.nva.nvi.common.service.exception.CandidateNotFoundException;
import no.sikt.nva.nvi.common.service.exception.InvalidNviCandidateException;
import no.sikt.nva.nvi.common.service.exception.UnauthorizedOperationException;
import no.sikt.nva.nvi.common.service.requests.CreateNoteRequest;
import no.sikt.nva.nvi.common.service.requests.DeleteNoteRequest;
import no.sikt.nva.nvi.common.service.requests.FetchByPublicationRequest;
import no.sikt.nva.nvi.common.service.requests.FetchCandidateRequest;
import no.sikt.nva.nvi.common.service.requests.UpdateApprovalRequest;
import no.sikt.nva.nvi.common.service.requests.UpsertCandidateRequest;
import nva.commons.core.Environment;
import nva.commons.core.JacocoGenerated;
import nva.commons.core.paths.UriWrapper;

public final class CandidateBO {

    private static final String PERIOD_CLOSED_MESSAGE = "Period is closed, perform actions on candidate is forbidden!";
    private static final String PERIOD_NOT_OPENED_MESSAGE = "Period is not opened yet, perform actions on candidate is"
                                                            + " forbidden!";
    private static final String DELETE_MESSAGE_ERROR = "Can not delete message you does not own!";
    private static final Environment ENVIRONMENT = new Environment();
    private static final String BASE_PATH = ENVIRONMENT.readEnv("CUSTOM_DOMAIN_BASE_PATH");
    private static final String API_DOMAIN = ENVIRONMENT.readEnv("API_HOST");
    private static final String CANDIDATE_PATH = "candidate";
    private static final String INVALID_CANDIDATE_MESSAGE = "Candidate is missing mandatory fields";
    private static final PeriodStatus PERIOD_STATUS_NO_PERIOD = PeriodStatus.builder()
                                                                    .withStatus(Status.NO_PERIOD)
                                                                    .build();
    private final UUID identifier;
    private final URI publicationId;
    private final URI publicationBucketUri;
    private final boolean applicable;
    private final InstanceType instanceType;
    private final Level level;
    private final PublicationDate publicationDate;
    private final boolean internationalCollaboration;
    private final List<Creator> creators;
    private final Map<URI, ApprovalBO> approvals;
    private final Map<UUID, NoteBO> notes;
    private final Map<URI, BigDecimal> points;
    private final PeriodStatus periodStatus;

    private CandidateBO(CandidateRepository repository, CandidateDao candidateDao, List<ApprovalStatusDao> approvals,
                        List<NoteDao> notes, PeriodStatus periodStatus) {
        this.identifier = candidateDao.identifier();
        this.approvals = mapToApprovalsMap(repository, approvals);
        this.notes = mapToNotesMap(repository, notes);
        this.points = mapToPointsMap(candidateDao);
        this.periodStatus = periodStatus;
        this.publicationId = candidateDao.candidate().publicationId();
        this.publicationBucketUri = candidateDao.candidate().publicationBucketUri();
        this.applicable = candidateDao.candidate().applicable();
        this.instanceType = InstanceType.parse(candidateDao.candidate().instanceType().getValue());
        this.level = Level.parse(candidateDao.candidate().level().getValue());
        this.publicationDate = mapToPublicationDate(candidateDao.candidate().publicationDate());
        this.internationalCollaboration = candidateDao.candidate().internationalCollaboration();
        this.creators = mapToCreators(candidateDao.candidate().creators());
    }

    public static CandidateBO fromRequest(FetchByPublicationRequest request, CandidateRepository repository,
                                          PeriodRepository periodRepository) {
        var candidateDao = repository.findByPublicationIdDao(request.publicationId())
                               .orElseThrow(CandidateNotFoundException::new);
        var approvalDaoList = repository.fetchApprovals(candidateDao.identifier());
        var noteDaoList = repository.getNotes(candidateDao.identifier());
        var periodStatus = getPeriodStatus(periodRepository, candidateDao.candidate().publicationDate().year());
        return new CandidateBO(repository, candidateDao, approvalDaoList, noteDaoList, periodStatus);
    }

    public static CandidateBO fromRequest(FetchCandidateRequest request, CandidateRepository repository,
                                          PeriodRepository periodRepository) {
        var candidateDao = repository.findCandidateDaoById(request.identifier())
                               .orElseThrow(CandidateNotFoundException::new);
        var approvalDaoList = repository.fetchApprovals(candidateDao.identifier());
        var noteDaoList = repository.getNotes(candidateDao.identifier());
        var periodStatus = calculatePeriodStatusIfApplicable(periodRepository, candidateDao);
        return new CandidateBO(repository, candidateDao, approvalDaoList, noteDaoList, periodStatus);
    }

    public static Optional<CandidateBO> fromRequest(UpsertCandidateRequest request, CandidateRepository repository,
                                                    PeriodRepository periodRepository) {
        if (isNewCandidate(request, repository)) {
            return Optional.of(createCandidate(request, repository, periodRepository));
        }
        if (shouldBeUpdated(request, repository)) {
            return Optional.of(updateCandidate(request, repository, periodRepository));
        }
        if (shouldBeDeleted(request, repository)) {
            return Optional.of(deleteCandidate(request, repository));
        }
        return Optional.empty();
    }

    public Map<URI, ApprovalBO> getApprovals() {
        return new HashMap<>(approvals);
    }

    public UUID identifier() {
        return identifier;
    }

    public URI getPublicationId() {
        return publicationId;
    }

    public CandidateDto toDto() {
        if (isApplicable()) {
            return CandidateDto.builder()
                       .withId(constructId(identifier))
                       .withIdentifier(identifier)
                       .withPublicationId(publicationId)
                       .withApprovalStatuses(mapToApprovalDtos())
                       .withNotes(mapToNoteDtos())
                       .withPeriodStatus(mapToPeriodStatusDto())
                       .build();
        } else {
            throw new CandidateNotFoundException();
        }
    }

    public CandidateBO updateApproval(UpdateApprovalRequest input) {
        validateCandidateState();
        approvals.computeIfPresent(input.institutionId(), (uri, approvalBO) -> approvalBO.update(input));
        return this;
    }

    public CandidateBO createNote(CreateNoteRequest input, CandidateRepository repository) {
        validateCandidateState();
        var noteBO = NoteBO.fromRequest(input, identifier, repository);
        notes.put(noteBO.noteId(), noteBO);
        return this;
    }

    public CandidateBO deleteNote(DeleteNoteRequest request) {
        validateCandidateState();
        var note = notes.get(request.noteId());
        validateNoteOwner(request.username(), note.getDao());
        notes.computeIfPresent(request.noteId(), (uuid, noteBO) -> {
            noteBO.delete();
            return null;
        });
        return this;
    }

    public boolean isApplicable() {
        return applicable;
    }

    @JacocoGenerated
    public Map<URI, BigDecimal> getPoints() {
        return points;
    }

    @JacocoGenerated
    public URI getBucketUri() {
        return publicationBucketUri;
    }

    public PeriodStatus periodStatus() {
        return periodStatus;
    }

    @Override
    @JacocoGenerated
    public int hashCode() {
        int result = identifier != null ? identifier.hashCode() : 0;
        result = 31 * result + (publicationId != null ? publicationId.hashCode() : 0);
        result = 31 * result + (publicationBucketUri != null ? publicationBucketUri.hashCode() : 0);
        result = 31 * result + (applicable ? 1 : 0);
        result = 31 * result + (instanceType != null ? instanceType.hashCode() : 0);
        result = 31 * result + (level != null ? level.hashCode() : 0);
        result = 31 * result + (publicationDate != null ? publicationDate.hashCode() : 0);
        result = 31 * result + (internationalCollaboration ? 1 : 0);
        result = 31 * result + (creators != null ? creators.hashCode() : 0);
        result = 31 * result + (approvals != null ? approvals.hashCode() : 0);
        result = 31 * result + (notes != null ? notes.hashCode() : 0);
        result = 31 * result + (points != null ? points.hashCode() : 0);
        result = 31 * result + (periodStatus != null ? periodStatus.hashCode() : 0);
        return result;
    }

    @Override
    @JacocoGenerated
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }

        CandidateBO that = (CandidateBO) o;

        if (applicable != that.applicable) {
            return false;
        }
        if (internationalCollaboration != that.internationalCollaboration) {
            return false;
        }
        if (!Objects.equals(identifier, that.identifier)) {
            return false;
        }
        if (!Objects.equals(publicationId, that.publicationId)) {
            return false;
        }
        if (!Objects.equals(publicationBucketUri, that.publicationBucketUri)) {
            return false;
        }
        if (instanceType != that.instanceType) {
            return false;
        }
        if (level != that.level) {
            return false;
        }
        if (!Objects.equals(publicationDate, that.publicationDate)) {
            return false;
        }
        if (!Objects.equals(creators, that.creators)) {
            return false;
        }
        if (!Objects.equals(approvals, that.approvals)) {
            return false;
        }
        if (!Objects.equals(notes, that.notes)) {
            return false;
        }
        if (!Objects.equals(points, that.points)) {
            return false;
        }
        return Objects.equals(periodStatus, that.periodStatus);
    }

    private static PeriodStatus calculatePeriodStatusIfApplicable(PeriodRepository periodRepository,
                                                                  CandidateDao candidateDao) {
        return candidateDao.candidate().applicable() ? getPeriodStatus(periodRepository, candidateDao.candidate()
                                                                                             .publicationDate()
                                                                                             .year())
                   : PERIOD_STATUS_NO_PERIOD;
    }

    private static boolean shouldBeDeleted(UpsertCandidateRequest request, CandidateRepository repository) {
        return !request.isApplicable() && isExistingCandidate(request, repository);
    }

    private static boolean shouldBeUpdated(UpsertCandidateRequest request, CandidateRepository repository) {
        return request.isApplicable() && isExistingCandidate(request, repository);
    }

    private static boolean isNewCandidate(UpsertCandidateRequest request, CandidateRepository repository) {
        return request.isApplicable() && isNotExistingCandidate(request, repository);
    }

    private static boolean isNotExistingCandidate(UpsertCandidateRequest request, CandidateRepository repository) {
        return !isExistingCandidate(request, repository);
    }

    private static boolean isNotNoteOwner(String username, NoteDao dao) {
        return !dao.note().user().value().equals(username);
    }

    private static CandidateBO deleteCandidate(UpsertCandidateRequest request, CandidateRepository repository) {
        var existingCandidateDao = repository.findByPublicationIdDao(request.publicationId())
                                       .orElseThrow(CandidateNotFoundException::new);
        var nonApplicableCandidate = updateCandidateToNonApplicable(existingCandidateDao, request);
        repository.updateCandidateAndRemovingApprovals(existingCandidateDao.identifier(), nonApplicableCandidate);

        return new CandidateBO(repository, nonApplicableCandidate, Collections.emptyList(), Collections.emptyList(),
                               PeriodStatus.builder().withStatus(Status.NO_PERIOD).build());
    }

    private static CandidateBO updateCandidate(UpsertCandidateRequest request, CandidateRepository repository,
                                               PeriodRepository periodRepository) {
        validateCandidate(request);
        var existingCandidateDao = repository.findByPublicationIdDao(request.publicationId())
                                       .orElseThrow(CandidateNotFoundException::new);
        if (shouldResetCandidate(request, existingCandidateDao)) {
            return resetCandidate(request, repository, periodRepository, existingCandidateDao);
        } else {
            return updateCandidateKeepingApprovalsAndNotes(request, repository, periodRepository, existingCandidateDao);
        }
    }

    private static CandidateBO resetCandidate(UpsertCandidateRequest request, CandidateRepository repository,
                                              PeriodRepository periodRepository, CandidateDao existingCandidateDao) {
        var newApprovals = mapToApprovals(request.institutionPoints());
        var newCandidateDao = updateCandidateDaoFromRequest(existingCandidateDao, request);
        repository.updateCandidate(existingCandidateDao.identifier(), newCandidateDao, newApprovals);
        var notes = repository.getNotes(existingCandidateDao.identifier());
        var periodStatus = getPeriodStatus(periodRepository, existingCandidateDao.candidate().publicationDate().year());
        var approvals = mapToApprovalsDaos(newCandidateDao.identifier(), newApprovals);
        return new CandidateBO(repository, newCandidateDao, approvals, notes, periodStatus);
    }

    private static CandidateBO updateCandidateKeepingApprovalsAndNotes(UpsertCandidateRequest request,
                                                                       CandidateRepository repository,
                                                                       PeriodRepository periodRepository,
                                                                       CandidateDao existingCandidateDao) {
        var updatedCandidate = updateCandidateDaoFromRequest(existingCandidateDao, request);
        var approvalDaoList = repository.fetchApprovals(updatedCandidate.identifier());
        repository.updateCandidate(updatedCandidate);
        var noteDaoList = repository.getNotes(updatedCandidate.identifier());
        var periodStatus = getPeriodStatus(periodRepository, updatedCandidate.candidate().publicationDate().year());
        return new CandidateBO(repository, updatedCandidate, approvalDaoList, noteDaoList, periodStatus);
    }

    private static boolean shouldResetCandidate(UpsertCandidateRequest request, CandidateDao candidate) {
        return levelIsUpdated(request, candidate) || instanceTypeIsUpdated(request, candidate) || creatorsAreUpdated(
            request, candidate) || pointsAreUpdated(request, candidate) || publicationYearIsUpdated(request, candidate);
    }

    private static boolean publicationYearIsUpdated(UpsertCandidateRequest request, CandidateDao candidate) {
        return !request.publicationDate().year().equals(candidate.candidate().publicationDate().year());
    }

    private static boolean pointsAreUpdated(UpsertCandidateRequest request, CandidateDao existingCandidateDao) {
        return !Objects.equals(request.institutionPoints(), mapToPointsMap(existingCandidateDao));
    }

    private static boolean creatorsAreUpdated(UpsertCandidateRequest request, CandidateDao existingCandidateDao) {
        return !Objects.equals(mapToCreators(request.creators()), existingCandidateDao.candidate().creators());
    }

    private static boolean instanceTypeIsUpdated(UpsertCandidateRequest request, CandidateDao existingCandidateDao) {
        return !Objects.equals(request.instanceType(), existingCandidateDao.candidate().instanceType().getValue());
    }

    private static boolean levelIsUpdated(UpsertCandidateRequest request, CandidateDao existingCandidateDao) {
        return !Objects.equals(request.level(), existingCandidateDao.candidate().level().getValue());
    }

    private static CandidateBO createCandidate(UpsertCandidateRequest request, CandidateRepository repository,
                                               PeriodRepository periodRepository) {
        validateCandidate(request);
        var candidateDao = repository.createDao(mapToCandidate(request), mapToApprovals(request.institutionPoints()));
        var approvals1 = repository.fetchApprovals(candidateDao.identifier());
        var notes1 = repository.getNotes(candidateDao.identifier());
        var periodStatus1 = getPeriodStatus(periodRepository, candidateDao.candidate().publicationDate().year());

        return new CandidateBO(repository, candidateDao, approvals1, notes1, periodStatus1);
    }

    private static List<ApprovalStatusDao> mapToApprovalsDaos(UUID identifier, List<DbApprovalStatus> newApprovals) {
        return newApprovals.stream().map(app -> mapToApprovalDao(identifier, app)).toList();
    }

    private static ApprovalStatusDao mapToApprovalDao(UUID identifier, DbApprovalStatus app) {
        return ApprovalStatusDao.builder().identifier(identifier).approvalStatus(app).build();
    }

    private static void validateCandidate(UpsertCandidateRequest candidate) {
        attempt(() -> {
            assertIsCandidate(candidate);
            Objects.requireNonNull(candidate.publicationBucketUri());
            Objects.requireNonNull(candidate.institutionPoints());
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
        return candidateDao.candidate()
                   .points()
                   .stream()
                   .collect(Collectors.toMap(DbInstitutionPoints::institutionId, DbInstitutionPoints::points));
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
                   .orElse(PERIOD_STATUS_NO_PERIOD);
    }

    private static URI constructId(UUID identifier) {
        return new UriWrapper(HTTPS, API_DOMAIN).addChild(BASE_PATH, CANDIDATE_PATH, identifier.toString()).getUri();
    }

    private static List<DbApprovalStatus> mapToApprovals(Map<URI, BigDecimal> points) {
        return points.keySet().stream().map(CandidateBO::mapToApproval).toList();
    }

    private static DbApprovalStatus mapToApproval(URI institutionId) {
        return DbApprovalStatus.builder().institutionId(institutionId).status(DbStatus.PENDING).build();
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
                   .points(mapToPoints(request.institutionPoints()))
                   .build();
    }

    private static List<DbInstitutionPoints> mapToPoints(Map<URI, BigDecimal> points) {
        return points.entrySet().stream().map(e -> new DbInstitutionPoints(e.getKey(), e.getValue())).toList();
    }

    private static DbPublicationDate mapToPublicationDate(
        no.sikt.nva.nvi.common.service.requests.PublicationDate publicationDate) {
        return DbPublicationDate.builder()
                   .year(publicationDate.year())
                   .month(publicationDate.month())
                   .day(publicationDate.day())
                   .build();
    }

    private static PublicationDate mapToPublicationDate(DbPublicationDate publicationDate) {
        return PublicationDate.builder()
                   .withYear(publicationDate.year())
                   .withMonth(publicationDate.month())
                   .withDay(publicationDate.day())
                   .build();
    }

    private static List<DbCreator> mapToCreators(Map<URI, List<URI>> creators) {
        return creators.entrySet().stream().map(e -> new DbCreator(e.getKey(), e.getValue())).toList();
    }

    private static List<Creator> mapToCreators(List<DbCreator> creators) {
        return creators.stream()
                   .map(creator -> Creator.builder().withCreatorId(creator.creatorId())
                                       .withAffiliations(new HashSet<>(creator.affiliations())).build())
                   .toList();
    }

    private static boolean isExistingCandidate(UpsertCandidateRequest publicationId, CandidateRepository repository) {
        return repository.findByPublicationId(publicationId.publicationId()).isPresent();
    }

    private static CandidateDao updateCandidateDaoFromRequest(CandidateDao candidateDao,
                                                              UpsertCandidateRequest request) {
        return candidateDao.copy()
                   .candidate(candidateDao.candidate()
                                  .copy()
                                  .creators(mapToCreators(request.creators()))
                                  .points(mapToPoints(request.institutionPoints()))
                                  .publicationDate(mapToPublicationDate(request.publicationDate()))
                                  .instanceType(InstanceType.parse(request.instanceType()))
                                  .level(DbLevel.parse(request.level()))
                                  .applicable(request.isApplicable())
                                  .internationalCollaboration(request.isInternationalCollaboration())
                                  .creatorCount(request.creatorCount())
                                  .build())
                   .version(randomUUID().toString())
                   .build();
    }

    private static CandidateDao updateCandidateToNonApplicable(CandidateDao candidateDao,
                                                               UpsertCandidateRequest request) {
        return candidateDao.copy()
                   .candidate(candidateDao.candidate().copy().applicable(request.isApplicable()).build())
                   .build();
    }

    private static String mapToUsernameString(Username assignee) {
        return assignee != null ? assignee.value() : null;
    }

    private void validateNoteOwner(String username, NoteDao dao) {
        if (isNotNoteOwner(username, dao)) {
            throw new UnauthorizedOperationException(DELETE_MESSAGE_ERROR);
        }
    }

    private void validateCandidateState() {
        if (Status.CLOSED_PERIOD.equals(periodStatus.status())) {
            throw new IllegalStateException(PERIOD_CLOSED_MESSAGE);
        }
        if (Status.NO_PERIOD.equals(periodStatus.status()) || Status.UNOPENED_PERIOD.equals(periodStatus.status())) {
            throw new IllegalStateException(PERIOD_NOT_OPENED_MESSAGE);
        }
    }

    private PeriodStatusDto mapToPeriodStatusDto() {
        return PeriodStatusDto.fromPeriodStatus(periodStatus);
    }

    private List<NoteDto> mapToNoteDtos() {
        return this.notes.values().stream().map(NoteBO::toDto).toList();
    }

    private List<ApprovalStatus> mapToApprovalDtos() {
        return approvals.values().stream().map(this::mapToApprovalDto).toList();
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
