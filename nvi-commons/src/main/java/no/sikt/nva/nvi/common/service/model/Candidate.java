package no.sikt.nva.nvi.common.service.model;

import static java.util.UUID.randomUUID;
import static no.sikt.nva.nvi.common.utils.DecimalUtils.adjustScaleAndRoundingMode;
import static nva.commons.core.attempt.Try.attempt;
import static nva.commons.core.paths.UriWrapper.HTTPS;
import java.math.BigDecimal;
import java.net.URI;
import java.util.Collections;
import java.util.HashMap;
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
import no.sikt.nva.nvi.common.db.model.ChannelType;
import no.sikt.nva.nvi.common.db.model.InstanceType;
import no.sikt.nva.nvi.common.model.InvalidNviCandidateException;
import no.sikt.nva.nvi.common.model.UpdateApprovalRequest;
import no.sikt.nva.nvi.common.service.dto.ApprovalDto;
import no.sikt.nva.nvi.common.service.dto.CandidateDto;
import no.sikt.nva.nvi.common.service.dto.NoteDto;
import no.sikt.nva.nvi.common.service.dto.PeriodStatusDto;
import no.sikt.nva.nvi.common.service.exception.CandidateNotFoundException;
import no.sikt.nva.nvi.common.service.model.PublicationDetails.Creator;
import no.sikt.nva.nvi.common.service.model.PublicationDetails.PublicationDate;
import no.sikt.nva.nvi.common.service.requests.CreateNoteRequest;
import no.sikt.nva.nvi.common.service.requests.DeleteNoteRequest;
import no.sikt.nva.nvi.common.service.requests.FetchByPublicationRequest;
import no.sikt.nva.nvi.common.service.requests.FetchCandidateRequest;
import no.sikt.nva.nvi.common.service.requests.UpdateNonCandidateRequest;
import no.sikt.nva.nvi.common.service.requests.UpsertCandidateRequest;
import nva.commons.core.Environment;
import nva.commons.core.JacocoGenerated;
import nva.commons.core.paths.UriWrapper;

public final class Candidate {

    private static final Environment ENVIRONMENT = new Environment();
    private static final String BASE_PATH = ENVIRONMENT.readEnv("CUSTOM_DOMAIN_BASE_PATH");
    private static final String API_DOMAIN = ENVIRONMENT.readEnv("API_HOST");
    private static final String CANDIDATE_PATH = "candidate";
    private static final String PERIOD_CLOSED_MESSAGE = "Period is closed, perform actions on candidate is forbidden!";
    private static final String PERIOD_NOT_OPENED_MESSAGE = "Period is not opened yet, perform actions on candidate is"
                                                            + " forbidden!";
    private static final String INVALID_CANDIDATE_MESSAGE = "Candidate is missing mandatory fields";
    private static final PeriodStatus PERIOD_STATUS_NO_PERIOD = PeriodStatus.builder()
                                                                    .withStatus(Status.NO_PERIOD)
                                                                    .build();
    private final CandidateRepository repository;
    private final UUID identifier;
    private final boolean applicable;
    private final Map<URI, Approval> approvals;
    private final Map<UUID, Note> notes;
    private final Map<URI, BigDecimal> institutionPoints;
    private final BigDecimal totalPoints;
    private final PeriodStatus periodStatus;
    private final PublicationDetails publicationDetails;
    private final BigDecimal basePoints;
    private final boolean internationalCollaboration;
    private final BigDecimal collaborationFactor;
    private final int creatorShareCount;

    private Candidate(CandidateRepository repository, CandidateDao candidateDao, List<ApprovalStatusDao> approvals,
                      List<NoteDao> notes, PeriodStatus periodStatus) {
        this.repository = repository;
        this.identifier = candidateDao.identifier();
        this.applicable = candidateDao.candidate().applicable();
        this.approvals = mapToApprovalsMap(repository, approvals);
        this.notes = mapToNotesMap(repository, notes);
        this.institutionPoints = mapToPointsMap(candidateDao);
        this.totalPoints = candidateDao.candidate().totalPoints();
        this.periodStatus = periodStatus;
        this.publicationDetails = mapToPublicationDetails(candidateDao);
        this.basePoints = candidateDao.candidate().basePoints();
        this.internationalCollaboration = candidateDao.candidate().internationalCollaboration();
        this.collaborationFactor = candidateDao.candidate().collaborationFactor();
        this.creatorShareCount = candidateDao.candidate().creatorShareCount();
    }

    public static Candidate fetchByPublicationId(FetchByPublicationRequest request, CandidateRepository repository,
                                                 PeriodRepository periodRepository) {
        var candidateDao = repository.findByPublicationId(request.publicationId())
                               .orElseThrow(CandidateNotFoundException::new);
        var approvalDaoList = repository.fetchApprovals(candidateDao.identifier());
        var noteDaoList = repository.getNotes(candidateDao.identifier());
        var periodStatus = findPeriodStatus(periodRepository, candidateDao.candidate().publicationDate().year());
        return new Candidate(repository, candidateDao, approvalDaoList, noteDaoList, periodStatus);
    }

    public static Candidate fetch(FetchCandidateRequest request, CandidateRepository repository,
                                  PeriodRepository periodRepository) {
        var candidateDao = repository.findCandidateById(request.identifier())
                               .orElseThrow(CandidateNotFoundException::new);
        var approvalDaoList = repository.fetchApprovals(candidateDao.identifier());
        var noteDaoList = repository.getNotes(candidateDao.identifier());
        var periodStatus = calculatePeriodStatusIfApplicable(periodRepository, candidateDao);
        return new Candidate(repository, candidateDao, approvalDaoList, noteDaoList, periodStatus);
    }

    public static Optional<Candidate> upsert(UpsertCandidateRequest request, CandidateRepository repository,
                                             PeriodRepository periodRepository) {
        if (isNotExistingCandidate(request, repository)) {
            return Optional.of(createCandidate(request, repository, periodRepository));
        }
        if (isExistingCandidate(request.publicationId(), repository)) {
            return Optional.of(updateCandidate(request, repository, periodRepository));
        }
        return Optional.empty();
    }

    public static Optional<Candidate> updateNonCandidate(UpdateNonCandidateRequest request,
                                                         CandidateRepository repository) {
        if (isExistingCandidate(request.publicationId(), repository)) {
            return Optional.of(deleteCandidate(request, repository));
        }
        return Optional.empty();
    }

    public PublicationDetails getPublicationDetails() {
        return publicationDetails;
    }

    public PeriodStatus getPeriodStatus() {
        return periodStatus;
    }

    public UUID getIdentifier() {
        return identifier;
    }

    public boolean isApplicable() {
        return applicable;
    }

    public Map<URI, BigDecimal> getInstitutionPoints() {
        return institutionPoints;
    }

    public Map<URI, Approval> getApprovals() {
        return new HashMap<>(approvals);
    }

    public BigDecimal getBasePoints() {
        return basePoints;
    }

    public boolean isInternationalCollaboration() {
        return internationalCollaboration;
    }

    public BigDecimal getCollaborationFactor() {
        return collaborationFactor;
    }

    public int getCreatorShareCount() {
        return creatorShareCount;
    }

    public BigDecimal getTotalPoints() {
        return totalPoints;
    }

    public CandidateDto toDto() {
        return CandidateDto.builder()
                   .withId(constructId(identifier))
                   .withIdentifier(identifier)
                   .withPublicationId(publicationDetails.publicationId())
                   .withApprovalStatuses(mapToApprovalDtos())
                   .withNotes(mapToNoteDtos())
                   .withPeriodStatus(mapToPeriodStatusDto())
                   .withTotalPoints(totalPoints)
                   .build();
    }

    public Candidate updateApproval(UpdateApprovalRequest input) {
        validateCandidateState();
        approvals.computeIfPresent(input.institutionId(), (uri, approval) -> approval.update(input));
        return this;
    }

    public Candidate createNote(CreateNoteRequest input) {
        validateCandidateState();
        var noteBO = Note.fromRequest(input, identifier, repository);
        notes.put(noteBO.getNoteId(), noteBO);
        return this;
    }

    public Candidate deleteNote(DeleteNoteRequest request) {
        validateCandidateState();
        var note = notes.get(request.noteId());
        note.validateOwner(request.username());
        notes.computeIfPresent(request.noteId(), (uuid, noteBO) -> {
            noteBO.delete();
            return null;
        });
        return this;
    }

    @Override
    @JacocoGenerated
    public int hashCode() {
        return Objects.hash(identifier, applicable, approvals, notes, institutionPoints, totalPoints, periodStatus,
                            publicationDetails);
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
        Candidate candidate = (Candidate) o;
        return applicable == candidate.applicable
               && Objects.equals(identifier, candidate.identifier)
               && Objects.equals(approvals, candidate.approvals)
               && Objects.equals(notes, candidate.notes)
               && Objects.equals(institutionPoints, candidate.institutionPoints)
               && Objects.equals(totalPoints, candidate.totalPoints)
               && Objects.equals(periodStatus, candidate.periodStatus)
               && Objects.equals(publicationDetails, candidate.publicationDetails);
    }

    private static PeriodStatus calculatePeriodStatusIfApplicable(PeriodRepository periodRepository,
                                                                  CandidateDao candidateDao) {
        return candidateDao.candidate().applicable() ? findPeriodStatus(periodRepository, candidateDao.candidate()
                                                                                              .publicationDate()
                                                                                              .year())
                   : PERIOD_STATUS_NO_PERIOD;
    }

    private static boolean isNotExistingCandidate(UpsertCandidateRequest request, CandidateRepository repository) {
        return !isExistingCandidate(request.publicationId(), repository);
    }

    private static Candidate deleteCandidate(UpdateNonCandidateRequest request, CandidateRepository repository) {
        var existingCandidateDao = repository.findByPublicationId(request.publicationId())
                                       .orElseThrow(CandidateNotFoundException::new);
        var nonApplicableCandidate = updateCandidateToNonApplicable(existingCandidateDao);
        repository.updateCandidateAndRemovingApprovals(existingCandidateDao.identifier(), nonApplicableCandidate);

        return new Candidate(repository, nonApplicableCandidate, Collections.emptyList(), Collections.emptyList(),
                             PeriodStatus.builder().withStatus(Status.NO_PERIOD).build());
    }

    private static Candidate updateCandidate(UpsertCandidateRequest request, CandidateRepository repository,
                                             PeriodRepository periodRepository) {
        validateCandidate(request);
        var existingCandidateDao = repository.findByPublicationId(request.publicationId())
                                       .orElseThrow(CandidateNotFoundException::new);
        if (shouldResetCandidate(request, existingCandidateDao) || isNotApplicable(existingCandidateDao)) {
            return resetCandidate(request, repository, periodRepository, existingCandidateDao);
        } else {
            return updateCandidateKeepingApprovalsAndNotes(request, repository, periodRepository, existingCandidateDao);
        }
    }

    private static boolean isNotApplicable(CandidateDao existingCandidateDao) {
        return !existingCandidateDao.candidate().applicable();
    }

    private static Candidate resetCandidate(UpsertCandidateRequest request, CandidateRepository repository,
                                            PeriodRepository periodRepository, CandidateDao existingCandidateDao) {
        var newApprovals = mapToApprovals(request.institutionPoints());
        var newCandidateDao = updateCandidateDaoFromRequest(existingCandidateDao, request);
        repository.updateCandidate(existingCandidateDao.identifier(), newCandidateDao, newApprovals);
        var notes = repository.getNotes(existingCandidateDao.identifier());
        var periodStatus = findPeriodStatus(periodRepository,
                                            existingCandidateDao.candidate().publicationDate().year());
        var approvals = mapToApprovalsDaos(newCandidateDao.identifier(), newApprovals);
        return new Candidate(repository, newCandidateDao, approvals, notes, periodStatus);
    }

    private static Candidate updateCandidateKeepingApprovalsAndNotes(UpsertCandidateRequest request,
                                                                     CandidateRepository repository,
                                                                     PeriodRepository periodRepository,
                                                                     CandidateDao existingCandidateDao) {
        var updatedCandidate = updateCandidateDaoFromRequest(existingCandidateDao, request);
        var approvalDaoList = repository.fetchApprovals(updatedCandidate.identifier());
        repository.updateCandidate(updatedCandidate);
        var noteDaoList = repository.getNotes(updatedCandidate.identifier());
        var periodStatus = findPeriodStatus(periodRepository, updatedCandidate.candidate().publicationDate().year());
        return new Candidate(repository, updatedCandidate, approvalDaoList, noteDaoList, periodStatus);
    }

    private static boolean shouldResetCandidate(UpsertCandidateRequest request, CandidateDao candidate) {
        return levelIsUpdated(request, candidate) || instanceTypeIsUpdated(request, candidate) || creatorsAreUpdated(
            request, candidate) || pointsAreUpdated(request, candidate) || publicationYearIsUpdated(request, candidate);
    }

    private static boolean publicationYearIsUpdated(UpsertCandidateRequest request, CandidateDao candidate) {
        return !request.publicationDate().year().equals(candidate.candidate().publicationDate().year());
    }

    private static boolean pointsAreUpdated(UpsertCandidateRequest request, CandidateDao existingCandidateDao) {
        return existingCandidateDao.candidate()
                   .points()
                   .stream()
                   .anyMatch(institutionPoints -> !equalsIgnoringScaleAndRoundingMode(
                       institutionPoints.points(),
                       extractRequestPoints(request, institutionPoints.institutionId())
                   ));
    }

    private static BigDecimal extractRequestPoints(UpsertCandidateRequest request, URI institutionId) {
        return request.institutionPoints().get(institutionId);
    }

    private static boolean equalsIgnoringScaleAndRoundingMode(BigDecimal existingPoints, BigDecimal requestPoints) {
        return Objects.equals(adjustScaleAndRoundingMode(requestPoints), adjustScaleAndRoundingMode(existingPoints));
    }

    private static boolean creatorsAreUpdated(UpsertCandidateRequest request, CandidateDao existingCandidateDao) {
        return !Objects.equals(mapToCreators(request.creators()), existingCandidateDao.candidate().creators());
    }

    private static boolean instanceTypeIsUpdated(UpsertCandidateRequest request, CandidateDao existingCandidateDao) {
        return !Objects.equals(request.instanceType(), existingCandidateDao.candidate().instanceType().getValue());
    }

    private static boolean levelIsUpdated(UpsertCandidateRequest request, CandidateDao existingCandidateDao) {
        return !Objects.equals(DbLevel.parse(request.level()), existingCandidateDao.candidate().level());
    }

    private static Candidate createCandidate(UpsertCandidateRequest request, CandidateRepository repository,
                                             PeriodRepository periodRepository) {
        validateCandidate(request);
        var candidateDao = repository.create(mapToCandidate(request), mapToApprovals(request.institutionPoints()));
        var approvals1 = repository.fetchApprovals(candidateDao.identifier());
        var notes1 = repository.getNotes(candidateDao.identifier());
        var periodStatus1 = findPeriodStatus(periodRepository, candidateDao.candidate().publicationDate().year());

        return new Candidate(repository, candidateDao, approvals1, notes1, periodStatus1);
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
            Objects.requireNonNull(candidate.totalPoints());
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

    private static Map<UUID, Note> mapToNotesMap(CandidateRepository repository, List<NoteDao> notes) {
        return notes.stream()
                   .map(dao -> new Note(repository, dao.identifier(), dao))
                   .collect(Collectors.toMap(Note::getNoteId, Function.identity()));
    }

    private static Map<URI, Approval> mapToApprovalsMap(CandidateRepository repository,
                                                        List<ApprovalStatusDao> approvals) {
        return approvals.stream()
                   .map(dao -> new Approval(repository, dao.identifier(), dao))
                   .collect(Collectors.toMap(Approval::getInstitutionId, Function.identity()));
    }

    private static PeriodStatus findPeriodStatus(PeriodRepository periodRepository, String year) {
        return periodRepository.findByPublishingYear(year)
                   .map(PeriodStatus::fromPeriod)
                   .orElse(PERIOD_STATUS_NO_PERIOD);
    }

    private static URI constructId(UUID identifier) {
        return new UriWrapper(HTTPS, API_DOMAIN).addChild(BASE_PATH, CANDIDATE_PATH, identifier.toString()).getUri();
    }

    private static List<DbApprovalStatus> mapToApprovals(Map<URI, BigDecimal> points) {
        return points.keySet().stream().map(Candidate::mapToApproval).toList();
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
                   .creatorShareCount(request.creatorShareCount())
                   .channelType(ChannelType.parse(request.channelType()))
                   .channelId(request.publicationChannelId())
                   .level(DbLevel.parse(request.level()))
                   .instanceType(InstanceType.parse(request.instanceType()))
                   .publicationDate(mapToPublicationDate(request.publicationDate()))
                   .internationalCollaboration(request.isInternationalCollaboration())
                   .collaborationFactor(adjustScaleAndRoundingMode(request.collaborationFactor()))
                   .basePoints(adjustScaleAndRoundingMode(request.basePoints()))
                   .points(mapToPoints(request.institutionPoints()))
                   .totalPoints(adjustScaleAndRoundingMode(request.totalPoints()))
                   .build();
    }

    private static CandidateDao updateCandidateDaoFromRequest(CandidateDao candidateDao,
                                                              UpsertCandidateRequest request) {
        return candidateDao.copy()
                   .candidate(candidateDao.candidate()
                                  .copy()
                                  .applicable(request.isApplicable())
                                  .creators(mapToCreators(request.creators()))
                                  .creatorShareCount(request.creatorShareCount())
                                  .channelType(ChannelType.parse(request.channelType()))
                                  .channelId(request.publicationChannelId())
                                  .level(DbLevel.parse(request.level()))
                                  .instanceType(InstanceType.parse(request.instanceType()))
                                  .publicationDate(mapToPublicationDate(request.publicationDate()))
                                  .internationalCollaboration(request.isInternationalCollaboration())
                                  .collaborationFactor(adjustScaleAndRoundingMode(request.collaborationFactor()))
                                  .basePoints(adjustScaleAndRoundingMode(request.basePoints()))
                                  .points(mapToPoints(request.institutionPoints()))
                                  .totalPoints(adjustScaleAndRoundingMode(request.totalPoints()))
                                  .build())
                   .version(randomUUID().toString())
                   .build();
    }

    private static List<DbInstitutionPoints> mapToPoints(Map<URI, BigDecimal> points) {
        return points.entrySet().stream()
                   .map(entry -> new DbInstitutionPoints(entry.getKey(), adjustScaleAndRoundingMode(entry.getValue())))
                   .toList();
    }

    private static DbPublicationDate mapToPublicationDate(PublicationDate publicationDate) {
        return DbPublicationDate.builder()
                   .year(publicationDate.year())
                   .month(publicationDate.month())
                   .day(publicationDate.day())
                   .build();
    }

    private static PublicationDate mapToPublicationDate(DbPublicationDate date) {
        return new PublicationDate(date.year(), date.month(), date.day());
    }

    private static List<DbCreator> mapToCreators(Map<URI, List<URI>> creators) {
        return creators.entrySet().stream().map(e -> new DbCreator(e.getKey(), e.getValue())).toList();
    }

    private static List<Creator> mapToCreators(List<DbCreator> creators) {
        return creators.stream().map(Candidate::mapToCreator).toList();
    }

    private static Creator mapToCreator(DbCreator dbCreator) {
        return new Creator(dbCreator.creatorId(), dbCreator.affiliations());
    }

    private static boolean isExistingCandidate(URI publicationId, CandidateRepository repository) {
        return repository.findByPublicationId(publicationId).isPresent();
    }

    private static CandidateDao updateCandidateToNonApplicable(CandidateDao candidateDao) {
        return candidateDao.copy()
                   .candidate(candidateDao.candidate().copy().applicable(false).build())
                   .build();
    }

    private static String mapToUsernameString(Username assignee) {
        return assignee != null ? assignee.value() : null;
    }

    private PublicationDetails mapToPublicationDetails(CandidateDao candidateDao) {
        return new PublicationDetails(candidateDao.candidate().publicationId(),
                                      candidateDao.candidate().publicationBucketUri(),
                                      candidateDao.candidate().instanceType().getValue(),
                                      mapToPublicationDate(candidateDao.candidate().publicationDate()),
                                      mapToCreators(candidateDao.candidate().creators()),
                                      candidateDao.candidate().channelType(),
                                      candidateDao.candidate().channelId(),
                                      candidateDao.candidate().level()
                                          .getValues().stream().findFirst().orElse(null));
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
        return this.notes.values().stream().map(Note::toDto).toList();
    }

    private List<ApprovalDto> mapToApprovalDtos() {
        return approvals.values().stream().map(this::mapToApprovalDto).toList();
    }

    private ApprovalDto mapToApprovalDto(Approval approval) {
        return ApprovalDto.builder()
                   .withInstitutionId(approval.getInstitutionId())
                   .withStatus(approval.getStatus())
                   .withAssignee(mapToUsernameString(approval.getAssignee()))
                   .withFinalizedBy(mapToUsernameString(approval.getFinalizedBy()))
                   .withFinalizedDate(approval.getFinalizedDate())
                   .withPoints(institutionPoints.get(approval.getInstitutionId()))
                   .withReason(approval.getReason())
                   .build();
    }
}
