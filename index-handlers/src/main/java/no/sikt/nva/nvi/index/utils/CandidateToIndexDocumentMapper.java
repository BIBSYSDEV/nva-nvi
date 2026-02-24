package no.sikt.nva.nvi.index.utils;

import static java.util.Objects.isNull;
import static java.util.Objects.nonNull;
import static no.sikt.nva.nvi.index.utils.NviCandidateIndexDocumentGenerator.extractSector;

import java.net.URI;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;
import no.sikt.nva.nvi.common.client.model.Organization;
import no.sikt.nva.nvi.common.dto.ContributorDto;
import no.sikt.nva.nvi.common.dto.PublicationChannelDto;
import no.sikt.nva.nvi.common.dto.PublicationDto;
import no.sikt.nva.nvi.common.model.ScientificValue;
import no.sikt.nva.nvi.common.service.dto.NviCreatorDto;
import no.sikt.nva.nvi.common.service.dto.UnverifiedNviCreatorDto;
import no.sikt.nva.nvi.common.service.dto.VerifiedNviCreatorDto;
import no.sikt.nva.nvi.common.service.model.Approval;
import no.sikt.nva.nvi.common.service.model.Candidate;
import no.sikt.nva.nvi.common.service.model.PageCount;
import no.sikt.nva.nvi.index.model.document.ApprovalStatus;
import no.sikt.nva.nvi.index.model.document.ApprovalView;
import no.sikt.nva.nvi.index.model.document.Contributor;
import no.sikt.nva.nvi.index.model.document.ContributorType;
import no.sikt.nva.nvi.index.model.document.InstitutionPointsView;
import no.sikt.nva.nvi.index.model.document.NviCandidateIndexDocument;
import no.sikt.nva.nvi.index.model.document.NviContributor;
import no.sikt.nva.nvi.index.model.document.NviOrganization;
import no.sikt.nva.nvi.index.model.document.OrganizationType;
import no.sikt.nva.nvi.index.model.document.Pages;
import no.sikt.nva.nvi.index.model.document.PublicationChannel;
import no.sikt.nva.nvi.index.model.document.PublicationDetails;
import no.sikt.nva.nvi.index.model.document.ReportingPeriod;

@SuppressWarnings({"PMD.CouplingBetweenObjects", "PMD.GodClass"})
public class CandidateToIndexDocumentMapper {

  private final Candidate candidate;
  private final PublicationDto publicationDto;

  public CandidateToIndexDocumentMapper(Candidate candidate, PublicationDto publicationDto) {
    this.candidate = candidate;
    this.publicationDto = publicationDto;
  }

  public NviCandidateIndexDocument toIndexDocument() {
    var contributors = buildContributors();
    var approvals = buildApprovals(contributors);
    var publicationDetails = buildPublicationDetails(contributors);
    return buildDocument(approvals, publicationDetails);
  }

  // --- Phase A: Build contributors ---

  private List<ContributorType> buildContributors() {
    return publicationDto.contributors().stream().map(this::mapContributor).toList();
  }

  private ContributorType mapContributor(ContributorDto contributorDto) {
    return findMatchingNviCreator(contributorDto)
        .map(nviCreator -> buildNviContributor(contributorDto, nviCreator))
        .orElseGet(() -> buildNonNviContributor(contributorDto));
  }

  private Optional<NviCreatorDto> findMatchingNviCreator(ContributorDto contributorDto) {
    return findMatchingVerifiedCreator(contributorDto)
        .map(NviCreatorDto.class::cast)
        .or(() -> findMatchingUnverifiedCreator(contributorDto).map(NviCreatorDto.class::cast));
  }

  private Optional<VerifiedNviCreatorDto> findMatchingVerifiedCreator(
      ContributorDto contributorDto) {
    if (isNull(contributorDto.id())) {
      return Optional.empty();
    }
    return candidate.publicationDetails().verifiedCreators().stream()
        .filter(creator -> creator.id().equals(contributorDto.id()))
        .findFirst();
  }

  private Optional<UnverifiedNviCreatorDto> findMatchingUnverifiedCreator(
      ContributorDto contributorDto) {
    if (isNull(contributorDto.name())) {
      return Optional.empty();
    }
    return candidate.publicationDetails().unverifiedCreators().stream()
        .filter(creator -> creator.name().equals(contributorDto.name()))
        .findFirst();
  }

  private ContributorType buildNviContributor(
      ContributorDto contributorDto, NviCreatorDto nviCreator) {
    var creatorId =
        nviCreator instanceof VerifiedNviCreatorDto verified ? verified.id().toString() : null;
    return NviContributor.builder()
        .withId(creatorId)
        .withName(nviCreator.name())
        .withOrcid(null)
        .withRole(getContributorRole(contributorDto))
        .withAffiliations(buildNviAffiliations(contributorDto, nviCreator))
        .build();
  }

  // TODO: Remove when non-NVI contributor data is available on Candidate
  private ContributorType buildNonNviContributor(ContributorDto contributorDto) {
    var contributorId = nonNull(contributorDto.id()) ? contributorDto.id().toString() : null;
    return Contributor.builder()
        .withId(contributorId)
        .withName(contributorDto.name())
        .withOrcid(null)
        .withRole(getContributorRole(contributorDto))
        .withAffiliations(buildSimpleAffiliations(contributorDto))
        .build();
  }

  // TODO: Remove when role is available on Candidate
  private static String getContributorRole(ContributorDto contributorDto) {
    return nonNull(contributorDto.role()) ? contributorDto.role().getValue() : null;
  }

  private List<OrganizationType> buildNviAffiliations(
      ContributorDto contributorDto, NviCreatorDto nviCreator) {
    var nviAffiliationUris = nviCreator.affiliations();
    return contributorDto.affiliations().stream()
        .map(affiliation -> buildAffiliation(affiliation.id(), nviAffiliationUris))
        .toList();
  }

  private OrganizationType buildAffiliation(URI affiliationUri, List<URI> nviAffiliationUris) {
    if (nviAffiliationUris.contains(affiliationUri)) {
      var partOfChain = findPartOfChain(affiliationUri);
      return NviOrganization.builder().withId(affiliationUri).withPartOf(partOfChain).build();
    }
    return no.sikt.nva.nvi.index.model.document.Organization.builder()
        .withId(affiliationUri)
        .build();
  }

  private static List<OrganizationType> buildSimpleAffiliations(ContributorDto contributorDto) {
    return contributorDto.affiliations().stream()
        .map(
            affiliation ->
                (OrganizationType)
                    no.sikt.nva.nvi.index.model.document.Organization.builder()
                        .withId(affiliation.id())
                        .build())
        .toList();
  }

  // --- Phase A.1: Organization hierarchy (partOf chain) ---

  private List<URI> findPartOfChain(URI affiliationUri) {
    for (var topLevelOrg : candidate.publicationDetails().topLevelOrganizations()) {
      if (topLevelOrg.isTopLevelOrganizationOf(affiliationUri)) {
        if (topLevelOrg.id().equals(affiliationUri)) {
          return List.of();
        }
        var ancestors = findAncestorsInTree(topLevelOrg, affiliationUri, new ArrayList<>());
        if (ancestors.isPresent()) {
          return ancestors.get();
        }
      }
    }
    return List.of();
  }

  private static Optional<List<URI>> findAncestorsInTree(
      Organization org, URI targetUri, List<URI> pathFromTop) {
    if (isNull(org.hasPart())) {
      return Optional.empty();
    }
    for (var child : org.hasPart()) {
      if (child.id().equals(targetUri)) {
        var ancestors = new ArrayList<>(pathFromTop);
        ancestors.add(org.id());
        Collections.reverse(ancestors);
        return Optional.of(ancestors);
      }
      var extendedPath = new ArrayList<>(pathFromTop);
      extendedPath.add(org.id());
      var result = findAncestorsInTree(child, targetUri, extendedPath);
      if (result.isPresent()) {
        return result;
      }
    }
    return Optional.empty();
  }

  // --- Phase B: Build approvals ---

  private List<ApprovalView> buildApprovals(List<ContributorType> contributors) {
    return candidate.approvals().values().stream()
        .map(approval -> buildApprovalView(approval, contributors))
        .toList();
  }

  private ApprovalView buildApprovalView(Approval approval, List<ContributorType> contributors) {
    return ApprovalView.builder()
        .withInstitutionId(approval.institutionId())
        .withLabels(extractLabels(approval))
        .withApprovalStatus(getApprovalStatus(approval))
        .withPoints(getInstitutionPoints(approval))
        .withInvolvedOrganizations(extractInvolvedOrganizations(approval, contributors))
        .withAssignee(approval.getAssigneeUsername())
        .withGlobalApprovalStatus(candidate.getGlobalApprovalStatus())
        .withSector(extractSector(approval.institutionId(), candidate))
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
      Approval approval, List<ContributorType> contributors) {
    return contributors.stream()
        .filter(NviContributor.class::isInstance)
        .map(NviContributor.class::cast)
        .flatMap(
            contributor -> contributor.getOrganizationsPartOf(approval.institutionId()).stream())
        .collect(Collectors.toSet());
  }

  // --- Phase C: Assemble document ---

  private NviCandidateIndexDocument buildDocument(
      List<ApprovalView> approvals, PublicationDetails publicationDetails) {
    return NviCandidateIndexDocument.builder()
        .withId(candidate.getId())
        .withContext(candidate.getContextUri())
        .withIsApplicable(candidate.isApplicable())
        .withIdentifier(candidate.identifier())
        .withReportingPeriod(ReportingPeriod.fromCandidate(candidate))
        .withReported(candidate.isReported())
        .withApprovals(approvals)
        .withPublicationDetails(publicationDetails)
        .withNumberOfApprovals(approvals.size())
        .withPoints(candidate.getTotalPoints())
        .withPublicationTypeChannelLevelPoints(candidate.getBasePoints())
        .withGlobalApprovalStatus(candidate.getGlobalApprovalStatus())
        .withCreatorShareCount(candidate.getCreatorShareCount())
        .withInternationalCollaborationFactor(candidate.getCollaborationFactor())
        .withCreatedDate(candidate.createdDate())
        .withModifiedDate(candidate.modifiedDate())
        .build();
  }

  private PublicationDetails buildPublicationDetails(List<ContributorType> contributors) {
    var candidateDetails = candidate.publicationDetails();
    var publicationDate = candidateDetails.publicationDate().toDtoPublicationDate();
    var pages = buildPages(candidateDetails.pageCount());

    return PublicationDetails.builder()
        .withId(candidateDetails.publicationId().toString())
        .withType(candidate.getPublicationType().getValue())
        .withTitle(candidateDetails.title())
        .withAbstract(candidateDetails.abstractText())
        .withPublicationDate(publicationDate)
        .withContributors(contributors)
        .withContributorsCount(candidateDetails.creatorCount())
        .withPublicationChannel(buildPublicationChannel())
        .withPages(pages)
        .withLanguage(candidateDetails.language())
        .build();
  }

  private static Pages buildPages(PageCount pageCount) {
    if (isNull(pageCount)) {
      return null;
    }
    return Pages.builder()
        .withBegin(pageCount.first())
        .withEnd(pageCount.last())
        .withNumberOfPages(pageCount.total())
        .build();
  }

  private PublicationChannel buildPublicationChannel() {
    var channel = candidate.getPublicationChannel();
    var builder =
        PublicationChannel.builder()
            .withScientificValue(ScientificValue.parse(channel.scientificValue().getValue()));

    if (nonNull(channel.id())) {
      builder.withId(channel.id());
    }
    if (nonNull(channel.channelType())) {
      builder.withType(channel.channelType().getValue());
    }

    findMatchingPublicationChannelDto()
        .ifPresent(
            dto -> {
              builder.withName(getChannelName(dto));
              builder.withPrintIssn(getChannelPrintIssn(dto));
            });

    return builder.build();
  }

  // TODO: Remove when channel name is available on Candidate
  private static String getChannelName(PublicationChannelDto channelDto) {
    return channelDto.name();
  }

  // TODO: Remove when channel printIssn is available on Candidate
  private static String getChannelPrintIssn(PublicationChannelDto channelDto) {
    return channelDto.printIssn();
  }

  private Optional<PublicationChannelDto> findMatchingPublicationChannelDto() {
    var candidateChannel = candidate.getPublicationChannel();
    if (nonNull(candidateChannel.id())) {
      return publicationDto.publicationChannels().stream()
          .filter(dto -> candidateChannel.id().equals(dto.id()))
          .findFirst();
    }
    if (nonNull(candidateChannel.channelType())) {
      return publicationDto.publicationChannels().stream()
          .filter(dto -> candidateChannel.channelType() == dto.channelType())
          .findFirst();
    }
    return Optional.empty();
  }
}
