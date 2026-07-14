package no.sikt.nva.nvi.index.mapper;

import static java.util.Collections.emptyMap;
import static java.util.function.Predicate.not;

import java.net.URI;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;
import no.sikt.nva.nvi.common.client.model.Organization;
import no.sikt.nva.nvi.common.dto.PublicationDto;
import no.sikt.nva.nvi.common.model.Sector;
import no.sikt.nva.nvi.common.service.model.Approval;
import no.sikt.nva.nvi.common.service.model.Candidate;
import no.sikt.nva.nvi.common.service.model.InstitutionPoints;
import no.sikt.nva.nvi.index.model.document.ApprovalStatus;
import no.sikt.nva.nvi.index.model.document.ApprovalView;
import no.sikt.nva.nvi.index.model.document.InstitutionPointsView;
import no.sikt.nva.nvi.index.model.document.NviContributor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

final class ApprovalMapper {

  private static final Logger LOGGER = LoggerFactory.getLogger(ApprovalMapper.class);
  private final Candidate candidate;
  private final PublicationDto publicationDto;

  ApprovalMapper(Candidate candidate, PublicationDto publicationDto) {
    this.candidate = candidate;
    this.publicationDto = publicationDto;
  }

  List<ApprovalView> mapApprovals(List<NviContributor> nviContributors) {
    return candidate.approvals().values().stream()
        .map(approval -> buildApprovalView(approval, nviContributors))
        .toList();
  }

  private ApprovalView buildApprovalView(Approval approval, List<NviContributor> nviContributors) {
    var institutionId = approval.institutionId();
    var institutionPoints = candidate.getInstitutionPoints(institutionId);
    return ApprovalView.builder()
        .withInstitutionId(institutionId)
        .withLabels(extractLabels(institutionId))
        .withApprovalStatus(extractApprovalStatus(approval))
        .withPoints(institutionPoints.map(InstitutionPointsView::from).orElse(null))
        .withInvolvedOrganizations(extractInvolvedOrganizations(approval, nviContributors))
        .withAssignee(approval.getAssigneeUsername())
        .withGlobalApprovalStatus(candidate.getGlobalApprovalStatus())
        .withSector(extractSector(institutionPoints))
        .withRboInstitution(extractRboInstitution(institutionPoints))
        .build();
  }

  private Map<String, String> extractLabels(URI institutionId) {
    return labelsFromCandidate(institutionId)
        .or(() -> labelsFromPublication(institutionId))
        .orElseGet(
            () -> {
              LOGGER.warn("No labels found for institution {}", institutionId);
              return emptyMap();
            });
  }

  private Optional<Map<String, String>> labelsFromCandidate(URI institutionId) {
    return candidate
        .publicationDetails()
        .findInstitution(institutionId)
        .map(Organization::labels)
        .filter(not(Map::isEmpty));
  }

  private Optional<Map<String, String>> labelsFromPublication(URI institutionId) {
    return Optional.ofNullable(publicationDto)
        .flatMap(publication -> publication.findInstitution(institutionId))
        .map(Organization::labels)
        .filter(not(Map::isEmpty));
  }

  private static ApprovalStatus extractApprovalStatus(Approval approval) {
    return approval.isPendingAndUnassigned()
        ? ApprovalStatus.NEW
        : ApprovalStatus.parse(approval.status().getValue());
  }

  private static Set<URI> extractInvolvedOrganizations(
      Approval approval, List<NviContributor> nviContributors) {
    return nviContributors.stream()
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
