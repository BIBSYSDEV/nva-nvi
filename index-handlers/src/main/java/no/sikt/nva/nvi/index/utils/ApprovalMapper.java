package no.sikt.nva.nvi.index.utils;

import static java.util.function.Predicate.not;

import java.net.URI;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
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
    var institutionId = approval.institutionId();
    var institutionPoints = candidate.getInstitutionPoints(institutionId);
    return ApprovalView.builder()
        .withInstitutionId(institutionId)
        .withLabels(extractLabels(institutionId))
        .withApprovalStatus(extractApprovalStatus(approval))
        .withPoints(institutionPoints.map(InstitutionPointsView::from).orElse(null))
        .withInvolvedOrganizations(extractInvolvedOrganizations(approval, contributors))
        .withAssignee(approval.getAssigneeUsername())
        .withGlobalApprovalStatus(candidate.getGlobalApprovalStatus())
        .withSector(extractSector(institutionPoints))
        .withRboInstitution(extractRboInstitution(institutionPoints))
        .build();
  }

  private Map<String, String> extractLabels(URI institutionId) {
    return candidate.publicationDetails().topLevelOrganizations().stream()
        .filter(org -> org.id().equals(institutionId))
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

  private static Set<URI> extractInvolvedOrganizations(
      Approval approval, List<ContributorType> contributors) {
    return contributors.stream()
        .filter(NviContributor.class::isInstance)
        .map(NviContributor.class::cast)
        .flatMap(
            contributor -> contributor.getOrganizationsPartOf(approval.institutionId()).stream())
        .collect(Collectors.toSet());
  }

  private static String extractSector(Optional<InstitutionPoints> institutionPoints) {
    return institutionPoints
        .map(InstitutionPoints::sector)
        .filter(not(Sector.UNKNOWN::equals))
        .map(Sector::toString)
        .orElse(null);
  }

  private static boolean extractRboInstitution(Optional<InstitutionPoints> institutionPoints) {
    return institutionPoints.map(InstitutionPoints::rboInstitution).orElse(false);
  }
}
