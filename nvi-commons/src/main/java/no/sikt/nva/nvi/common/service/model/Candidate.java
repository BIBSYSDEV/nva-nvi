package no.sikt.nva.nvi.common.service.model;

import static java.util.Collections.emptyList;
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
import java.util.ArrayList;
import java.util.Collection;
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
import no.sikt.nva.nvi.common.dto.UpsertNonNviCandidateRequest;
import no.sikt.nva.nvi.common.dto.UpsertNviCandidateRequest;
import no.sikt.nva.nvi.common.model.InstanceType;
import no.sikt.nva.nvi.common.model.InvalidNviCandidateException;
import no.sikt.nva.nvi.common.model.PointCalculation;
import no.sikt.nva.nvi.common.model.PublicationChannel;
import no.sikt.nva.nvi.common.model.UpdateAssigneeRequest;
import no.sikt.nva.nvi.common.model.UpdateStatusRequest;
import no.sikt.nva.nvi.common.model.UserInstance;
import no.sikt.nva.nvi.common.permissions.CandidatePermissions;
import no.sikt.nva.nvi.common.service.dto.CandidateOperation;
import no.sikt.nva.nvi.common.service.dto.UnverifiedNviCreatorDto;
import no.sikt.nva.nvi.common.service.dto.VerifiedNviCreatorDto;
import no.sikt.nva.nvi.common.service.exception.CandidateNotFoundException;
import no.sikt.nva.nvi.common.service.exception.IllegalCandidateUpdateException;
import no.sikt.nva.nvi.common.service.requests.CreateNoteRequest;
import no.sikt.nva.nvi.common.service.requests.DeleteNoteRequest;
import no.sikt.nva.nvi.common.service.requests.FetchByPublicationRequest;
import no.sikt.nva.nvi.common.service.requests.FetchCandidateRequest;
import nva.commons.apigateway.exceptions.UnauthorizedException;
import nva.commons.core.Environment;
import nva.commons.core.JacocoGenerated;
import nva.commons.core.paths.UriWrapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

// Should be refactored, technical debt task: https://sikt.atlassian.net/browse/NP-48093
@SuppressWarnings({"PMD.GodClass", "PMD.CouplingBetweenObjects"})
public final class Candidate {

  private static final Logger LOGGER = LoggerFactory.getLogger(Candidate.class);
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
  private final CandidateRepository repository;
  private final UUID identifier;
  private final boolean applicable;
  private final Map<URI, Approval> approvals;
  private final Map<UUID, Note> notes;
  private final PeriodStatus period;
  private final PointCalculation pointCalculation;
  private final PublicationDetails publicationDetails;
  private final Instant createdDate;
  private final Instant modifiedDate;
  private final ReportStatus reportStatus;
  private final Long revisionRead;

  private Candidate(
      CandidateRepository repository,
      UUID identifier,
      boolean applicable,
      Map<URI, Approval> approvals,
      Map<UUID, Note> notes,
      PeriodStatus period,
      PointCalculation pointCalculation,
      PublicationDetails publicationDetails,
      Instant createdDate,
      Instant modifiedDate,
      ReportStatus reportStatus,
      Long revisionRead) {
    this.repository = repository;
    this.identifier = identifier;
    this.applicable = applicable;
    this.approvals = approvals;
    this.notes = notes;
    this.period = period;
    this.pointCalculation = pointCalculation;
    this.publicationDetails = publicationDetails;
    this.createdDate = createdDate;
    this.modifiedDate = modifiedDate;
    this.reportStatus = reportStatus;
    this.revisionRead = revisionRead;
  }

  private Candidate(
      CandidateRepository repository,
      CandidateDao candidateDao,
      List<ApprovalStatusDao> approvals,
      List<NoteDao> notes,
      PeriodStatus period) {
    this.repository = repository;
    var dbCandidate = candidateDao.candidate();
    this.identifier = candidateDao.identifier();
    this.applicable = dbCandidate.applicable();
    this.approvals = mapToApprovalsMap(approvals);
    this.notes = mapToNotesMap(repository, notes);
    this.period = period;
    this.pointCalculation = PointCalculation.from(candidateDao);
    this.publicationDetails = PublicationDetails.from(candidateDao);
    this.createdDate = dbCandidate.createdDate();
    this.modifiedDate = dbCandidate.modifiedDate();
    this.reportStatus = dbCandidate.reportStatus();
    this.revisionRead = candidateDao.revision();
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

  public InstanceType getPublicationType() {
    return pointCalculation.instanceType();
  }

  public List<InstitutionPoints> getInstitutionPoints() {
    return Optional.ofNullable(pointCalculation.institutionPoints()).orElse(emptyList());
  }

  // TODO: Make method return InstitutionPoints once we have migrated candidates from Cristin
  public Optional<InstitutionPoints> getInstitutionPoints(URI institutionId) {
    return getInstitutionPoints().stream()
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
    return pointCalculation.basePoints();
  }

  public BigDecimal getCollaborationFactor() {
    return pointCalculation.collaborationFactor();
  }

  public int getCreatorShareCount() {
    return pointCalculation.creatorShareCount();
  }

  public Map<UUID, Note> getNotes() {
    return new HashMap<>(notes);
  }

  public BigDecimal getTotalPoints() {
    return pointCalculation.totalPoints();
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

  public PublicationChannel getPublicationChannel() {
    return pointCalculation.channel();
  }

  public void updateApprovalAssignee(UpdateAssigneeRequest input) {
    validateCandidateState();
    if (!approvals.containsKey(input.institutionId())) {
      LOGGER.error("No approval found matching UpdateAssigneeRequest: {}", input);
      throw new IllegalCandidateUpdateException("No approval found matching UpdateAssigneeRequest");
    }
    var approval = approvals.get(input.institutionId());
    approval.updateAssigneeProperly(repository, toDao(), input);
  }

  public void updateApprovalStatus(UpdateStatusRequest input, UserInstance userInstance) {
    validateUpdateStatusRequest(input);
    validateCandidateState();

    var currentState = getApprovalStatus(input.institutionId());
    var newState = input.approvalStatus();

    if (currentState.equals(newState)) {
      LOGGER.warn(
          "Approval status update attempted with no change: candidateId={}, request={},"
              + " userInstance={}",
          identifier,
          input,
          userInstance);
      return;
    }

    LOGGER.info(
        "Updating approval status: candidateId={}, organizationId={}, oldStatus={}, newStatus={},"
            + " username={}",
        identifier,
        input.institutionId(),
        currentState,
        newState,
        input.username());

    var permissions = new CandidatePermissions(this, userInstance);
    var attemptedOperation = CandidateOperation.fromApprovalStatus(input.approvalStatus());
    try {
      permissions.validateAuthorization(attemptedOperation);
    } catch (UnauthorizedException e) {
      throw new IllegalStateException("Cannot update approval status");
    }

    if (!approvals.containsKey(input.institutionId())) {
      LOGGER.error("No approval found matching UpdateStatusRequest: {}", input);
      throw new IllegalCandidateUpdateException("No approval found matching UpdateStatusRequest");
    }
    var approval = approvals.get(input.institutionId());
    approval.updateStatusProperly(repository, toDao(), input);
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
        period,
        pointCalculation,
        publicationDetails,
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
        && Objects.equals(identifier, candidate.identifier)
        && Objects.equals(approvals, candidate.approvals)
        && Objects.equals(notes, candidate.notes)
        && Objects.equals(period, candidate.period)
        && Objects.equals(pointCalculation, candidate.pointCalculation)
        && Objects.equals(publicationDetails, candidate.publicationDetails)
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
        emptyList(),
        emptyList(),
        PeriodStatus.builder().withStatus(Status.NO_PERIOD).build());
  }

  // TODO: Add validate method to assert invariants, such as approvals matching institutions

  private static void updateCandidate(
      UpsertNviCandidateRequest request, CandidateRepository repository, Candidate candidate) {
    validateCandidate(request);
    var updatedCandidate = candidate.apply(request);
    if (shouldResetCandidate(request, candidate) || isNotApplicable(candidate)) {
      LOGGER.info("Resetting all approvals for candidate {}", candidate.getIdentifier());
      var approvalsToReset = getApprovalsPresentInBoth(candidate, updatedCandidate);
      var approvalsToDelete = getApprovalsToDelete(candidate, updatedCandidate);

      repository.updateCandidateAndDeleteOtherApprovals(
          updatedCandidate.toDao(), approvalsToReset, approvalsToDelete);
    } else {
      var approvalsToReset = getIndividualApprovalsToReset(candidate, updatedCandidate);
      LOGGER.info(
          "Resetting individual approvals for candidate {}: {}",
          candidate.getIdentifier(),
          approvalsToReset);
      repository.updateCandidateAndDeleteOtherApprovals(
          updatedCandidate.toDao(), approvalsToReset, emptyList());
    }
  }

  /**
   * Returns new approvals in a pending state for all institutions that should have their approvals
   * reset because their points have changed.
   */
  private static List<ApprovalStatusDao> getIndividualApprovalsToReset(
      Candidate currentCandidate, Candidate updatedCandidate) {
    var oldApprovals = currentCandidate.getApprovals();
    var newApprovals = new ArrayList<ApprovalStatusDao>();
    var updatedPoints =
        updatedCandidate.getInstitutionPoints().stream()
            .filter(
                institutionPoints -> !hasSameInstitutionPoints(currentCandidate, institutionPoints))
            .toList();
    var statusesBasedOnNewPoints = mapToApprovals(updatedPoints);

    for (var newStatus : statusesBasedOnNewPoints) {
      var oldApproval = oldApprovals.get(newStatus.institutionId());
      var expectedRevision = isNull(oldApproval) ? null : oldApproval.getRevisionRead();
      var newApproval = new Approval(currentCandidate.getIdentifier(), newStatus, expectedRevision);
      newApprovals.add(newApproval.toDao());
    }

    return newApprovals;
  }

  /**
   * Returns current approvals for all institutions that should have their approval deleted because
   * they no longer have associated points.
   */
  private static List<ApprovalStatusDao> getApprovalsToDelete(
      Candidate currentCandidate, Candidate updatedCandidate) {
    return currentCandidate.getApprovals().keySet().stream()
        .filter(not(id -> updatedCandidate.getInstitutionPoints(id).isPresent()))
        .map(id -> currentCandidate.getApprovals().get(id))
        .map(Approval::toDao)
        .toList();
  }

  /**
   * Returns reset approvals for all institutions that still have points after the candidate is
   * updated.
   */
  private static List<ApprovalStatusDao> getApprovalsPresentInBoth(
      Candidate currentCandidate, Candidate updatedCandidate) {
    var oldApprovals = currentCandidate.getApprovals();
    var newApprovals = new ArrayList<ApprovalStatusDao>();
    var statusesBasedOnNewPoints = mapToApprovals(updatedCandidate.getInstitutionPoints());

    for (var newStatus : statusesBasedOnNewPoints) {
      var oldApproval = oldApprovals.get(newStatus.institutionId());
      var expectedRevision = isNull(oldApproval) ? null : oldApproval.getRevisionRead();
      var newApproval = new Approval(currentCandidate.getIdentifier(), newStatus, expectedRevision);
      newApprovals.add(newApproval.toDao());
    }
    return newApprovals;
  }

  private static boolean isNotApplicable(Candidate candidate) {
    return !candidate.isApplicable();
  }

  private static boolean shouldResetCandidate(
      UpsertNviCandidateRequest request, Candidate candidate) {
    return publicationChannelIsUpdated(request, candidate)
        || instanceTypeIsUpdated(request, candidate)
        || creatorsAreUpdated(request, candidate)
        || hasChangeInTopLevelOrganizations(request, candidate)
        || publicationYearIsUpdated(request, candidate);
  }

  private static boolean publicationChannelIsUpdated(
      UpsertNviCandidateRequest request, Candidate candidate) {
    var requestChannel = PublicationChannel.from(request.pointCalculation().channel());
    return !requestChannel.equals(candidate.getPublicationChannel());
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
        request.pointCalculation().institutionPoints().stream()
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
    var oldCreatorCount = candidate.getPublicationDetails().nviCreators().size();
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
    var newType = request.pointCalculation().instanceType();
    var currentType = candidate.getPublicationType();
    return !Objects.equals(newType, currentType);
  }

  private static void createCandidate(
      UpsertNviCandidateRequest request, CandidateRepository repository) {
    validateCandidate(request);
    repository.create(
        mapToCandidate(request), mapToApprovals(request.pointCalculation().institutionPoints()));
  }

  private static void validateCandidate(UpsertNviCandidateRequest candidate) {
    attempt(
            () -> {
              candidate.validate();
              return candidate;
            })
        .orElseThrow(failure -> new InvalidNviCandidateException(INVALID_CANDIDATE_MESSAGE));
  }

  private static Map<UUID, Note> mapToNotesMap(
      CandidateRepository repository, List<NoteDao> notes) {
    return notes.stream()
        .map(dao -> new Note(repository, dao.identifier(), dao))
        .collect(Collectors.toMap(Note::getNoteId, Function.identity()));
  }

  private static Map<URI, Approval> mapToApprovalsMap(List<ApprovalStatusDao> approvals) {
    return approvals.stream()
        .map(dao -> new Approval(dao.identifier(), dao))
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
    var dbDetails = PublicationDetails.from(request).toDbPublication();
    var dbPointCalculation = PointCalculation.from(request).toDbPointCalculation();
    return DbCandidate.builder()
        .publicationId(dbDetails.id())
        .pointCalculation(dbPointCalculation)
        .publicationDetails(dbDetails)
        .publicationBucketUri(request.publicationBucketUri())
        .publicationIdentifier(dbDetails.identifier())
        .applicable(request.isApplicable())
        .creators(allCreators)
        .creatorShareCount(dbPointCalculation.creatorShareCount())
        .channelId(dbPointCalculation.publicationChannel().id())
        .channelType(dbPointCalculation.publicationChannel().channelType())
        .level(DbLevel.parse(dbPointCalculation.publicationChannel().scientificValue()))
        .instanceType(request.pointCalculation().instanceType().getValue())
        .publicationDate(dbDetails.publicationDate())
        .internationalCollaboration(dbPointCalculation.internationalCollaboration())
        .collaborationFactor(dbPointCalculation.collaborationFactor())
        .basePoints(dbPointCalculation.basePoints())
        .points(dbPointCalculation.institutionPoints())
        .totalPoints(dbPointCalculation.totalPoints())
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

  private static CandidateDao updateCandidateToNonApplicable(CandidateDao candidateDao) {
    return candidateDao
        .copy()
        .candidate(candidateDao.candidate().copy().applicable(false).build())
        .periodYear(null)
        .build();
  }

  private Set<URI> getVerifiedNviCreatorIds() {
    return publicationDetails.getVerifiedNviCreatorIds();
  }

  private Builder copy() {
    return new Builder()
        .withRepository(repository)
        .withIdentifier(identifier)
        .withApplicable(applicable)
        .withApprovals(approvals)
        .withNotes(notes)
        .withPeriod(period)
        .withReportStatus(reportStatus)
        .withModifiedDate(modifiedDate)
        .withCreatedDate(createdDate)
        .withPointCalculation(pointCalculation)
        .withPublicationDetails(publicationDetails)
        .withRevision(revisionRead);
  }

  private CandidateDao toDao() {
    var dbPublication = publicationDetails.toDbPublication();
    var dbChannel = pointCalculation.channel().toDbPublicationChannel();
    var dbCandidate =
        DbCandidate.builder()
            .pointCalculation(pointCalculation.toDbPointCalculation())
            .publicationDetails(dbPublication)
            .applicable(publicationDetails.isApplicable())
            .creators(dbPublication.creators())
            .creatorShareCount(getCreatorShareCount())
            .channelType(dbChannel.channelType())
            .channelId(dbChannel.id())
            .level(DbLevel.parse(dbChannel.scientificValue()))
            .instanceType(pointCalculation.instanceType().getValue())
            .publicationDate(dbPublication.publicationDate())
            .internationalCollaboration(pointCalculation.isInternationalCollaboration())
            .collaborationFactor(adjustScaleAndRoundingMode(getCollaborationFactor()))
            .basePoints(adjustScaleAndRoundingMode(getBasePoints()))
            .points(mapToPoints(getInstitutionPoints()))
            .totalPoints(adjustScaleAndRoundingMode(getTotalPoints()))
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
        .revision(revisionRead)
        .version(randomUUID().toString())
        .periodYear(dbPublication.publicationDate().year())
        .build();
  }

  private Candidate apply(UpsertNviCandidateRequest request) {
    return this.copy()
        .withApplicable(request.isApplicable())
        .withPointCalculation(PointCalculation.from(request))
        .withPublicationDetails(PublicationDetails.from(request))
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
        : approval.updateAssigneeProperly(
            repository, toDao(), new UpdateAssigneeRequest(approval.getInstitutionId(), username));
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

  public static final class Builder {

    private CandidateRepository repository;
    private UUID identifier;
    private boolean applicable;
    private Map<URI, Approval> approvals;
    private Map<UUID, Note> notes;
    private PeriodStatus period;
    private PointCalculation pointCalculation;
    private PublicationDetails publicationDetails;
    private Instant createdDate;
    private Instant modifiedDate;
    private ReportStatus reportStatus;
    private Long revision;

    private Builder() {}

    public Builder withRepository(CandidateRepository repository) {
      this.repository = repository;
      return this;
    }

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

    public Builder withPeriod(PeriodStatus period) {
      this.period = period;
      return this;
    }

    public Builder withPointCalculation(PointCalculation pointCalculation) {
      this.pointCalculation = pointCalculation;
      return this;
    }

    public Builder withPublicationDetails(PublicationDetails publicationDetails) {
      this.publicationDetails = publicationDetails;
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

    public Builder withRevision(Long revision) {
      this.revision = revision;
      return this;
    }

    public Candidate build() {
      return new Candidate(
          repository,
          identifier,
          applicable,
          approvals,
          notes,
          period,
          pointCalculation,
          publicationDetails,
          createdDate,
          modifiedDate,
          reportStatus,
          revision);
    }
  }
}
