package no.sikt.nva.nvi.common.validator;

import static java.util.stream.Collectors.toSet;
import static no.sikt.nva.nvi.common.service.model.ApprovalStatus.APPROVED;
import static no.sikt.nva.nvi.common.service.model.ApprovalStatus.PENDING;
import static no.sikt.nva.nvi.common.service.model.ApprovalStatus.REJECTED;
import static no.sikt.nva.nvi.common.utils.RequestUtil.isNviCurator;

import java.net.URI;
import java.util.EnumSet;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import no.sikt.nva.nvi.common.client.OrganizationRetriever;
import no.sikt.nva.nvi.common.client.model.Organization;
import no.sikt.nva.nvi.common.model.UpdateStatusRequest;
import no.sikt.nva.nvi.common.service.dto.CandidateDto;
import no.sikt.nva.nvi.common.service.dto.CandidateOperation;
import no.sikt.nva.nvi.common.service.dto.NviCreatorDto;
import no.sikt.nva.nvi.common.service.dto.UnverifiedNviCreatorDto;
import no.sikt.nva.nvi.common.service.dto.problem.CandidateProblem;
import no.sikt.nva.nvi.common.service.dto.problem.UnverifiedCreatorFromOrganizationProblem;
import no.sikt.nva.nvi.common.service.dto.problem.UnverifiedCreatorProblem;
import no.sikt.nva.nvi.common.service.model.Candidate;
import nva.commons.apigateway.RequestInfo;
import nva.commons.apigateway.exceptions.UnauthorizedException;

/**
 * This validates a candidate in the context of an organization, based on the top-level organization
 * ID from a user request. This is intended to check which operations a given user can perform on
 * the candidate.
 */
public class CandidateUpdateValidator {
  private final Candidate candidate;
  private final Set<CandidateProblem> problems = new HashSet<>();
  private Set<CandidateOperation> allowedApprovalOperations;
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

  public CandidateDto getCandidateDto(
      RequestInfo requestInfo, ViewingScopeValidator viewingScopeValidator)
      throws UnauthorizedException {
    var allowedNoteOperations = getAllowedNoteOperations(requestInfo, viewingScopeValidator);
    var allAllowedOperations =
        Stream.of(allowedApprovalOperations, allowedNoteOperations)
            .flatMap(Set::stream)
            .collect(toSet());
    return candidate
        .toDto()
        .copy()
        .withAllowedOperations(allAllowedOperations)
        .withProblems(problems)
        .build();
  }



  public boolean isValidStatusChange(UpdateStatusRequest updateRequest) {
    var attemptedOperation = CandidateOperation.fromApprovalStatus(updateRequest.approvalStatus());
    return allowedApprovalOperations.contains(attemptedOperation);
  }

  private void validate() {
    checkForUnverifiedCreators();
    checkForUnverifiedCreatorsFromOrganization();
    checkAllowedApprovalOperations();
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

  private void checkAllowedApprovalOperations() {
    var hasUnverifiedCreator =
        problems.stream().anyMatch(UnverifiedCreatorFromOrganizationProblem.class::isInstance);

    var currentStatus = candidate.getApprovalStatus(userTopLevelOrganizationId);
    var validTransitions = currentStatus.getValidTransitions();
    var validStatesForOrganization =
        hasUnverifiedCreator ? EnumSet.of(PENDING) : EnumSet.of(PENDING, APPROVED, REJECTED);

    // Find the intersection of valid approvals for this organization and valid transitions
    validStatesForOrganization.retainAll(validTransitions);
    allowedApprovalOperations =
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
        .collect(toSet());
  }

  private Set<CandidateOperation> getAllowedNoteOperations(
      RequestInfo requestInfo, ViewingScopeValidator viewingScopeValidator)
      throws UnauthorizedException {
    var isAllowedToAccessCandidate =
        viewingScopeValidator.userIsAllowedToAccessOneOf(
            requestInfo.getUserName(), candidate.getNviCreatorAffiliations());
    if (isAllowedToAccessCandidate && isNviCurator(requestInfo)) {
      return EnumSet.of(CandidateOperation.NOTE_CREATE);
    }
    return EnumSet.noneOf(CandidateOperation.class);
  }
}
