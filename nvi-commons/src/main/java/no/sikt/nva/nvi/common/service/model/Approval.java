package no.sikt.nva.nvi.common.service.model;

import static java.util.Objects.isNull;
import static java.util.Objects.nonNull;

import java.net.URI;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;
import no.sikt.nva.nvi.common.db.ApprovalStatusDao;
import no.sikt.nva.nvi.common.db.ApprovalStatusDao.DbApprovalStatus;
import no.sikt.nva.nvi.common.db.ApprovalStatusDao.DbStatus;
import no.sikt.nva.nvi.common.db.CandidateRepository;
import no.sikt.nva.nvi.common.model.UpdateApprovalRequest;
import no.sikt.nva.nvi.common.model.UpdateAssigneeRequest;
import no.sikt.nva.nvi.common.model.UpdateStatusRequest;
import nva.commons.core.JacocoGenerated;

public class Approval {

  public static final String ERROR_MSG_USERNAME_NULL = "Username cannot be null";
  private static final String UNKNOWN_REQUEST_TYPE_MESSAGE = "Unknown request type";
  private static final String ERROR_MISSING_REJECTION_REASON =
      "Cannot reject approval status without reason.";
  private static final String ERROR_MSG_MISSING_ORGANIZATION_ID =
      "Request is missing required organization ID";
  private final CandidateRepository repository;
  private final UUID identifier;
  private final URI institutionId;
  private final ApprovalStatus status;
  private final Username assignee;
  private final Username finalizedBy;
  private final Instant finalizedDate;
  private final String reason;

  public Approval(
      CandidateRepository repository, UUID identifier, ApprovalStatusDao dbApprovalStatus) {
    this.repository = repository;
    this.identifier = identifier;
    this.institutionId = dbApprovalStatus.approvalStatus().institutionId();
    this.status = ApprovalStatus.parse(dbApprovalStatus.approvalStatus().status().getValue());
    this.assignee = Username.fromUserName(dbApprovalStatus.approvalStatus().assignee());
    this.finalizedBy = Username.fromUserName(dbApprovalStatus.approvalStatus().finalizedBy());
    this.finalizedDate = dbApprovalStatus.approvalStatus().finalizedDate();
    this.reason = dbApprovalStatus.approvalStatus().reason();
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

  public boolean isAssigned() {
    return nonNull(assignee) && nonNull(assignee.value());
  }

  public boolean isPendingAndUnassigned() {
    return ApprovalStatus.PENDING.equals(status) && !isAssigned();
  }

  public Approval update(UpdateApprovalRequest input) {
    if (input instanceof UpdateAssigneeRequest request) {
      var newDao = repository.updateApprovalStatusDao(identifier, updateAssignee(request));
      return new Approval(repository, identifier, newDao);
    } else if (input instanceof UpdateStatusRequest request) {
      validateUpdateStatusRequest(request);
      var newDao = repository.updateApprovalStatusDao(identifier, updateStatus(request));
      return new Approval(repository, identifier, newDao);
    } else {
      throw new IllegalArgumentException(UNKNOWN_REQUEST_TYPE_MESSAGE);
    }
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

  private DbApprovalStatus updateAssignee(UpdateAssigneeRequest request) {
    return new DbApprovalStatus(
        institutionId,
        DbStatus.parse(status.getValue()),
        no.sikt.nva.nvi.common.db.model.Username.fromString(request.username()),
        no.sikt.nva.nvi.common.db.model.Username.fromUserName(finalizedBy),
        finalizedDate,
        reason);
  }

  private DbApprovalStatus updateStatus(UpdateStatusRequest request) {
    return switch (request.approvalStatus()) {
      case APPROVED -> finalizeApprovedStatus(request);
      case REJECTED -> finalizeRejectedStatus(request);
      case PENDING, NONE -> resetStatus();
    };
  }

  private DbApprovalStatus resetStatus() {
    return new DbApprovalStatus(
        institutionId,
        DbStatus.PENDING,
        no.sikt.nva.nvi.common.db.model.Username.fromUserName(assignee),
        null,
        null,
        null);
  }

  private DbApprovalStatus finalizeApprovedStatus(UpdateStatusRequest request) {
    var username = no.sikt.nva.nvi.common.db.model.Username.fromString(request.username());
    return new DbApprovalStatus(
        institutionId,
        DbStatus.APPROVED,
        assigneeOrUsername(username),
        username,
        Instant.now(),
        null);
  }

  private DbApprovalStatus finalizeRejectedStatus(UpdateStatusRequest request) {
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
