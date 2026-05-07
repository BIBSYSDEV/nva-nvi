package no.sikt.nva.nvi.index.utils;

import static java.util.function.Predicate.not;

import java.net.URI;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;
import no.sikt.nva.nvi.common.client.model.Organization;
import no.sikt.nva.nvi.common.model.Sector;
import no.sikt.nva.nvi.common.service.model.Approval;
import no.sikt.nva.nvi.common.service.model.Candidate;
import no.sikt.nva.nvi.common.service.model.InstitutionPoints;
import no.sikt.nva.nvi.index.model.document.ApprovalStatus;
import no.sikt.nva.nvi.index.model.document.ApprovalView;
import no.sikt.nva.nvi.index.model.document.ContributorType;
import no.sikt.nva.nvi.index.model.document.InstitutionPointsView;
import no.sikt.nva.nvi.index.model.document.NviContributor;

final class ApprovalMapper {

  private final Candidate candidate;

  ApprovalMapper(Candidate candidate) {
    this.candidate = candidate;
  }

  List<ApprovalView> mapApprovals(List<ContributorType> contributors) {
    return candidate.approvals().values().stream()
        .map(approval -> buildApprovalView(approval, contributors))
        .toList();
  }

  private ApprovalView buildApprovalView(Approval approval, List<ContributorType> contributors) {
    return ApprovalView.builder()
        .withInstitutionId(approval.institutionId())
        .withLabels(extractLabels(approval))
        .withApprovalStatus(extractApprovalStatus(approval))
        .withPoints(extractInstitutionPoints(approval))
        .withInvolvedOrganizations(extractInvolvedOrganizations(approval, contributors))
        .withAssignee(approval.getAssigneeUsername())
        .withGlobalApprovalStatus(candidate.getGlobalApprovalStatus())
        .withSector(extractSector(approval.institutionId()))
        .withRboInstitution(extractRboInstitution(approval.institutionId()))
        .build();
  }

  private Map<String, String> extractLabels(Approval approval) {
    return candidate.publicationDetails().topLevelOrganizations().stream()
        .filter(org -> org.id().equals(approval.institutionId()))
        .findFirst()
        .map(Organization::labels)
        .filter(Objects::nonNull)
        .orElse(Collections.emptyMap());
  }

  private static ApprovalStatus extractApprovalStatus(Approval approval) {
    return approval.isPendingAndUnassigned()
        ? ApprovalStatus.NEW
        : ApprovalStatus.parse(approval.status().getValue());
  }

  private InstitutionPointsView extractInstitutionPoints(Approval approval) {
    return candidate
        .getInstitutionPoints(approval.institutionId())
        .map(InstitutionPointsView::from)
        .orElse(null);
  }

  private static Set<URI> extractInvolvedOrganizations(
      Approval approval, List<ContributorType> contributors) {
    return contributors.stream()
        .filter(NviContributor.class::isInstance)
        .map(NviContributor.class::cast)
        .flatMap(
            contributor -> contributor.getOrganizationsPartOf(approval.institutionId()).stream())
        .collect(Collectors.toSet());
  }

  private String extractSector(URI institutionId) {
    return candidate
        .getInstitutionPoints(institutionId)
        .map(InstitutionPoints::sector)
        .filter(not(Sector.UNKNOWN::equals))
        .map(Sector::toString)
        .orElse(null);
  }

  private boolean extractRboInstitution(URI institutionId) {
    return candidate
        .getInstitutionPoints(institutionId)
        .map(InstitutionPoints::rboInstitution)
        .orElse(false);
  }
}
