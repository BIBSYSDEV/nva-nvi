package no.sikt.nva.nvi.index.utils;

import static java.util.function.Predicate.not;

import java.net.URI;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;
import no.sikt.nva.nvi.common.model.Sector;
import no.sikt.nva.nvi.common.service.model.Approval;
import no.sikt.nva.nvi.common.service.model.Candidate;
import no.sikt.nva.nvi.common.service.model.InstitutionPoints;
import no.sikt.nva.nvi.index.model.document.ApprovalStatus;
import no.sikt.nva.nvi.index.model.document.ApprovalView;
import no.sikt.nva.nvi.index.model.document.ContributorType;
import no.sikt.nva.nvi.index.model.document.InstitutionPointsView;
import no.sikt.nva.nvi.index.model.document.NviContributor;

final class ApprovalExpander {

  private final Candidate candidate;

  ApprovalExpander(Candidate candidate) {
    this.candidate = candidate;
  }

  List<ApprovalView> createApprovals(List<ContributorType> expandedContributors) {
    return candidate.approvals().values().stream()
        .map(approval -> toApproval(approval, expandedContributors))
        .toList();
  }

  private ApprovalView toApproval(Approval approval, List<ContributorType> expandedContributors) {
    return ApprovalView.builder()
        .withInstitutionId(approval.institutionId())
        .withLabels(extractLabels(approval))
        .withApprovalStatus(getApprovalStatus(approval))
        .withPoints(getInstitutionPoints(approval))
        .withInvolvedOrganizations(extractInvolvedOrganizations(approval, expandedContributors))
        .withAssignee(extractAssignee(approval))
        .withGlobalApprovalStatus(candidate.getGlobalApprovalStatus())
        .withSector(extractSector(approval.institutionId()))
        .build();
  }

  private Map<String, String> extractLabels(Approval approval) {
    return candidate.publicationDetails().topLevelOrganizations().stream()
        .filter(org -> org.id().equals(approval.institutionId()))
        .findFirst()
        .map(no.sikt.nva.nvi.common.client.model.Organization::labels)
        .orElse(Collections.emptyMap());
  }

  private static ApprovalStatus getApprovalStatus(Approval approval) {
    return approval.isPendingAndUnassigned()
        ? ApprovalStatus.NEW
        : ApprovalStatus.parse(approval.status().getValue());
  }

  private InstitutionPointsView getInstitutionPoints(Approval approval) {
    return candidate
        .getInstitutionPoints(approval.institutionId())
        .map(InstitutionPointsView::from)
        .orElse(null);
  }

  private static Set<URI> extractInvolvedOrganizations(
      Approval approval, List<ContributorType> expandedContributors) {
    return expandedContributors.stream()
        .filter(NviContributor.class::isInstance)
        .map(NviContributor.class::cast)
        .flatMap(
            contributor -> contributor.getOrganizationsPartOf(approval.institutionId()).stream())
        .collect(Collectors.toSet());
  }

  private static String extractAssignee(Approval approval) {
    return Optional.of(approval).map(Approval::getAssigneeUsername).orElse(null);
  }

  private String extractSector(URI institutionId) {
    return candidate
        .getInstitutionPoints(institutionId)
        .map(InstitutionPoints::sector)
        .filter(not(Sector.UNKNOWN::equals))
        .map(Sector::toString)
        .orElse(null);
  }
}
