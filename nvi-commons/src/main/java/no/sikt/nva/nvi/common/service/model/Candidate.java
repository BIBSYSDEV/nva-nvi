package no.sikt.nva.nvi.common.service.model;

import static java.util.Objects.isNull;
import static java.util.Objects.nonNull;
import static java.util.UUID.randomUUID;
import static no.sikt.nva.nvi.common.db.ReportStatus.REPORTED;
import static no.sikt.nva.nvi.common.service.model.ApprovalStatus.APPROVED;
import static no.sikt.nva.nvi.common.service.model.ApprovalStatus.REJECTED;
import static no.sikt.nva.nvi.common.utils.DecimalUtils.adjustScaleAndRoundingMode;
import static nva.commons.core.attempt.Try.attempt;
import static nva.commons.core.ioutils.IoUtils.stringFromResources;
import static nva.commons.core.paths.UriWrapper.HTTPS;
import java.math.BigDecimal;
import java.net.URI;
import java.nio.file.Path;
import java.time.Instant;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.Stream;
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
import no.sikt.nva.nvi.common.db.ReportStatus;
import no.sikt.nva.nvi.common.db.model.ChannelType;
import no.sikt.nva.nvi.common.db.model.InstanceType;
import no.sikt.nva.nvi.common.model.InvalidNviCandidateException;
import no.sikt.nva.nvi.common.model.UpdateApprovalRequest;
import no.sikt.nva.nvi.common.model.UpdateAssigneeRequest;
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
    public static final URI CONTEXT_URI = UriWrapper.fromHost(API_DOMAIN)
                                              .addChild(BASE_PATH, "context")
                                              .getUri();
    private static final String CONTEXT = stringFromResources(Path.of("nviCandidateContext.json"))
                                              .replace("__REPLACE_WITH_API_DOMAIN__", API_DOMAIN);
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
    private final List<InstitutionPoints> institutionPoints;
    private final BigDecimal totalPoints;
    private final PeriodStatus period;
    private final PublicationDetails publicationDetails;
    private final BigDecimal basePoints;
    private final boolean internationalCollaboration;
    private final BigDecimal collaborationFactor;
    private final int creatorShareCount;
    private final Instant createdDate;
    private final Instant modifiedDate;
    private final ReportStatus reportStatus;

    private Candidate(CandidateRepository repository, CandidateDao candidateDao, List<ApprovalStatusDao> approvals,
                      List<NoteDao> notes, PeriodStatus period) {
        this.repository = repository;
        this.identifier = candidateDao.identifier();
        this.applicable = candidateDao.candidate().applicable();
        this.approvals = mapToApprovalsMap(repository, approvals);
        this.notes = mapToNotesMap(repository, notes);
        this.institutionPoints = mapToInstitutionPoints(candidateDao);
        this.totalPoints = candidateDao.candidate().totalPoints();
        this.period = period;
        this.publicationDetails = mapToPublicationDetails(candidateDao);
        this.basePoints = candidateDao.candidate().basePoints();
        this.internationalCollaboration = candidateDao.candidate().internationalCollaboration();
        this.collaborationFactor = candidateDao.candidate().collaborationFactor();
        this.creatorShareCount = candidateDao.candidate().creatorShareCount();
        this.createdDate = candidateDao.candidate().createdDate();
        this.modifiedDate = candidateDao.candidate().modifiedDate();
        this.reportStatus = candidateDao.candidate().reportStatus();
    }

    public static Candidate fetchByPublicationId(FetchByPublicationRequest request, CandidateRepository repository,
                                                 PeriodRepository periodRepository) {
        var candidateDao = repository.findByPublicationId(request.publicationId())
                               .orElseThrow(CandidateNotFoundException::new);
        var approvalDaoList = repository.fetchApprovals(candidateDao.identifier());
        var noteDaoList = repository.getNotes(candidateDao.identifier());
        var periodStatus = findPeriodStatus(periodRepository, candidateDao.getPeriodYear());
        return new Candidate(repository, candidateDao, approvalDaoList, noteDaoList, periodStatus);
    }

    public static Candidate fetch(FetchCandidateRequest request, CandidateRepository repository,
                                  PeriodRepository periodRepository) {
        var candidateDao = repository.findCandidateById(request.identifier())
                               .orElseThrow(CandidateNotFoundException::new);
        var approvalDaoList = repository.fetchApprovals(candidateDao.identifier());
        var noteDaoList = repository.getNotes(candidateDao.identifier());
        var periodStatus = findPeriodStatus(periodRepository, candidateDao.getPeriodYear());
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
            return Optional.of(updateToNotApplicable(request, repository));
        }
        return Optional.empty();
    }

    public static String getJsonLdContext() {
        return CONTEXT;
    }

    public static URI getContextUri() {
        return CONTEXT_URI;
    }

    public Instant getCreatedDate() {
        return createdDate;
    }

    public Instant getModifiedDate() {
        return modifiedDate;
    }

    public PublicationDetails getPublicationDetails() {
        return publicationDetails;
    }

    public PeriodStatus getPeriod() {
        return period;
    }

    public UUID getIdentifier() {
        return identifier;
    }

    public URI getId() {
        return new UriWrapper(HTTPS, API_DOMAIN).addChild(BASE_PATH, CANDIDATE_PATH, identifier.toString()).getUri();
    }

    public boolean isApplicable() {
        return applicable;
    }

    public Map<URI, BigDecimal> getInstitutionPointsMap() {
        return institutionPoints.stream().collect(Collectors.toMap(InstitutionPoints::institutionId,
                                                                   InstitutionPoints::institutionPoints));
    }

    public List<InstitutionPoints> getInstitutionPoints() {
        return institutionPoints;
    }

    public BigDecimal getPointValueForInstitution(URI institutionId) {
        return getInstitutionPoints(institutionId).institutionPoints();
    }

    public InstitutionPoints getInstitutionPoints(URI institutionId) {
        return institutionPoints.stream()
                   .filter(institutionPoints -> institutionPoints.institutionId().equals(institutionId))
                   .findFirst()
                   .orElseThrow();
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

    public boolean isReported() {
        return REPORTED.equals(reportStatus);
    }

    public GlobalApprovalStatus getGlobalApprovalStatus() {
        if (isDispute()) {
            return GlobalApprovalStatus.DISPUTE;
        } else if (areAnyApprovalsPending()) {
            return GlobalApprovalStatus.PENDING;
        } else if (areAllApprovalsApproved()) {
            return GlobalApprovalStatus.APPROVED;
        } else {
            return GlobalApprovalStatus.REJECTED;
        }
    }

    public CandidateDto toDto() {
        return CandidateDto.builder()
                   .withId(getId())
                   .withContext(CONTEXT_URI)
                   .withIdentifier(identifier)
                   .withPublicationId(publicationDetails.publicationId())
                   .withApprovals(mapToApprovalDtos())
                   .withNotes(mapToNoteDtos())
                   .withPeriod(mapToPeriodStatusDto())
                   .withTotalPoints(totalPoints)
                   .withReportStatus(Optional.ofNullable(reportStatus).map(ReportStatus::getValue).orElse(null))
                   .build();
    }

    public Candidate updateApproval(UpdateApprovalRequest input) {
        validateCandidateState();
        approvals.computeIfPresent(input.institutionId(), (uri, approval) -> approval.update(input));
        return this;
    }

    public Candidate createNote(CreateNoteRequest input) {
        validateCandidateState();
        var note = Note.fromRequest(input, identifier, repository);
        notes.put(note.getNoteId(), note);
        setUserAsAssigneeIfApprovalIsUnassigned(input.username(), input.institutionId());
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
        return Objects.hash(identifier, applicable, approvals, notes, institutionPoints, totalPoints, period,
                            publicationDetails, basePoints, internationalCollaboration, collaborationFactor,
                            creatorShareCount, createdDate, reportStatus);
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
               && internationalCollaboration == candidate.internationalCollaboration
               && creatorShareCount == candidate.creatorShareCount
               && Objects.equals(identifier, candidate.identifier)
               && Objects.equals(approvals, candidate.approvals)
               && Objects.equals(notes, candidate.notes)
               && Objects.equals(institutionPoints, candidate.institutionPoints)
               && Objects.equals(totalPoints, candidate.totalPoints)
               && Objects.equals(period, candidate.period)
               && Objects.equals(publicationDetails, candidate.publicationDetails)
               && Objects.equals(basePoints, candidate.basePoints)
               && Objects.equals(collaborationFactor, candidate.collaborationFactor)
               && Objects.equals(createdDate, candidate.createdDate)
               && Objects.equals(reportStatus, candidate.reportStatus);
    }

    private static boolean isNotExistingCandidate(UpsertCandidateRequest request, CandidateRepository repository) {
        return !isExistingCandidate(request.publicationId(), repository);
    }

    private static Candidate updateToNotApplicable(UpdateNonCandidateRequest request, CandidateRepository repository) {
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

    private static boolean isNotApplicable(CandidateDao candidateDao) {
        return !candidateDao.candidate().applicable();
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
                   .anyMatch(institutionPoints -> isNotequalIgnoringScaleAndRoundingMode(
                       institutionPoints.points(),
                       request.getPointsForInstitution(institutionPoints.institutionId())
                   ));
    }

    private static boolean isNotequalIgnoringScaleAndRoundingMode(BigDecimal existingPoints, BigDecimal requestPoints) {
        return !Objects.equals(adjustScaleAndRoundingMode(requestPoints), adjustScaleAndRoundingMode(existingPoints));
    }

    private static boolean creatorsAreUpdated(UpsertCandidateRequest request, CandidateDao existingCandidateDao) {
        return !Objects.equals(mapToCreators(request.creators()), existingCandidateDao.candidate().creators());
    }

    private static boolean instanceTypeIsUpdated(UpsertCandidateRequest request, CandidateDao existingCandidateDao) {
        return !Objects.equals(request.instanceType(), existingCandidateDao.candidate().instanceType());
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

    private static List<InstitutionPoints> mapToInstitutionPoints(CandidateDao candidateDao) {
        if (isNull(candidateDao.candidate().points()) || candidateDao.candidate().points().isEmpty()) {
            return Collections.emptyList();
        } else {
            return candidateDao.candidate().points().stream().map(InstitutionPoints::from).toList();
        }
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

    private static List<DbApprovalStatus> mapToApprovals(List<InstitutionPoints> institutionPoints) {
        return institutionPoints.stream().map(InstitutionPoints::institutionId).map(Candidate::mapToApproval).toList();
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
                   .instanceType(request.instanceType())
                   .publicationDate(mapToPublicationDate(request.publicationDate()))
                   .internationalCollaboration(request.isInternationalCollaboration())
                   .collaborationFactor(adjustScaleAndRoundingMode(request.collaborationFactor()))
                   .basePoints(adjustScaleAndRoundingMode(request.basePoints()))
                   .points(mapToPoints(request.institutionPoints()))
                   .totalPoints(adjustScaleAndRoundingMode(request.totalPoints()))
                   .createdDate(Instant.now())
                   .modifiedDate(Instant.now())
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
                                  .instanceType(request.instanceType())
                                  .publicationDate(mapToPublicationDate(request.publicationDate()))
                                  .internationalCollaboration(request.isInternationalCollaboration())
                                  .collaborationFactor(adjustScaleAndRoundingMode(request.collaborationFactor()))
                                  .basePoints(adjustScaleAndRoundingMode(request.basePoints()))
                                  .points(mapToPoints(request.institutionPoints()))
                                  .totalPoints(adjustScaleAndRoundingMode(request.totalPoints()))
                                  .modifiedDate(Instant.now())
                                  .build())
                   .version(randomUUID().toString())
                   .periodYear(request.publicationDate().year())
                   .build();
    }

    private static List<DbInstitutionPoints> mapToPoints(List<InstitutionPoints> points) {
        return points.stream()
                   .map(DbInstitutionPoints::from)
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
        return nonNull(creators) ? creators.stream().map(Candidate::mapToCreator).toList() : List.of();
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
                   .periodYear(null)
                   .build();
    }



    private void setUserAsAssigneeIfApprovalIsUnassigned(String username, URI institutionId) {
        approvals.computeIfPresent(institutionId, (uri, approval) -> updateAssigneeIfUnassigned(username, approval));
    }

    private Approval updateAssigneeIfUnassigned(String username, Approval approval) {
        return approval.isAssigned()
                   ? approval
                   : approval.update(new UpdateAssigneeRequest(approval.getInstitutionId(), username));
    }

    private boolean isDispute() {
        var approvalStatuses = streamApprovals().map(Approval::getStatus).toList();
        return approvalStatuses.stream().anyMatch(APPROVED::equals)
               && approvalStatuses.stream().anyMatch(REJECTED::equals);
    }

    private boolean areAllApprovalsApproved() {
        return streamApprovals().map(Approval::getStatus).allMatch(APPROVED::equals);
    }

    private boolean areAnyApprovalsPending() {
        return streamApprovals().anyMatch(approval -> ApprovalStatus.PENDING.equals(approval.getStatus()));
    }

    private Stream<Approval> streamApprovals() {
        return approvals.values().stream();
    }

    private PublicationDetails mapToPublicationDetails(CandidateDao candidateDao) {
        return new PublicationDetails(candidateDao.candidate().publicationId(),
                                      candidateDao.candidate().publicationBucketUri(),
                                      candidateDao.candidate().instanceType(),
                                      mapToPublicationDate(candidateDao.candidate().publicationDate()),
                                      mapToCreators(candidateDao.candidate().creators()),
                                      candidateDao.candidate().channelType(),
                                      candidateDao.candidate().channelId(),
                                      candidateDao.candidate().level().getValue());
    }

    private void validateCandidateState() {
        if (Status.CLOSED_PERIOD.equals(period.status())) {
            throw new IllegalStateException(PERIOD_CLOSED_MESSAGE);
        }
        if (Status.NO_PERIOD.equals(period.status()) || Status.UNOPENED_PERIOD.equals(period.status())) {
            throw new IllegalStateException(PERIOD_NOT_OPENED_MESSAGE);
        }
    }

    private PeriodStatusDto mapToPeriodStatusDto() {
        return PeriodStatusDto.fromPeriodStatus(period);
    }

    private List<NoteDto> mapToNoteDtos() {
        return this.notes.values().stream().map(Note::toDto).toList();
    }

    private List<ApprovalDto> mapToApprovalDtos() {
        return streamApprovals().map(this::toApprovalDto).toList();
    }

    private ApprovalDto toApprovalDto(Approval approval) {
        var points = getPointValueForInstitution(approval.getInstitutionId());
        return ApprovalDto.fromApprovalAndInstitutionPoints(approval, points);
    }
}
