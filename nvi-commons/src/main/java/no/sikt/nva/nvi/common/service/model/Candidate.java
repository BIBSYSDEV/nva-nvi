package no.sikt.nva.nvi.common.service.model;

import static java.util.Collections.emptyList;
import static java.util.Collections.emptyMap;
import static java.util.Objects.isNull;
import static java.util.Objects.nonNull;
import static java.util.UUID.randomUUID;
import static java.util.function.Predicate.not;
import static no.sikt.nva.nvi.common.db.ReportStatus.REPORTED;
import static no.sikt.nva.nvi.common.service.model.Approval.createNewApproval;
import static no.sikt.nva.nvi.common.service.model.ApprovalStatus.APPROVED;
import static no.sikt.nva.nvi.common.service.model.ApprovalStatus.PENDING;
import static no.sikt.nva.nvi.common.service.model.ApprovalStatus.REJECTED;
import static no.sikt.nva.nvi.common.utils.DecimalUtils.adjustScaleAndRoundingMode;
import static no.sikt.nva.nvi.common.utils.RequestUtil.getAllCreators;
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
import no.sikt.nva.nvi.common.db.CandidateDao;
import no.sikt.nva.nvi.common.db.CandidateDao.DbCandidate;
import no.sikt.nva.nvi.common.db.CandidateDao.DbInstitutionPoints;
import no.sikt.nva.nvi.common.db.CandidateDao.DbLevel;
import no.sikt.nva.nvi.common.db.NoteDao;
import no.sikt.nva.nvi.common.db.ReportStatus;
import no.sikt.nva.nvi.common.dto.UpsertNviCandidateRequest;
import no.sikt.nva.nvi.common.model.InstanceType;
import no.sikt.nva.nvi.common.model.PointCalculation;
import no.sikt.nva.nvi.common.model.PublicationChannel;
import no.sikt.nva.nvi.common.model.UpdateApprovalRequest;
import no.sikt.nva.nvi.common.model.UpdateAssigneeRequest;
import no.sikt.nva.nvi.common.model.UpdateStatusRequest;
import no.sikt.nva.nvi.common.service.dto.UnverifiedNviCreatorDto;
import no.sikt.nva.nvi.common.service.dto.VerifiedNviCreatorDto;
import no.sikt.nva.nvi.common.service.exception.IllegalCandidateUpdateException;
import no.sikt.nva.nvi.common.service.requests.CreateNoteRequest;
import no.sikt.nva.nvi.common.service.requests.DeleteNoteRequest;
import nva.commons.core.Environment;
import nva.commons.core.paths.UriWrapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

// Should be refactored, technical debt task: https://sikt.atlassian.net/browse/NP-48093
@SuppressWarnings({"PMD.GodClass", "PMD.CouplingBetweenObjects"})
public record Candidate(
    UUID identifier,
    boolean applicable,
    Map<URI, Approval> approvals,
    Map<UUID, Note> notes,
    Optional<NviPeriod> period, // FIXME: Can this be NviPeriod?
    PointCalculation pointCalculation,
    PublicationDetails publicationDetails,
    Instant createdDate,
    Instant modifiedDate,
    ReportStatus reportStatus,
    Long revision,
    Environment environment) {

  private static final Logger LOGGER = LoggerFactory.getLogger(Candidate.class);
  private static final String CONTEXT = stringFromResources(Path.of("nviCandidateContext.json"));
  private static final String CANDIDATE_PATH = "candidate";
  private static final String PERIOD_CLOSED_MESSAGE =
      "Period is closed, perform actions on candidate is forbidden!";
  private static final String PERIOD_NOT_OPENED_MESSAGE =
      "Period is not opened yet, perform actions on candidate is forbidden!";

  public static Candidate fromDao(
      CandidateDao candidateDao,
      Collection<ApprovalStatusDao> approvals,
      Collection<NoteDao> notes,
      Optional<NviPeriod> period,
      Environment environment) {
    var dbCandidate = candidateDao.candidate();

    return new Builder()
        .withIdentifier(candidateDao.identifier())
        .withApplicable(dbCandidate.applicable())
        .withApprovals(mapToApprovalsMap(approvals))
        .withNotes(mapToNotesMap(notes))
        .withPeriod(period)
        .withPointCalculation(PointCalculation.from(candidateDao))
        .withPublicationDetails(PublicationDetails.from(candidateDao))
        .withCreatedDate(dbCandidate.createdDate())
        .withModifiedDate(dbCandidate.modifiedDate())
        .withReportStatus(dbCandidate.reportStatus())
        .withRevision(candidateDao.revision())
        .withEnvironment(environment)
        .build();
  }

  public static Candidate fromRequest(
      UUID identifier,
      UpsertNviCandidateRequest request,
      NviPeriod targetPeriod,
      Environment environment) {
    if (!targetPeriod.isOpen()) {
      throw new IllegalCandidateUpdateException("Target period is not open");
    }

    var approvals =
        request.pointCalculation().institutionPoints().stream()
            .map(InstitutionPoints::institutionId)
            .map(institutionId -> createNewApproval(identifier, institutionId))
            .collect(Collectors.toMap(Approval::institutionId, Function.identity()));

    return new Builder()
        .withIdentifier(identifier)
        .withApplicable(request.isApplicable())
        .withApprovals(approvals)
        .withPeriod(Optional.of(targetPeriod))
        .withPointCalculation(PointCalculation.from(request))
        .withPublicationDetails(PublicationDetails.from(request))
        .withCreatedDate(Instant.now())
        .withModifiedDate(Instant.now())
        .withEnvironment(environment)
        .build();
  }

  public Candidate apply(UpsertNviCandidateRequest request, NviPeriod targetPeriod) {
    if (isReported()) {
      throw new IllegalCandidateUpdateException("Cannot update reported candidate");
    }
    if (!canUpdateInPeriod(targetPeriod)) {
      throw new IllegalCandidateUpdateException("Cannot move candidate to period that is not open");
    }

    return this.copy()
        .withApplicable(request.isApplicable())
        .withPointCalculation(PointCalculation.from(request))
        .withPublicationDetails(PublicationDetails.from(request))
        .withPeriod(Optional.of(targetPeriod))
        .withModifiedDate(Instant.now())
        .build();
  }

  public Candidate updateToNonCandidate() {
    if (isReported()) {
      throw new IllegalCandidateUpdateException("Cannot update reported candidate");
    }

    return copy()
        .withPeriod(Optional.empty())
        .withApplicable(false)
        .withApprovals(emptyMap())
        .build();
  }

  private boolean canUpdateInPeriod(NviPeriod targetPeriod) {
    var hasSamePeriod =
        period.filter(currentPeriod -> targetPeriod.id().equals(currentPeriod.id())).isPresent();
    return hasSamePeriod || targetPeriod.isOpen();
  }

  public CandidateDao toDao() {
    var dbPublication = publicationDetails.toDbPublication();
    var dbChannel = pointCalculation.channel().toDbPublicationChannel();
    var dbCandidate =
        DbCandidate.builder()
            .pointCalculation(pointCalculation.toDbPointCalculation())
            .publicationDetails(dbPublication)
            .applicable(applicable)
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
            .modifiedDate(modifiedDate)
            .reportStatus(reportStatus)
            .publicationBucketUri(dbPublication.publicationBucketUri())
            .publicationId(dbPublication.id())
            .publicationIdentifier(dbPublication.identifier())
            .build();
    var periodYear = period.map(NviPeriod::publishingYear).map(Object::toString).orElse(null);
    return CandidateDao.builder()
        .identifier(identifier)
        .candidate(dbCandidate)
        .revision(revision)
        .version(randomUUID().toString()) // FIXME
        .periodYear(periodYear)
        .build();
  }

  public static String getJsonLdContext() {
    return CONTEXT;
  }

  public URI getContextUri() {
    var basePath = environment.readEnv("CUSTOM_DOMAIN_BASE_PATH");
    var apiHost = environment.readEnv("API_HOST");
    return UriWrapper.fromHost(apiHost).addChild(basePath, "context").getUri();
  }

  // FIXME: Clean-up duplication here
  public URI getId() {
    var basePath = environment.readEnv("CUSTOM_DOMAIN_BASE_PATH");
    var apiHost = environment.readEnv("API_HOST");
    return new UriWrapper(HTTPS, apiHost)
        .addChild(basePath, CANDIDATE_PATH, identifier.toString())
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
    return nonNull(approval) ? approval.status() : ApprovalStatus.NONE;
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
    return !isReported() && period.filter(NviPeriod::isClosed).isPresent();
  }

  public boolean isUnderReview() {
    return !areAllApprovalsPending() && period.filter(NviPeriod::isOpen).isPresent();
  }

  public boolean isPendingReview() {
    return areAllApprovalsPending() && period.filter(NviPeriod::isOpen).isPresent();
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

  public Approval updateApprovalAssignee(UpdateAssigneeRequest request) {
    validateCandidateState();
    var updatedApproval = getApproval(request).withAssignee(request);
    approvals.put(request.institutionId(), updatedApproval);
    return updatedApproval;
  }

  public Approval updateApprovalStatus(UpdateStatusRequest request) {
    validateCandidateState();
    var currentApproval = getApproval(request);
    return currentApproval.withStatus(request);
  }

  private Approval getApproval(UpdateApprovalRequest request) {
    var approval = approvals.get(request.institutionId());
    if (isNull(approval)) {
      LOGGER.error("No approval found matching request: {}", request);
      throw new IllegalCandidateUpdateException("No approval found matching request");
    }
    return approval;
  }

  public NoteCreationResult createNote(CreateNoteRequest input) {
    validateCandidateState();
    var note = Note.fromRequest(input, identifier);
    notes.put(note.noteIdentifier(), note);

    var updatedApproval = assignUserToApprovalIfUnassigned(input.username(), input.institutionId());

    return new NoteCreationResult(note, updatedApproval);
  }

  public Note deleteNote(DeleteNoteRequest request) {
    validateCandidateState();
    var note = notes.get(request.noteId());
    note.validateOwner(request.username());
    notes.remove(request.noteId());

    return note;
  }

  public URI getPublicationId() {
    return publicationDetails.publicationId();
  }

  public List<URI> getNviCreatorAffiliations() {
    return publicationDetails.getNviCreatorAffiliations();
  }

  public static List<InstitutionPoints> getUpdatedInstitutionPoints(
      Candidate currentCandidate, Candidate updatedCandidate) {
    return updatedCandidate.getInstitutionPoints().stream()
        .filter(institutionPoints -> !hasSameInstitutionPoints(currentCandidate, institutionPoints))
        .toList();
  }

  /**
   * Returns current approvals for all institutions that should have their approval deleted because
   * they no longer have associated points.
   */
  public static List<ApprovalStatusDao> getApprovalsToDelete(
      Candidate currentCandidate, Candidate updatedCandidate) {
    return currentCandidate.getApprovals().keySet().stream()
        .filter(not(id -> updatedCandidate.getInstitutionPoints(id).isPresent()))
        .map(id -> currentCandidate.getApprovals().get(id))
        .map(Approval::toDao)
        .toList();
  }

  public static boolean shouldResetCandidate(
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
    var publicationYearOfCandidate = candidate.publicationDetails().publicationDate().year();
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
    var oldCreatorCount = candidate.publicationDetails().nviCreators().size();
    var newCreatorCount = getAllCreators(request).size();
    var hasSameCount = oldCreatorCount == newCreatorCount;
    var hasSameCreators = hasSameCreators(request, candidate);
    return !(hasSameCount && hasSameCreators);
  }

  private static boolean hasSameCreators(UpsertNviCandidateRequest request, Candidate candidate) {
    var affiliationsOfRemovedUnverifiedCreators =
        candidate.publicationDetails().unverifiedCreators().stream()
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

  private static Map<UUID, Note> mapToNotesMap(Collection<NoteDao> notes) {
    return notes.stream()
        .map(Note::fromDao)
        .collect(Collectors.toMap(Note::noteIdentifier, Function.identity()));
  }

  private static Map<URI, Approval> mapToApprovalsMap(Collection<ApprovalStatusDao> approvals) {
    return approvals.stream()
        .map(Approval::fromDao)
        .collect(Collectors.toMap(Approval::institutionId, Function.identity()));
  }

  public List<Approval> createResetApprovalsForInstitutions(
      Collection<InstitutionPoints> institutionPoints) {
    var oldApprovals = getApprovals();
    var resetApprovals = new ArrayList<Approval>();
    for (var institutionPoint : institutionPoints) {
      var organizationId = institutionPoint.institutionId();
      var currentApproval = oldApprovals.get(organizationId);
      var newApproval =
          isNull(currentApproval)
              ? createNewApproval(identifier, organizationId)
              : currentApproval.resetApproval();
      resetApprovals.add(newApproval);
    }
    return resetApprovals;
  }

  private static List<DbInstitutionPoints> mapToPoints(List<InstitutionPoints> points) {
    return points.stream().map(DbInstitutionPoints::from).toList();
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
        .withPeriod(period)
        .withReportStatus(reportStatus)
        .withModifiedDate(modifiedDate)
        .withCreatedDate(createdDate)
        .withPointCalculation(pointCalculation)
        .withPublicationDetails(publicationDetails)
        .withRevision(revision);
  }

  /**
   * Creates a copy of the approval with the user assigned, if the approval exists and is currently
   * unassigned.
   *
   * @return Optional containing the updated approval, or empty if no update needed
   */
  private Optional<Approval> assignUserToApprovalIfUnassigned(String username, URI institutionId) {
    var approval = approvals.get(institutionId);
    if (isNull(approval) || approval.isAssigned()) {
      return Optional.empty();
    }
    var updatedApproval = approval.withAssignee(new UpdateAssigneeRequest(institutionId, username));
    return Optional.of(updatedApproval);
  }

  private boolean isDispute() {
    var approvalStatuses = streamApprovals().map(Approval::status).toList();
    return approvalStatuses.stream().anyMatch(APPROVED::equals)
        && approvalStatuses.stream().anyMatch(REJECTED::equals);
  }

  private boolean areAllApprovalsApproved() {
    return streamApprovals().map(Approval::status).allMatch(APPROVED::equals);
  }

  private boolean areAllApprovalsPending() {
    return streamApprovals().map(Approval::status).allMatch(PENDING::equals);
  }

  private boolean areAnyApprovalsPending() {
    return streamApprovals().anyMatch(approval -> PENDING.equals(approval.status()));
  }

  private Stream<Approval> streamApprovals() {
    return approvals.values().stream();
  }

  private void validateCandidateState() {
    if (period.isEmpty()) {
      throw new IllegalStateException(PERIOD_NOT_OPENED_MESSAGE);
    }
    if (period.filter(NviPeriod::isOpen).isEmpty()) {
      throw new IllegalStateException(PERIOD_NOT_OPENED_MESSAGE);
    }
    if (period.filter(NviPeriod::isClosed).isPresent()) {
      throw new IllegalStateException(PERIOD_CLOSED_MESSAGE);
    }
  }

  public static final class Builder {

    private UUID identifier;
    private boolean applicable;
    private Map<URI, Approval> approvals;
    private Map<UUID, Note> notes;
    private Optional<NviPeriod> period;
    private PointCalculation pointCalculation;
    private PublicationDetails publicationDetails;
    private Instant createdDate;
    private Instant modifiedDate;
    private ReportStatus reportStatus;
    private Long revision;
    private Environment environment;

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

    public Builder withPeriod(Optional<NviPeriod> period) {
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

    public Builder withEnvironment(Environment environment) {
      this.environment = environment;
      return this;
    }

    public Candidate build() {
      return new Candidate(
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
          revision,
          environment);
    }
  }
}
