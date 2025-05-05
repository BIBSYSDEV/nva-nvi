package no.sikt.nva.nvi.common.service.model;

import static java.util.Objects.isNull;
import static java.util.Objects.nonNull;
import static java.util.UUID.randomUUID;
import static java.util.function.Predicate.not;
import static no.sikt.nva.nvi.common.db.ReportStatus.REPORTED;
import static no.sikt.nva.nvi.common.service.model.Approval.validateUpdateStatusRequest;
import static no.sikt.nva.nvi.common.service.model.ApprovalStatus.APPROVED;
import static no.sikt.nva.nvi.common.service.model.ApprovalStatus.PENDING;
import static no.sikt.nva.nvi.common.service.model.ApprovalStatus.REJECTED;
import static no.sikt.nva.nvi.common.utils.DecimalUtils.adjustScaleAndRoundingMode;
import static no.sikt.nva.nvi.common.utils.RequestUtil.getAllCreators;
import static nva.commons.core.attempt.Try.attempt;
import static nva.commons.core.ioutils.IoUtils.stringFromResources;
import static nva.commons.core.paths.UriWrapper.HTTPS;

import java.math.BigDecimal;
import java.net.URI;
import java.nio.file.Path;
import java.time.Instant;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import no.sikt.nva.nvi.common.client.OrganizationRetriever;
import no.sikt.nva.nvi.common.db.ApprovalStatusDao;
import no.sikt.nva.nvi.common.db.ApprovalStatusDao.DbApprovalStatus;
import no.sikt.nva.nvi.common.db.ApprovalStatusDao.DbStatus;
import no.sikt.nva.nvi.common.db.CandidateDao;
import no.sikt.nva.nvi.common.db.CandidateDao.DbCandidate;
import no.sikt.nva.nvi.common.db.CandidateDao.DbCreatorType;
import no.sikt.nva.nvi.common.db.CandidateDao.DbInstitutionPoints;
import no.sikt.nva.nvi.common.db.CandidateDao.DbLevel;
import no.sikt.nva.nvi.common.db.CandidateRepository;
import no.sikt.nva.nvi.common.db.NoteDao;
import no.sikt.nva.nvi.common.db.PeriodRepository;
import no.sikt.nva.nvi.common.db.PeriodStatus;
import no.sikt.nva.nvi.common.db.PeriodStatus.Status;
import no.sikt.nva.nvi.common.db.ReportStatus;
import no.sikt.nva.nvi.common.db.model.ChannelType;
import no.sikt.nva.nvi.common.dto.UpsertNonNviCandidateRequest;
import no.sikt.nva.nvi.common.dto.UpsertNviCandidateRequest;
import no.sikt.nva.nvi.common.model.InvalidNviCandidateException;
import no.sikt.nva.nvi.common.model.UpdateApprovalRequest;
import no.sikt.nva.nvi.common.model.UpdateAssigneeRequest;
import no.sikt.nva.nvi.common.model.UpdateStatusRequest;
import no.sikt.nva.nvi.common.service.dto.ApprovalDto;
import no.sikt.nva.nvi.common.service.dto.CandidateDto;
import no.sikt.nva.nvi.common.service.dto.NoteDto;
import no.sikt.nva.nvi.common.service.dto.PeriodStatusDto;
import no.sikt.nva.nvi.common.service.dto.UnverifiedNviCreatorDto;
import no.sikt.nva.nvi.common.service.dto.VerifiedNviCreatorDto;
import no.sikt.nva.nvi.common.service.exception.CandidateNotFoundException;
import no.sikt.nva.nvi.common.service.exception.IllegalCandidateUpdateException;
import no.sikt.nva.nvi.common.service.requests.CreateNoteRequest;
import no.sikt.nva.nvi.common.service.requests.DeleteNoteRequest;
import no.sikt.nva.nvi.common.service.requests.FetchByPublicationRequest;
import no.sikt.nva.nvi.common.service.requests.FetchCandidateRequest;
import no.sikt.nva.nvi.common.validator.CandidateUpdateValidator;
import nva.commons.core.Environment;
import nva.commons.core.JacocoGenerated;
import nva.commons.core.paths.UriWrapper;

// Should be refactored, technical debt task: https://sikt.atlassian.net/browse/NP-48093
@SuppressWarnings({"PMD.GodClass", "PMD.CouplingBetweenObjects"})
public final class Candidate {

  private static final Environment ENVIRONMENT = new Environment();
  private static final String BASE_PATH = ENVIRONMENT.readEnv("CUSTOM_DOMAIN_BASE_PATH");
  private static final String API_DOMAIN = ENVIRONMENT.readEnv("API_HOST");
  public static final URI CONTEXT_URI =
      UriWrapper.fromHost(API_DOMAIN).addChild(BASE_PATH, "context").getUri();
  private static final String CONTEXT = stringFromResources(Path.of("nviCandidateContext.json"));
  private static final String CANDIDATE_PATH = "candidate";
  private static final String PERIOD_CLOSED_MESSAGE =
      "Period is closed, perform actions on candidate is forbidden!";
  private static final String PERIOD_NOT_OPENED_MESSAGE =
      "Period is not opened yet, perform actions on candidate is" + " forbidden!";
  private static final String INVALID_CANDIDATE_MESSAGE = "Candidate is missing mandatory fields";
  private static final PeriodStatus PERIOD_STATUS_NO_PERIOD =
      PeriodStatus.builder().withStatus(Status.NO_PERIOD).build();
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

  private Candidate(
      UUID identifier,
      boolean applicable,
      Map<URI, Approval> approvals,
      Map<UUID, Note> notes,
      List<InstitutionPoints> institutionPoints,
      BigDecimal totalPoints,
      PeriodStatus period,
      PublicationDetails publicationDetails,
      BigDecimal basePoints,
      boolean internationalCollaboration,
      BigDecimal collaborationFactor,
      int creatorShareCount,
      Instant createdDate,
      Instant modifiedDate,
      ReportStatus reportStatus) {
    this.identifier = identifier;
    this.applicable = applicable;
    this.approvals = approvals;
    this.notes = notes;
    this.institutionPoints = institutionPoints;
    this.totalPoints = totalPoints;
    this.period = period;
    this.publicationDetails = publicationDetails;
    this.basePoints = basePoints;
    this.internationalCollaboration = internationalCollaboration;
    this.collaborationFactor = collaborationFactor;
    this.creatorShareCount = creatorShareCount;
    this.createdDate = createdDate;
    this.modifiedDate = modifiedDate;
    this.reportStatus = reportStatus;
  }

  private Candidate(
      CandidateRepository repository,
      CandidateDao candidateDao,
      List<ApprovalStatusDao> approvals,
      List<NoteDao> notes,
      PeriodStatus period) {
    var details = candidateDao.candidate().publicationDetails();
    this.identifier = candidateDao.identifier();
    this.applicable = details.applicable();
    this.approvals = mapToApprovalsMap(repository, approvals);
    this.notes = mapToNotesMap(repository, notes);
    this.institutionPoints = mapToInstitutionPoints(candidateDao);
    this.totalPoints = candidateDao.candidate().totalPoints();
    this.period = period;
    this.publicationDetails = PublicationDetails.from(candidateDao);
    this.basePoints = candidateDao.candidate().basePoints();
    this.internationalCollaboration = details.internationalCollaboration();
    this.collaborationFactor = candidateDao.candidate().collaborationFactor();
    this.creatorShareCount = candidateDao.candidate().creatorShareCount();
    this.createdDate = candidateDao.candidate().createdDate();
    this.modifiedDate = candidateDao.candidate().modifiedDate();
    this.reportStatus = candidateDao.candidate().reportStatus();
  }

  public static Candidate fetchByPublicationId(
      FetchByPublicationRequest request,
      CandidateRepository repository,
      PeriodRepository periodRepository) {
    var candidateDao =
        repository
            .findByPublicationId(request.publicationId())
            .orElseThrow(CandidateNotFoundException::new);
    var approvalDaoList = repository.fetchApprovals(candidateDao.identifier());
    var noteDaoList = repository.getNotes(candidateDao.identifier());
    var periodStatus = findPeriodStatus(periodRepository, candidateDao.getPeriodYear());
    return new Candidate(repository, candidateDao, approvalDaoList, noteDaoList, periodStatus);
  }

  public static Candidate fetch(
      FetchCandidateRequest request,
      CandidateRepository repository,
      PeriodRepository periodRepository) {
    var candidateDao =
        repository
            .findCandidateById(request.identifier())
            .orElseThrow(CandidateNotFoundException::new);
    var approvalDaoList = repository.fetchApprovals(candidateDao.identifier());
    var noteDaoList = repository.getNotes(candidateDao.identifier());
    var periodStatus = findPeriodStatus(periodRepository, candidateDao.getPeriodYear());
    return new Candidate(repository, candidateDao, approvalDaoList, noteDaoList, periodStatus);
  }

  public static void upsert(
      UpsertNviCandidateRequest request,
      CandidateRepository candidateRepository,
      PeriodRepository periodRepository) {
    var optionalCandidate = fetchOptionalCandidate(request, candidateRepository, periodRepository);
    optionalCandidate.ifPresentOrElse(
        candidate -> updateExistingCandidate(request, candidateRepository, candidate),
        () -> createCandidate(request, candidateRepository));
  }

  public static Optional<Candidate> updateNonCandidate(
      UpsertNonNviCandidateRequest request, CandidateRepository repository) {
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

  public ReportStatus getReportStatus() {
    return reportStatus;
  }

  public UUID getIdentifier() {
    return identifier;
  }

  public URI getId() {
    return new UriWrapper(HTTPS, API_DOMAIN)
        .addChild(BASE_PATH, CANDIDATE_PATH, identifier.toString())
        .getUri();
  }

  /**
   * A candidate is considered "Applicable" if the publication itself fulfills the criteria for NVI
   * reporting. This is determined by facts such as the type of publication and level of the
   * publication channel. This does not take into account metadata such as approvals and status of
   * the relevant reporting period.
   */
  public boolean isApplicable() {
    return applicable;
  }

  public BigDecimal getPointValueForInstitution(URI institutionId) {
    return getInstitutionPoints(institutionId)
        .map(InstitutionPoints::institutionPoints)
        .orElse(null);
  }

  public List<InstitutionPoints> getInstitutionPoints() {
    return nonNull(institutionPoints) ? institutionPoints : List.of();
  }

  // TODO: Make method return InstitutionPoints once we have migrated candidates from Cristin
  public Optional<InstitutionPoints> getInstitutionPoints(URI institutionId) {
    return institutionPoints.stream()
        .filter(points -> points.institutionId().equals(institutionId))
        .findFirst();
  }

  public Map<URI, Approval> getApprovals() {
    return new HashMap<>(approvals);
  }

  public ApprovalStatus getApprovalStatus(URI organizationId) {
    var approval = approvals.get(organizationId);
    return nonNull(approval) ? approval.getStatus() : ApprovalStatus.NONE;
  }

  public BigDecimal getBasePoints() {
    return basePoints;
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

  public boolean isNotReportedInClosedPeriod() {
    return !isReported() && period.isClosed();
  }

  public boolean isUnderReview() {
    return !areAllApprovalsPending() && period.isOpen();
  }

  public boolean isPendingReview() {
    return areAllApprovalsPending() && period.isOpen();
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

  public String getScientificLevel() {
    return publicationDetails.publicationChannel().level();
  }

  public ChannelType getPublicationChannelType() {
    return publicationDetails.publicationChannel().channelType();
  }

  public URI getPublicationChannelId() {
    return publicationDetails.publicationChannel().id();
  }

  public CandidateDto toDto(
      URI userTopLevelOrganizationId, OrganizationRetriever organizationRetriever) {
    var validator =
        new CandidateUpdateValidator(this, organizationRetriever, userTopLevelOrganizationId);
    return CandidateDto.builder()
        .withId(getId())
        .withContext(CONTEXT_URI)
        .withIdentifier(getIdentifier())
        .withPublicationId(getPublicationId())
        .withApprovals(mapToApprovalDtos())
        .withAllowedOperations(validator.getAllowedOperations())
        .withProblems(validator.getProblems())
        .withNotes(mapToNoteDtos())
        .withPeriod(mapToPeriodStatusDto())
        .withTotalPoints(getTotalPoints())
        .withReportStatus(
            Optional.ofNullable(getReportStatus()).map(ReportStatus::getValue).orElse(null))
        .build();
  }

  /**
   * @deprecated Use either
   * {@link #updateApprovalAssignee(UpdateAssigneeRequest input) or
   * {@link #updateApprovalStatus(
   *  UpdateStatusRequest input,
   *  OrganizationRetriever organizationRetriever
   *  )} instead.
   */
  @Deprecated(since = "2025-01-31", forRemoval = true)
  public Candidate updateApproval(UpdateApprovalRequest input) {
    validateCandidateState();
    approvals.computeIfPresent(input.institutionId(), (uri, approval) -> approval.update(input));
    return this;
  }

  public Candidate updateApprovalAssignee(UpdateAssigneeRequest input) {
    validateCandidateState();
    approvals.computeIfPresent(input.institutionId(), (uri, approval) -> approval.update(input));
    return this;
  }

  public Candidate updateApprovalStatus(
      UpdateStatusRequest input, OrganizationRetriever organizationRetriever) {
    validateUpdateStatusRequest(input);
    validateCandidateState();
    var validator =
        new CandidateUpdateValidator(this, organizationRetriever, input.institutionId());

    var currentState = getApprovalStatus(input.institutionId());
    var newState = input.approvalStatus();

    if (currentState.equals(newState)) {
      // No change in state, return without making any changes
      return this;
    }

    if (!validator.isValidStatusChange(input)) {
      throw new IllegalStateException("Cannot update approval status");
    }

    approvals.computeIfPresent(input.institutionId(), (uri, approval) -> approval.update(input));
    return this;
  }

  public Candidate createNote(CreateNoteRequest input, CandidateRepository repository) {
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
    notes.computeIfPresent(
        request.noteId(),
        (uuid, noteBO) -> {
          noteBO.delete();
          return null;
        });
    return this;
  }

  @Override
  @JacocoGenerated
  public int hashCode() {
    return Objects.hash(
        identifier,
        applicable,
        approvals,
        notes,
        institutionPoints,
        totalPoints,
        period,
        publicationDetails,
        basePoints,
        internationalCollaboration,
        collaborationFactor,
        creatorShareCount,
        createdDate,
        reportStatus);
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

  public URI getPublicationId() {
    return publicationDetails.publicationId();
  }

  public List<URI> getNviCreatorAffiliations() {
    return publicationDetails.getNviCreatorAffiliations();
  }

  public void updateVersion(CandidateRepository candidateRepository) {
    candidateRepository
        .findCandidateById(identifier)
        .map(candidateDao -> updateVersion(candidateRepository, candidateDao))
        .orElseThrow(CandidateNotFoundException::new);
  }

  private static CandidateDao updateVersion(
      CandidateRepository candidateRepository, CandidateDao candidateDao) {
    var candidateWithNewVersion = candidateDao.copy().version(randomUUID().toString()).build();
    candidateRepository.updateCandidate(candidateWithNewVersion);
    return candidateWithNewVersion;
  }

  private static Optional<Candidate> fetchOptionalCandidate(
      UpsertNviCandidateRequest request,
      CandidateRepository candidateRepository,
      PeriodRepository periodRepository) {
    return attempt(
            () ->
                fetchByPublicationId(request::publicationId, candidateRepository, periodRepository))
        .toOptional();
  }

  private static void updateExistingCandidate(
      UpsertNviCandidateRequest request,
      CandidateRepository repository,
      Candidate existingCandidate) {
    if (existingCandidate.isReported()) {
      throw new IllegalCandidateUpdateException("Can not update reported candidate");
    } else {
      updateCandidate(request, repository, existingCandidate);
    }
  }

  private static Candidate updateToNotApplicable(
      UpsertNonNviCandidateRequest request, CandidateRepository repository) {
    var existingCandidateDao =
        repository
            .findByPublicationId(request.publicationId())
            .orElseThrow(CandidateNotFoundException::new);
    var nonApplicableCandidate = updateCandidateToNonApplicable(existingCandidateDao);
    repository.updateCandidateAndRemovingApprovals(
        existingCandidateDao.identifier(), nonApplicableCandidate);

    return new Candidate(
        repository,
        nonApplicableCandidate,
        Collections.emptyList(),
        Collections.emptyList(),
        PeriodStatus.builder().withStatus(Status.NO_PERIOD).build());
  }

  private static void updateCandidate(
      UpsertNviCandidateRequest request, CandidateRepository repository, Candidate candidate) {
    validateCandidate(request);
    var updatedCandidate = candidate.apply(request);
    if (shouldResetCandidate(request, candidate) || isNotApplicable(candidate)) {
      var newApprovals = mapToApprovals(updatedCandidate.getInstitutionPoints());
      repository.updateCandidateAndDeleteOtherApprovals(updatedCandidate.toDao(), newApprovals);
    } else {
      var updatedApprovals = getIndividualApprovalsToReset(candidate, updatedCandidate);
      repository.updateCandidateAndKeepOtherApprovals(updatedCandidate.toDao(), updatedApprovals);
    }
  }

  /**
   * Returns new approvals in a pending state for all institutions that should have their approvals
   * reset.
   */
  private static List<DbApprovalStatus> getIndividualApprovalsToReset(
      Candidate candidate, Candidate updatedCandidate) {
    return updatedCandidate.getInstitutionPoints().stream()
        .filter(institutionPoints -> !hasSameInstitutionPoints(candidate, institutionPoints))
        .map(InstitutionPoints::institutionId)
        .map(Candidate::mapToApproval)
        .toList();
  }

  private static boolean isNotApplicable(Candidate candidate) {
    return !candidate.isApplicable();
  }

  private static boolean shouldResetCandidate(
      UpsertNviCandidateRequest request, Candidate candidate) {
    return levelIsUpdated(request, candidate)
        || publicationChannelIsUpdated(request, candidate)
        || instanceTypeIsUpdated(request, candidate)
        || creatorsAreUpdated(request, candidate)
        || hasChangeInTopLevelOrganizations(request, candidate)
        || publicationYearIsUpdated(request, candidate);
  }

  private static boolean publicationChannelIsUpdated(
      UpsertNviCandidateRequest request, Candidate candidate) {
    return !request.publicationChannelId().equals(candidate.getPublicationChannelId());
  }

  private static boolean publicationYearIsUpdated(
      UpsertNviCandidateRequest request, Candidate candidate) {
    var publicationYearOfCandidate = candidate.getPublicationDetails().publicationDate().year();
    var publicationYearFromRequest = request.publicationDetails().publicationDate().year();
    return not(publicationYearOfCandidate::equals).test(publicationYearFromRequest);
  }

  private static boolean hasChangeInTopLevelOrganizations(
      UpsertNviCandidateRequest request, Candidate candidate) {
    var oldTopLevelOrganizations =
        candidate.getInstitutionPoints().stream()
            .map(InstitutionPoints::institutionId)
            .collect(Collectors.toSet());
    var newTopLevelOrganizations =
        request.institutionPoints().stream()
            .map(InstitutionPoints::institutionId)
            .collect(Collectors.toSet());
    return !oldTopLevelOrganizations.equals(newTopLevelOrganizations);
  }

  private static Boolean hasSameInstitutionPoints(
      Candidate candidate, InstitutionPoints institutionPoints) {
    return Optional.ofNullable(
            candidate.getPointValueForInstitution(institutionPoints.institutionId()))
        .map(
            currentPoints ->
                isEqualWithSameScale(currentPoints, institutionPoints.institutionPoints()))
        .orElse(false);
  }

  private static boolean isEqualWithSameScale(BigDecimal currentPoints, BigDecimal newPoints) {
    return Objects.equals(
        adjustScaleAndRoundingMode(currentPoints), adjustScaleAndRoundingMode(newPoints));
  }

  /*
   * Checks whether the set of creators is the same in the request and the candidate.
   * This allows for unverified creators to be converted to verified creators by assuming that
   * a removed creator is replaced by a new creator with the same affiliations.
   */
  private static boolean creatorsAreUpdated(
      UpsertNviCandidateRequest request, Candidate candidate) {
    var oldCreatorCount = candidate.getPublicationDetails().getNviCreators().size();
    var newCreatorCount = getAllCreators(request).size();
    var hasSameCount = oldCreatorCount == newCreatorCount;
    var hasSameCreators = hasSameCreators(request, candidate);
    return !(hasSameCount && hasSameCreators);
  }

  private static boolean hasSameCreators(UpsertNviCandidateRequest request, Candidate candidate) {
    var affiliationsOfRemovedUnverifiedCreators =
        candidate.getPublicationDetails().unverifiedCreators().stream()
            .filter(not(creator -> request.unverifiedCreators().contains(creator)))
            .map(UnverifiedNviCreatorDto::affiliations)
            .map(HashSet::new)
            .toList();

    var currentCreatorIds = candidate.getVerifiedNviCreatorIds();
    var affiliationsOfNewVerifiedCreators =
        request.verifiedCreators().stream()
            .filter(not(creator -> currentCreatorIds.contains(creator.id())))
            .map(VerifiedNviCreatorDto::affiliations)
            .map(HashSet::new)
            .toList();

    var removedInNew =
        affiliationsOfNewVerifiedCreators.containsAll(affiliationsOfRemovedUnverifiedCreators);
    var newInRemoved =
        affiliationsOfRemovedUnverifiedCreators.containsAll(affiliationsOfNewVerifiedCreators);
    return removedInNew && newInRemoved;
  }

  private static boolean instanceTypeIsUpdated(
      UpsertNviCandidateRequest request, Candidate candidate) {
    var newType = request.publicationDetails().publicationType();
    var currentType = candidate.getPublicationDetails().publicationType();
    return !Objects.equals(newType, currentType);
  }

  // FIXME: Add parsing to value class? Original level is mis-mapped to NonCandidateLevel
  private static boolean levelIsUpdated(UpsertNviCandidateRequest request, Candidate candidate) {
    return !Objects.equals(request.level(), candidate.getScientificLevel());
  }

  private static void createCandidate(
      UpsertNviCandidateRequest request, CandidateRepository repository) {
    validateCandidate(request);
    repository.create(mapToCandidate(request), mapToApprovals(request.institutionPoints()));
  }

  private static void validateCandidate(UpsertNviCandidateRequest candidate) {
    attempt(
            () -> {
              candidate.validate();
              Objects.requireNonNull(candidate.instanceType());
              Objects.requireNonNull(candidate.level());
              return candidate;
            })
        .orElseThrow(failure -> new InvalidNviCandidateException(INVALID_CANDIDATE_MESSAGE));
  }

  private static List<InstitutionPoints> mapToInstitutionPoints(CandidateDao candidateDao) {
    if (isNull(candidateDao.candidate().points()) || candidateDao.candidate().points().isEmpty()) {
      return Collections.emptyList();
    } else {
      return candidateDao.candidate().points().stream().map(InstitutionPoints::from).toList();
    }
  }

  private static Map<UUID, Note> mapToNotesMap(
      CandidateRepository repository, List<NoteDao> notes) {
    return notes.stream()
        .map(dao -> new Note(repository, dao.identifier(), dao))
        .collect(Collectors.toMap(Note::getNoteId, Function.identity()));
  }

  private static Map<URI, Approval> mapToApprovalsMap(
      CandidateRepository repository, List<ApprovalStatusDao> approvals) {
    return approvals.stream()
        .map(dao -> new Approval(repository, dao.identifier(), dao))
        .collect(Collectors.toMap(Approval::getInstitutionId, Function.identity()));
  }

  private static PeriodStatus findPeriodStatus(PeriodRepository periodRepository, String year) {
    return periodRepository
        .findByPublishingYear(year)
        .map(PeriodStatus::fromPeriod)
        .orElse(PERIOD_STATUS_NO_PERIOD);
  }

  private static List<DbApprovalStatus> mapToApprovals(List<InstitutionPoints> institutionPoints) {
    return institutionPoints.stream()
        .map(InstitutionPoints::institutionId)
        .map(Candidate::mapToApproval)
        .toList();
  }

  private static DbApprovalStatus mapToApproval(URI institutionId) {
    return DbApprovalStatus.builder().institutionId(institutionId).status(DbStatus.PENDING).build();
  }

  private static DbCandidate mapToCandidate(UpsertNviCandidateRequest request) {
    var allCreators = mapToDbCreators(request.verifiedCreators(), request.unverifiedCreators());
    var publicationDetails = PublicationDetails.from(request).toDbPublication();
    return DbCandidate.builder()
        .publicationId(publicationDetails.id())
        .publicationDetails(publicationDetails)
        .publicationBucketUri(request.publicationBucketUri())
        .publicationIdentifier(publicationDetails.identifier())
        .applicable(publicationDetails.applicable())
        .creators(allCreators)
        .creatorShareCount(request.creatorShareCount())
        .channelId(publicationDetails.publicationChannel().id())
        .channelType(publicationDetails.publicationChannel().channelType())
        .level(DbLevel.parse(publicationDetails.publicationChannel().scientificValue()))
        .instanceType(publicationDetails.publicationType().getValue())
        .publicationDate(publicationDetails.publicationDate())
        .internationalCollaboration(publicationDetails.internationalCollaboration())
        .collaborationFactor(adjustScaleAndRoundingMode(request.collaborationFactor()))
        .basePoints(adjustScaleAndRoundingMode(request.basePoints()))
        .points(mapToPoints(request.institutionPoints()))
        .totalPoints(adjustScaleAndRoundingMode(request.totalPoints()))
        .createdDate(Instant.now())
        .modifiedDate(Instant.now())
        .build();
  }

  private static List<DbInstitutionPoints> mapToPoints(List<InstitutionPoints> points) {
    return points.stream().map(DbInstitutionPoints::from).toList();
  }

  private static List<DbCreatorType> mapToDbCreators(
      Collection<VerifiedNviCreatorDto> verifiedNviCreators,
      Collection<UnverifiedNviCreatorDto> unverifiedNviCreators) {
    var verifiedCreators = verifiedNviCreators.stream().map(VerifiedNviCreatorDto::toDao);
    var unverifiedCreators = unverifiedNviCreators.stream().map(UnverifiedNviCreatorDto::toDao);
    return Stream.concat(verifiedCreators, unverifiedCreators)
        .map(DbCreatorType.class::cast)
        .toList();
  }

  private static boolean isExistingCandidate(URI publicationId, CandidateRepository repository) {
    return repository.findByPublicationId(publicationId).isPresent();
  }

  // TODO: clean up
  private static CandidateDao updateCandidateToNonApplicable(CandidateDao candidateDao) {
    var publicationDetails =
        candidateDao.candidate().publicationDetails().copy().applicable(false).build();
    return candidateDao
        .copy()
        .candidate(
            candidateDao
                .candidate()
                .copy()
                .applicable(false)
                .publicationDetails(publicationDetails)
                .build())
        .periodYear(null)
        .build();
  }

  private Set<URI> getVerifiedNviCreatorIds() {
    return publicationDetails.getVerifiedNviCreatorIds();
  }

  private Builder copy() {
    return new Builder()
        .withIdentifier(identifier)
        .withApplicable(applicable)
        .withApprovals(approvals)
        .withNotes(notes)
        .withInstitutionPoints(institutionPoints)
        .withTotalPoints(totalPoints)
        .withPeriod(period)
        .withBasePoints(basePoints)
        .withInternationalCollaboration(internationalCollaboration)
        .withCollaborationFactor(collaborationFactor)
        .withCreatorShareCount(creatorShareCount)
        .withReportStatus(reportStatus)
        .withModifiedDate(modifiedDate)
        .withCreatedDate(createdDate)
        .withPublicationDetails(publicationDetails);
  }

  private CandidateDao toDao() {
    var dbPublication = publicationDetails.toDbPublication();
    var dbCandidate =
        DbCandidate.builder()
            .publicationDetails(dbPublication)
            .applicable(dbPublication.applicable())
            .creators(dbPublication.creators())
            .creatorShareCount(creatorShareCount)
            .channelType(getPublicationChannelType())
            .channelId(getPublicationChannelId())
            .level(DbLevel.parse(getScientificLevel()))
            .instanceType(dbPublication.publicationType().getValue())
            .publicationDate(dbPublication.publicationDate())
            .internationalCollaboration(dbPublication.internationalCollaboration())
            .collaborationFactor(adjustScaleAndRoundingMode(collaborationFactor))
            .basePoints(adjustScaleAndRoundingMode(basePoints))
            .points(mapToPoints(institutionPoints))
            .totalPoints(adjustScaleAndRoundingMode(totalPoints))
            .createdDate(createdDate)
            .modifiedDate(Instant.now())
            .reportStatus(reportStatus)
            .publicationBucketUri(dbPublication.publicationBucketUri())
            .publicationId(dbPublication.id())
            .publicationIdentifier(dbPublication.identifier())
            .build();
    return CandidateDao.builder()
        .identifier(identifier)
        .candidate(dbCandidate)
        .version(randomUUID().toString())
        .periodYear(dbPublication.publicationDate().year())
        .build();
  }

  private Candidate apply(UpsertNviCandidateRequest request) {
    return this.copy()
        .withApplicable(request.isApplicable())
        .withBasePoints(adjustScaleAndRoundingMode(request.basePoints()))
        .withCollaborationFactor(adjustScaleAndRoundingMode(request.collaborationFactor()))
        .withCreatorShareCount(request.creatorShareCount())
        .withInternationalCollaboration(request.isInternationalCollaboration())
        .withTotalPoints(adjustScaleAndRoundingMode(request.totalPoints()))
        .withPublicationDetails(PublicationDetails.from(request))
        .withInstitutionPoints(request.institutionPoints())
        .withModifiedDate(Instant.now())
        .build();
  }

  private void setUserAsAssigneeIfApprovalIsUnassigned(String username, URI institutionId) {
    approvals.computeIfPresent(
        institutionId, (uri, approval) -> updateAssigneeIfUnassigned(username, approval));
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

  private boolean areAllApprovalsPending() {
    return streamApprovals().map(Approval::getStatus).allMatch(PENDING::equals);
  }

  private boolean areAnyApprovalsPending() {
    return streamApprovals().anyMatch(approval -> PENDING.equals(approval.getStatus()));
  }

  private Stream<Approval> streamApprovals() {
    return approvals.values().stream();
  }

  private void validateCandidateState() {
    if (Status.CLOSED_PERIOD.equals(period.status())) {
      throw new IllegalStateException(PERIOD_CLOSED_MESSAGE);
    }
    if (Status.NO_PERIOD.equals(period.status())
        || Status.UNOPENED_PERIOD.equals(period.status())) {
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

  public static final class Builder {

    private UUID identifier;
    private boolean applicable;
    private Map<URI, Approval> approvals;
    private Map<UUID, Note> notes;
    private List<InstitutionPoints> institutionPoints;
    private BigDecimal totalPoints;
    private PeriodStatus period;
    private PublicationDetails publicationDetails;
    private BigDecimal basePoints;
    private boolean internationalCollaboration;
    private BigDecimal collaborationFactor;
    private int creatorShareCount;
    private Instant createdDate;
    private Instant modifiedDate;
    private ReportStatus reportStatus;

    private Builder() {}

    public Builder withIdentifier(UUID identifier) {
      this.identifier = identifier;
      return this;
    }

    public Builder withApplicable(boolean applicable) {
      this.applicable = applicable;
      return this;
    }

    public Builder withApprovals(Map<URI, Approval> approvals) {
      this.approvals = approvals;
      return this;
    }

    public Builder withNotes(Map<UUID, Note> notes) {
      this.notes = notes;
      return this;
    }

    public Builder withInstitutionPoints(List<InstitutionPoints> institutionPoints) {
      this.institutionPoints = institutionPoints;
      return this;
    }

    public Builder withTotalPoints(BigDecimal totalPoints) {
      this.totalPoints = totalPoints;
      return this;
    }

    public Builder withPeriod(PeriodStatus period) {
      this.period = period;
      return this;
    }

    public Builder withPublicationDetails(PublicationDetails publicationDetails) {
      this.publicationDetails = publicationDetails;
      return this;
    }

    public Builder withBasePoints(BigDecimal basePoints) {
      this.basePoints = basePoints;
      return this;
    }

    public Builder withInternationalCollaboration(boolean internationalCollaboration) {
      this.internationalCollaboration = internationalCollaboration;
      return this;
    }

    public Builder withCollaborationFactor(BigDecimal collaborationFactor) {
      this.collaborationFactor = collaborationFactor;
      return this;
    }

    public Builder withCreatorShareCount(int creatorShareCount) {
      this.creatorShareCount = creatorShareCount;
      return this;
    }

    public Builder withCreatedDate(Instant createdDate) {
      this.createdDate = createdDate;
      return this;
    }

    public Builder withModifiedDate(Instant modifiedDate) {
      this.modifiedDate = modifiedDate;
      return this;
    }

    public Builder withReportStatus(ReportStatus reportStatus) {
      this.reportStatus = reportStatus;
      return this;
    }

    public Candidate build() {
      return new Candidate(
          identifier,
          applicable,
          approvals,
          notes,
          institutionPoints,
          totalPoints,
          period,
          publicationDetails,
          basePoints,
          internationalCollaboration,
          collaborationFactor,
          creatorShareCount,
          createdDate,
          modifiedDate,
          reportStatus);
    }
  }
}
