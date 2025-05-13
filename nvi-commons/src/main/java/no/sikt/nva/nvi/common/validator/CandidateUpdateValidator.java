package no.sikt.nva.nvi.common.validator;

import static no.sikt.nva.nvi.common.service.model.ApprovalStatus.APPROVED;
import static no.sikt.nva.nvi.common.service.model.ApprovalStatus.PENDING;
import static no.sikt.nva.nvi.common.service.model.ApprovalStatus.REJECTED;

import java.net.URI;
import java.util.EnumSet;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import no.sikt.nva.nvi.common.client.OrganizationRetriever;
import no.sikt.nva.nvi.common.client.model.Organization;
import no.sikt.nva.nvi.common.model.UpdateStatusRequest;
import no.sikt.nva.nvi.common.service.dto.CandidateOperation;
import no.sikt.nva.nvi.common.service.dto.NviCreatorDto;
import no.sikt.nva.nvi.common.service.dto.UnverifiedNviCreatorDto;
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
  private final OrganizationRetriever organizationRetriever;
  private final URI userTopLevelOrganizationId;

  public CandidateUpdateValidator(
      Candidate candidate,
      OrganizationRetriever organizationRetriever,
      URI userTopLevelOrganizationId) {
    this.candidate = candidate;
    this.organizationRetriever = organizationRetriever;
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
    var unverifiedCreators = candidate.getPublicationDetails().unverifiedCreators();
    if (!unverifiedCreators.isEmpty()) {
      problems.add(new UnverifiedCreatorProblem());
    }
  }

  private void checkForUnverifiedCreatorsFromOrganization() {
    var creators = getUnverifiedCreatorsFromUserOrganization();
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

  private List<String> getUnverifiedCreatorsFromUserOrganization() {
    var unverifiedCreators = candidate.getPublicationDetails().unverifiedCreators();
    return unverifiedCreators.stream()
        .filter(
            contributor ->
                getTopLevelAffiliations(contributor, organizationRetriever)
                    .contains(userTopLevelOrganizationId))
        .map(UnverifiedNviCreatorDto::name)
        .toList();
  }

  /**
   * This finds the top-level affiliations of a creator, which is not currently persisted. Once we
   * persist the necessary metadata, we can simplify this check and avoid the lookup.
   *
   * <p>TODO: Persist full organization hierarchy for creators and simplify this validation.
   */
  private static Set<URI> getTopLevelAffiliations(
      NviCreatorDto contributor, OrganizationRetriever organizationRetriever) {
    return contributor.affiliations().stream()
        .distinct()
        .map(organizationRetriever::fetchOrganization)
        .map(Organization::getTopLevelOrg)
        .map(Organization::id)
        .collect(Collectors.toSet());
  }
}
