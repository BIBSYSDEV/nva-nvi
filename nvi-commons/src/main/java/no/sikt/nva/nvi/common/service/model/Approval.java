package no.sikt.nva.nvi.common.service.model;

import static java.util.Objects.isNull;
import static java.util.Objects.nonNull;
import static java.util.UUID.randomUUID;
import static no.sikt.nva.nvi.common.service.model.ApprovalStatus.toDbStatus;

import java.net.URI;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;
import no.sikt.nva.nvi.common.db.ApprovalStatusDao;
import no.sikt.nva.nvi.common.db.ApprovalStatusDao.DbApprovalStatus;
import no.sikt.nva.nvi.common.db.ApprovalStatusDao.DbStatus;
import no.sikt.nva.nvi.common.db.CandidateDao;
import no.sikt.nva.nvi.common.db.CandidateRepository;
import no.sikt.nva.nvi.common.model.UpdateAssigneeRequest;
import no.sikt.nva.nvi.common.model.UpdateStatusRequest;
import nva.commons.core.JacocoGenerated;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class Approval {

  private static final Logger LOGGER = LoggerFactory.getLogger(Approval.class);
  public static final String ERROR_MSG_USERNAME_NULL = "Username cannot be null";
  private static final String ERROR_MISSING_REJECTION_REASON =
      "Cannot reject approval status without reason.";
  private static final String ERROR_MSG_MISSING_ORGANIZATION_ID =
      "Request is missing required organization ID";
  private final UUID identifier;
  private final URI institutionId;
  private final ApprovalStatus status;
  private final Username assignee;
  private final Username finalizedBy;
  private final Instant finalizedDate;
  private final String reason;
  private final Long revisionRead;

  public Approval(UUID candidateIdentifier, ApprovalStatusDao dbApprovalStatus) {
    this.identifier = candidateIdentifier;
    this.institutionId = dbApprovalStatus.approvalStatus().institutionId();
    this.status = ApprovalStatus.parse(dbApprovalStatus.approvalStatus().status().getValue());
    this.assignee = Username.fromUserName(dbApprovalStatus.approvalStatus().assignee());
    this.finalizedBy = Username.fromUserName(dbApprovalStatus.approvalStatus().finalizedBy());
    this.finalizedDate = dbApprovalStatus.approvalStatus().finalizedDate();
    this.reason = dbApprovalStatus.approvalStatus().reason();
    this.revisionRead = dbApprovalStatus.revision();
  }

  public Approval(UUID candidateIdentifier, DbApprovalStatus approvalStatus, Long revisionRead) {
    this.identifier = candidateIdentifier;
    this.institutionId = approvalStatus.institutionId();
    this.status = ApprovalStatus.parse(approvalStatus.status().getValue());
    this.assignee = Username.fromUserName(approvalStatus.assignee());
    this.finalizedBy = Username.fromUserName(approvalStatus.finalizedBy());
    this.finalizedDate = approvalStatus.finalizedDate();
    this.reason = approvalStatus.reason();
    this.revisionRead = revisionRead;
  }

  public URI getInstitutionId() {
    return institutionId;
  }

  public ApprovalStatus getStatus() {
    return status;
  }

  public String getAssigneeUsername() {
    return isNull(assignee) ? null : assignee.value();
  }

  public String getFinalizedByUserName() {
    return isNull(finalizedBy) ? null : finalizedBy.value();
  }

  public Instant getFinalizedDate() {
    return finalizedDate;
  }

  public String getReason() {
    return reason;
  }

  public Long getRevisionRead() {
    return revisionRead;
  }

  public boolean isAssigned() {
    return nonNull(assignee) && nonNull(assignee.value());
  }

  public boolean isPendingAndUnassigned() {
    return ApprovalStatus.PENDING.equals(status) && !isAssigned();
  }

  public Approval updateAssignee(
      CandidateRepository repository, CandidateDao candidate, UpdateAssigneeRequest request) {
    LOGGER.info("Updating assignee for candidateId={}: {}", identifier, request);
    LOGGER.info("Current assignee for institutionId={}: {}", institutionId, assignee);
    var updatedDbStatus = createUpdatedStatus(request);
    var updatedApprovalDao = this.toDao(updatedDbStatus);
    var newDao = repository.updateApprovalStatusDao(candidate, updatedApprovalDao);
    var newAssignee = newDao.approvalStatus().assignee();
    LOGGER.info("Assignee updated successfully: {} -> {}", assignee, newAssignee);
    return new Approval(identifier, newDao);
  }

  public Approval updateStatus(
      CandidateRepository repository, CandidateDao candidate, UpdateStatusRequest request) {
    LOGGER.info("Updating approval status for candidateId={}: {}", identifier, request);
    LOGGER.info("Current status for institutionId={}: {}", institutionId, status);
    validateUpdateStatusRequest(request);
    var updatedDbStatus = createUpdatedStatus(request);
    var updatedApprovalDao = this.toDao(updatedDbStatus);
    var newDao = repository.updateApprovalStatusDao(candidate, updatedApprovalDao);
    var newStatus = newDao.approvalStatus().status();
    LOGGER.info("Approval status updated successfully: {} -> {}", status, newStatus);
    return new Approval(identifier, newDao);
  }

  public ApprovalStatusDao toDao(DbApprovalStatus dbStatus) {
    return ApprovalStatusDao.builder()
        .identifier(identifier)
        .approvalStatus(dbStatus)
        .revision(revisionRead)
        .version(randomUUID().toString())
        .build();
  }

  public ApprovalStatusDao toDao() {
    return ApprovalStatusDao.builder()
        .identifier(identifier)
        .approvalStatus(createCopyOfCurrentStatus())
        .revision(revisionRead)
        .version(randomUUID().toString())
        .build();
  }

  @Override
  @JacocoGenerated
  public int hashCode() {
    return Objects.hash(
        identifier, institutionId, status, assignee, finalizedBy, finalizedDate, reason);
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
    Approval approval = (Approval) o;
    return Objects.equals(identifier, approval.identifier)
        && Objects.equals(institutionId, approval.institutionId)
        && Objects.equals(status, approval.status)
        && Objects.equals(assignee, approval.assignee)
        && Objects.equals(finalizedBy, approval.finalizedBy)
        && Objects.equals(finalizedDate, approval.finalizedDate)
        && Objects.equals(reason, approval.reason);
  }

  @Override
  @JacocoGenerated
  public String toString() {
    return "Approval{"
        + "identifier="
        + identifier
        + ", institutionId="
        + institutionId
        + ", status="
        + status
        + ", assignee="
        + assignee
        + ", finalizedBy="
        + finalizedBy
        + ", finalizedDate="
        + finalizedDate
        + ", reason='"
        + reason
        + '\''
        + '}';
  }

  private DbApprovalStatus createUpdatedStatus(UpdateAssigneeRequest request) {
    return new DbApprovalStatus(
        institutionId,
        DbStatus.parse(status.getValue()),
        no.sikt.nva.nvi.common.db.model.Username.fromString(request.username()),
        no.sikt.nva.nvi.common.db.model.Username.fromUserName(finalizedBy),
        finalizedDate,
        reason);
  }

  private DbApprovalStatus createUpdatedStatus(UpdateStatusRequest request) {
    return switch (request.approvalStatus()) {
      case APPROVED -> createFinalizedStatus(request);
      case REJECTED -> createRejectedStatus(request);
      case PENDING, NONE -> createResetStatus();
    };
  }

  private DbApprovalStatus createResetStatus() {
    return new DbApprovalStatus(
        institutionId,
        DbStatus.PENDING,
        no.sikt.nva.nvi.common.db.model.Username.fromUserName(assignee),
        null,
        null,
        null);
  }

  private DbApprovalStatus createCopyOfCurrentStatus() {
    return new DbApprovalStatus(
        institutionId,
        toDbStatus(status),
        no.sikt.nva.nvi.common.db.model.Username.fromUserName(assignee),
        no.sikt.nva.nvi.common.db.model.Username.fromUserName(finalizedBy),
        finalizedDate,
        reason);
  }

  private DbApprovalStatus createFinalizedStatus(UpdateStatusRequest request) {
    var username = no.sikt.nva.nvi.common.db.model.Username.fromString(request.username());
    return new DbApprovalStatus(
        institutionId,
        DbStatus.APPROVED,
        assigneeOrUsername(username),
        username,
        Instant.now(),
        null);
  }

  private DbApprovalStatus createRejectedStatus(UpdateStatusRequest request) {
    if (isNull(request.reason())) {
      throw new UnsupportedOperationException(ERROR_MISSING_REJECTION_REASON);
    }
    var username = no.sikt.nva.nvi.common.db.model.Username.fromString(request.username());
    return new DbApprovalStatus(
        institutionId,
        DbStatus.REJECTED,
        assigneeOrUsername(username),
        username,
        Instant.now(),
        request.reason());
  }

  private no.sikt.nva.nvi.common.db.model.Username assigneeOrUsername(
      no.sikt.nva.nvi.common.db.model.Username username) {
    return isAssigned()
        ? no.sikt.nva.nvi.common.db.model.Username.fromUserName(assignee)
        : username;
  }

  public static void validateUpdateStatusRequest(UpdateStatusRequest input) {
    if (isNull(input.username())) {
      throw new IllegalArgumentException(ERROR_MSG_USERNAME_NULL);
    }
    if (isNull(input.institutionId())) {
      throw new IllegalArgumentException(ERROR_MSG_MISSING_ORGANIZATION_ID);
    }
  }
}
