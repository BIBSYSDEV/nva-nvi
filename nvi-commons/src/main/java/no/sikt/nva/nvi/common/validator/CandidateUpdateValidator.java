package no.sikt.nva.nvi.common.validator;

import static java.util.function.Predicate.not;
import static no.sikt.nva.nvi.common.service.model.ApprovalStatus.APPROVED;
import static no.sikt.nva.nvi.common.service.model.ApprovalStatus.PENDING;
import static no.sikt.nva.nvi.common.service.model.ApprovalStatus.REJECTED;

import java.net.URI;
import java.util.EnumSet;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import no.sikt.nva.nvi.common.model.NviCreator;
import no.sikt.nva.nvi.common.model.UpdateStatusRequest;
import no.sikt.nva.nvi.common.service.dto.CandidateOperation;
import no.sikt.nva.nvi.common.service.dto.problem.CandidateProblem;
import no.sikt.nva.nvi.common.service.dto.problem.UnverifiedCreatorFromOrganizationProblem;
import no.sikt.nva.nvi.common.service.dto.problem.UnverifiedCreatorProblem;
import no.sikt.nva.nvi.common.service.model.Candidate;

/**
 * This validates a candidate in the context of an organization, based on the top-level organization
 * ID from a user request. This is intended to check which operations a given user can perform on
 * the candidate.
 */
public class CandidateUpdateValidator {
  private final Candidate candidate;
  private final Set<CandidateProblem> problems = new HashSet<>();
  private Set<CandidateOperation> allowedOperations;
  private final URI userTopLevelOrganizationId;

  public CandidateUpdateValidator(Candidate candidate, URI userTopLevelOrganizationId) {
    this.candidate = candidate;
    this.userTopLevelOrganizationId = userTopLevelOrganizationId;
    validate();
  }

  public Set<CandidateProblem> getProblems() {
    return problems;
  }

  public Set<CandidateOperation> getAllowedOperations() {
    return allowedOperations;
  }

  public boolean isValidStatusChange(UpdateStatusRequest updateRequest) {
    var attemptedOperation = CandidateOperation.fromApprovalStatus(updateRequest.approvalStatus());
    return allowedOperations.contains(attemptedOperation);
  }

  private void validate() {
    checkForUnverifiedCreators();
    checkForUnverifiedCreatorsFromOrganization();
    checkAllowedOperations();
  }

  private void checkForUnverifiedCreators() {
    if (hasUnverifiedCreators(candidate)) {
      problems.add(new UnverifiedCreatorProblem());
    }
  }

  private void checkForUnverifiedCreatorsFromOrganization() {
    var creators = getUnverifiedCreatorNames(candidate, userTopLevelOrganizationId);
    if (!creators.isEmpty()) {
      problems.add(new UnverifiedCreatorFromOrganizationProblem(creators));
    }
  }

  private void checkAllowedOperations() {
    var hasUnverifiedCreator =
        problems.stream().anyMatch(UnverifiedCreatorFromOrganizationProblem.class::isInstance);

    var currentStatus = candidate.getApprovalStatus(userTopLevelOrganizationId);
    var validTransitions = currentStatus.getValidTransitions();
    var validStatesForOrganization =
        hasUnverifiedCreator ? EnumSet.of(PENDING) : EnumSet.of(PENDING, APPROVED, REJECTED);

    // Find the intersection of valid approvals for this organization and valid transitions
    validStatesForOrganization.retainAll(validTransitions);
    allowedOperations =
        validStatesForOrganization.stream()
            .map(CandidateOperation::fromApprovalStatus)
            .collect(Collectors.toUnmodifiableSet());
  }

  private static boolean hasUnverifiedCreators(Candidate candidate) {
    return candidate.getPublicationDetails().nviCreators().stream()
        .anyMatch(not(NviCreator::isVerified));
  }

  private static List<String> getUnverifiedCreatorNames(Candidate candidate, URI organizationId) {
    return candidate.getPublicationDetails().nviCreators().stream()
        .filter(not(NviCreator::isVerified))
        .filter(creator -> creator.isAffiliatedWithTopLevelOrganization(organizationId))
        .map(NviCreator::name)
        .toList();
  }
}
