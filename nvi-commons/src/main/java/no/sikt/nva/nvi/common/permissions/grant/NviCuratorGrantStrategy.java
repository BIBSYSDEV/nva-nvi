package no.sikt.nva.nvi.common.permissions.grant;

import static no.sikt.nva.nvi.common.service.model.ApprovalStatus.APPROVED;
import static no.sikt.nva.nvi.common.service.model.ApprovalStatus.PENDING;
import static no.sikt.nva.nvi.common.service.model.ApprovalStatus.REJECTED;

import java.util.EnumSet;
import java.util.Set;
import no.sikt.nva.nvi.common.model.UserInstance;
import no.sikt.nva.nvi.common.permissions.BaseStrategy;
import no.sikt.nva.nvi.common.service.dto.CandidateOperation;
import no.sikt.nva.nvi.common.service.model.ApprovalStatus;
import no.sikt.nva.nvi.common.service.model.Candidate;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class NviCuratorGrantStrategy extends BaseStrategy implements GrantStrategy {
  private static final Logger LOGGER = LoggerFactory.getLogger(NviCuratorGrantStrategy.class);

  public NviCuratorGrantStrategy(Candidate candidate, UserInstance userInstance) {
    super(candidate, userInstance);
  }

  @Override
  public boolean allowsAction(CandidateOperation operation) {
    if (isNotCuratorForCandidate()) {
      LOGGER.warn("Access denied: User is not a curator for candidate {}", candidate.getId());
      return false;
    }

    return switch (operation) {
      case APPROVAL_APPROVE -> canSetApprovalStatus(APPROVED);
      case APPROVAL_REJECT -> canSetApprovalStatus(REJECTED);
      case APPROVAL_PENDING -> canSetApprovalStatus(PENDING);
      case NOTE_CREATE -> true;
    };
  }

  private boolean isNotCuratorForCandidate() {
    return !(userInstance.isNviCurator() && hasCreatorFromUserOrganization());
  }

  private Set<ApprovalStatus> getValidApprovalStates() {
    return hasUnverifiedCreatorFromUserOrganization()
        ? EnumSet.of(PENDING)
        : EnumSet.of(PENDING, APPROVED, REJECTED);
  }

  private boolean canSetApprovalStatus(ApprovalStatus newStatus) {
    var currentStatus = candidate.getApprovalStatus(userInstance.topLevelOrganizationId());
    var validTransitions = currentStatus.getValidTransitions();
    var validStates = getValidApprovalStates();

    var isAllowedTransition =
        validStates.contains(newStatus) && validTransitions.contains(newStatus);
    return canUpdateApprovals() && isAllowedTransition;
  }

  private boolean canUpdateApprovals() {
    return candidate.isApplicable() && !candidate.isReported();
  }
}
