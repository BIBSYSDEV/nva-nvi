package no.sikt.nva.nvi.common.service;

import static java.util.Collections.emptyList;

import java.util.List;
import no.sikt.nva.nvi.common.db.CandidateRepository;
import no.sikt.nva.nvi.common.model.UpdateAssigneeRequest;
import no.sikt.nva.nvi.common.model.UpdateStatusRequest;
import no.sikt.nva.nvi.common.model.UserInstance;
import no.sikt.nva.nvi.common.permissions.CandidatePermissions;
import no.sikt.nva.nvi.common.service.dto.CandidateOperation;
import no.sikt.nva.nvi.common.service.model.Candidate;
import nva.commons.apigateway.exceptions.UnauthorizedException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public record ApprovalService(CandidateRepository candidateRepository) {
  private static final Logger LOGGER = LoggerFactory.getLogger(ApprovalService.class);

  public void updateApprovalAssignee(Candidate candidate, UpdateAssigneeRequest request) {
    LOGGER.info("Updating assignee for candidateId={}: {}", candidate.identifier(), request);
    var updatedApproval = candidate.updateApprovalAssignee(request);
    candidateRepository.updateCandidateItems(
        candidate.toDao(), List.of(updatedApproval.toDao()), emptyList());
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
      throw new IllegalStateException("Cannot update approval status");
    }

    var updatedApproval = candidate.updateApprovalStatus(request);
    LOGGER.info("Saving updated approval with status {}", updatedApproval.status());
    candidateRepository.updateCandidateItems(
        candidate.toDao(), List.of(updatedApproval.toDao()), emptyList());
  }
}
