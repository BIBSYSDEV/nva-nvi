package no.sikt.nva.nvi.common.service;

import static java.util.Collections.emptyList;
import static no.sikt.nva.nvi.common.db.DynamoRepository.defaultDynamoClient;

import java.util.List;
import java.util.Objects;

import no.sikt.nva.nvi.common.db.CandidateRepository;
import no.sikt.nva.nvi.common.model.UpdateApprovalRequest;
import no.sikt.nva.nvi.common.model.UpdateAssigneeRequest;
import no.sikt.nva.nvi.common.model.UpdateStatusRequest;
import no.sikt.nva.nvi.common.model.UserInstance;
import no.sikt.nva.nvi.common.permissions.CandidatePermissions;
import no.sikt.nva.nvi.common.service.dto.CandidateOperation;
import no.sikt.nva.nvi.common.service.model.Candidate;
import nva.commons.apigateway.exceptions.UnauthorizedException;
import nva.commons.core.JacocoGenerated;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class ApprovalService {
  private static final Logger LOGGER = LoggerFactory.getLogger(ApprovalService.class);
  private final CandidateRepository candidateRepository;

  public ApprovalService(CandidateRepository candidateRepository) {
    this.candidateRepository = candidateRepository;
  }

  @JacocoGenerated
  public static ApprovalService defaultApprovalService() {
    var dynamoClient = defaultDynamoClient();
    return new ApprovalService(new CandidateRepository(dynamoClient));
  }

  public void updateApproval(
      Candidate candidate, UpdateApprovalRequest request, UserInstance user) {
    if (request instanceof UpdateStatusRequest statusRequest) {
      updateApprovalStatus(candidate, statusRequest, user);
    }
    if (request instanceof UpdateAssigneeRequest assigneeRequest) {
      updateApprovalAssignee(candidate, assigneeRequest);
    }
  }

  public void updateApprovalAssignee(Candidate candidate, UpdateAssigneeRequest request) {
    LOGGER.info("Updating assignee for candidateId={}: {}", candidate.identifier(), request);
    var updatedApproval = candidate.getApprovalWithUpdatedAssignee(request);
    var updatedApprovals = List.of(updatedApproval.toDao());
    LOGGER.info("Saving updated approval with assignee {}", updatedApproval.assignee());
    candidateRepository.updateCandidateItems(
        candidate.toDao(), updatedApprovals, emptyList(), emptyList());
  }

  public void updateApprovalStatus(
      Candidate candidate, UpdateStatusRequest request, UserInstance user) {
    LOGGER.info("Updating approval status for candidateId={}: {}", candidate.identifier(), request);
    var currentStatus = candidate.getApprovalStatus(request.institutionId());
    if (request.approvalStatus().equals(currentStatus)) {
      LOGGER.warn("Approval status update attempted with no change in status: {}", request);
      return;
    }

    var permissions = new CandidatePermissions(candidate, user);
    var attemptedOperation = CandidateOperation.fromApprovalStatus(request.approvalStatus());
    try {
      permissions.validateAuthorization(attemptedOperation);
    } catch (UnauthorizedException e) {
      LOGGER.warn("Unauthorized attempt to update approval status", e);
      throw new IllegalStateException("Cannot update approval status");
    }

    var updatedApproval = candidate.getApprovalWithUpdatedStatus(request);
    var updatedApprovals = List.of(updatedApproval.toDao());
    LOGGER.info("Saving updated approval with status {}", updatedApproval.status());
    candidateRepository.updateCandidateItems(
        candidate.toDao(), updatedApprovals, emptyList(), emptyList());
  }
}
