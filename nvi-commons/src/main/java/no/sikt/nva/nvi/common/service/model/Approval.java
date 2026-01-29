package no.sikt.nva.nvi.common.service.model;

import static java.util.Objects.isNull;
import static java.util.Objects.nonNull;
import static java.util.UUID.randomUUID;
import static java.util.function.Predicate.not;
import static no.sikt.nva.nvi.common.service.model.ApprovalStatus.toDbStatus;
import static nva.commons.core.StringUtils.isBlank;

import java.net.URI;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import no.sikt.nva.nvi.common.db.ApprovalStatusDao;
import no.sikt.nva.nvi.common.db.ApprovalStatusDao.DbApprovalStatus;
import no.sikt.nva.nvi.common.model.UpdateAssigneeRequest;
import no.sikt.nva.nvi.common.model.UpdateStatusRequest;
import nva.commons.core.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public record Approval(
    UUID identifier,
    URI institutionId,
    ApprovalStatus status,
    Username assignee,
    Username finalizedBy,
    Instant finalizedDate,
    String reason,
    Long revision) {

  private static final Logger LOGGER = LoggerFactory.getLogger(Approval.class);
  private static final String ERROR_MSG_USERNAME_NULL = "Username cannot be null";
  private static final String ERROR_MISSING_REJECTION_REASON =
      "Cannot reject approval status without reason.";
  private static final String ERROR_MSG_MISSING_ORGANIZATION_ID =
      "Request is missing required organization ID";
  private static final String ERROR_MSG_MISMATCHED_IDS = "Mismatched organization IDs";

  public static Approval fromDao(ApprovalStatusDao approval) {
    return new Approval(
        approval.identifier(),
        approval.approvalStatus().institutionId(),
        ApprovalStatus.parse(approval.approvalStatus().status().getValue()),
        Username.fromUserName(approval.approvalStatus().assignee()),
        Username.fromUserName(approval.approvalStatus().finalizedBy()),
        approval.approvalStatus().finalizedDate(),
        approval.approvalStatus().reason(),
        approval.revision());
  }

  public static Approval createNewApproval(UUID candidateIdentifier, URI institutionId) {
    return new Approval(
        candidateIdentifier, institutionId, ApprovalStatus.PENDING, null, null, null, null, null);
  }

  public Approval resetApproval() {
    return new Approval(
        identifier, institutionId, ApprovalStatus.PENDING, assignee, null, null, null, revision);
  }

  public Approval withAssignee(UpdateAssigneeRequest request) {
    LOGGER.info("Updating assignee for candidateId={}: {}", identifier, request);
    validateUpdateAssigneeRequest(request);

    var newAssignee =
        Optional.ofNullable(request.username())
            .filter(not(StringUtils::isBlank))
            .map(Username::fromString)
            .orElse(null);

    LOGGER.info(
        "Updating assignee for institutionId={}: {} -> {}", institutionId, assignee, newAssignee);
    return new Approval(
        identifier,
        institutionId,
        status,
        newAssignee,
        finalizedBy,
        finalizedDate,
        reason,
        revision);
  }

  public Approval withStatus(UpdateStatusRequest request) {
    LOGGER.info("Updating approval status for candidateId={}: {}", identifier, request);
    validateUpdateStatusRequest(request);

    var username = Username.fromString(request.username());
    var updatedStatus = request.approvalStatus();
    var updatedReason = updatedStatus == ApprovalStatus.REJECTED ? request.reason() : null;

    LOGGER.info("Updating approval status: {} -> {}", status, updatedStatus);
    return new Approval(
        identifier,
        institutionId,
        updatedStatus,
        isAssigned() ? assignee : username,
        updatedStatus.isFinalized() ? username : null,
        updatedStatus.isFinalized() ? Instant.now() : null,
        updatedReason,
        revision);
  }

  public ApprovalStatusDao toDao() {
    return ApprovalStatusDao.builder()
        .identifier(identifier)
        .approvalStatus(createCopyOfCurrentStatus())
        .revision(revision)
        .version(randomUUID().toString())
        .build();
  }

  public String getAssigneeUsername() {
    return isNull(assignee) ? null : assignee.value();
  }

  public String getFinalizedByUserName() {
    return isNull(finalizedBy) ? null : finalizedBy.value();
  }

  public boolean isAssigned() {
    return nonNull(assignee) && nonNull(assignee.value());
  }

  public boolean isPendingAndUnassigned() {
    return status == ApprovalStatus.PENDING && !isAssigned();
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

  private void validateUpdateAssigneeRequest(UpdateAssigneeRequest request) {
    if (isNull(request.institutionId())) {
      throw new IllegalArgumentException(ERROR_MSG_MISSING_ORGANIZATION_ID);
    }
    if (!institutionId.equals(request.institutionId())) {
      throw new IllegalArgumentException(ERROR_MSG_MISMATCHED_IDS);
    }
  }

  private void validateUpdateStatusRequest(UpdateStatusRequest request) {
    status.validateStateTransition(request.approvalStatus());
    if (isNull(request.institutionId())) {
      throw new IllegalArgumentException(ERROR_MSG_MISSING_ORGANIZATION_ID);
    }
    if (!institutionId.equals(request.institutionId())) {
      throw new IllegalArgumentException(ERROR_MSG_MISMATCHED_IDS);
    }
    if (isBlank(request.username())) {
      throw new IllegalArgumentException(ERROR_MSG_USERNAME_NULL);
    }
    if (request.approvalStatus() == ApprovalStatus.REJECTED && isBlank(request.reason())) {
      throw new IllegalArgumentException(ERROR_MISSING_REJECTION_REASON);
    }
  }
}
