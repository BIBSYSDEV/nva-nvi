package no.sikt.nva.nvi.index.utils;

import static java.util.Objects.isNull;
import static java.util.Objects.nonNull;
import static java.util.function.Predicate.not;
import static no.sikt.nva.nvi.common.utils.GraphUtils.PART_OF_PROPERTY;
import static no.sikt.nva.nvi.common.utils.GraphUtils.createModel;
import static no.sikt.nva.nvi.common.utils.JsonPointers.JSON_POINTER_JOURNAL_PISSN;
import static no.sikt.nva.nvi.common.utils.JsonPointers.JSON_PRT_PAGES_END;
import static no.sikt.nva.nvi.common.utils.JsonPointers.JSON_PTR_ABSTRACT;
import static no.sikt.nva.nvi.common.utils.JsonPointers.JSON_PTR_AFFILIATIONS;
import static no.sikt.nva.nvi.common.utils.JsonPointers.JSON_PTR_CONTRIBUTOR;
import static no.sikt.nva.nvi.common.utils.JsonPointers.JSON_PTR_DAY;
import static no.sikt.nva.nvi.common.utils.JsonPointers.JSON_PTR_ID;
import static no.sikt.nva.nvi.common.utils.JsonPointers.JSON_PTR_IDENTITY;
import static no.sikt.nva.nvi.common.utils.JsonPointers.JSON_PTR_INSTANCE_TYPE;
import static no.sikt.nva.nvi.common.utils.JsonPointers.JSON_PTR_JOURNAL_NAME;
import static no.sikt.nva.nvi.common.utils.JsonPointers.JSON_PTR_LABELS;
import static no.sikt.nva.nvi.common.utils.JsonPointers.JSON_PTR_LANGUAGE;
import static no.sikt.nva.nvi.common.utils.JsonPointers.JSON_PTR_MAIN_TITLE;
import static no.sikt.nva.nvi.common.utils.JsonPointers.JSON_PTR_MONTH;
import static no.sikt.nva.nvi.common.utils.JsonPointers.JSON_PTR_NAME;
import static no.sikt.nva.nvi.common.utils.JsonPointers.JSON_PTR_ORCID;
import static no.sikt.nva.nvi.common.utils.JsonPointers.JSON_PTR_PAGES_BEGIN;
import static no.sikt.nva.nvi.common.utils.JsonPointers.JSON_PTR_PAGES_NUMBER;
import static no.sikt.nva.nvi.common.utils.JsonPointers.JSON_PTR_PUBLICATION_DATE;
import static no.sikt.nva.nvi.common.utils.JsonPointers.JSON_PTR_PUBLISHER_NAME;
import static no.sikt.nva.nvi.common.utils.JsonPointers.JSON_PTR_ROLE_TYPE;
import static no.sikt.nva.nvi.common.utils.JsonPointers.JSON_PTR_SERIES_NAME;
import static no.sikt.nva.nvi.common.utils.JsonPointers.JSON_PTR_SERIES_PISSN;
import static no.sikt.nva.nvi.common.utils.JsonPointers.JSON_PTR_TOP_LEVEL_ORGANIZATIONS;
import static no.sikt.nva.nvi.common.utils.JsonPointers.JSON_PTR_YEAR;
import static no.sikt.nva.nvi.common.utils.JsonUtils.extractJsonNodeTextValue;
import static no.sikt.nva.nvi.common.utils.JsonUtils.extractOptJsonNodeTextValue;
import static no.sikt.nva.nvi.common.utils.JsonUtils.streamNode;
import static no.unit.nva.commons.json.JsonUtils.dtoObjectMapper;
import static nva.commons.core.attempt.Try.attempt;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import java.net.URI;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import java.util.stream.StreamSupport;
import no.sikt.nva.nvi.common.client.OrganizationRetriever;
import no.sikt.nva.nvi.common.dto.PublicationDateDto;
import no.sikt.nva.nvi.common.model.ChannelType;
import no.sikt.nva.nvi.common.model.ScientificValue;
import no.sikt.nva.nvi.common.model.Sector;
import no.sikt.nva.nvi.common.service.dto.NviCreatorDto;
import no.sikt.nva.nvi.common.service.dto.UnverifiedNviCreatorDto;
import no.sikt.nva.nvi.common.service.dto.VerifiedNviCreatorDto;
import no.sikt.nva.nvi.common.service.model.Approval;
import no.sikt.nva.nvi.common.service.model.Candidate;
import no.sikt.nva.nvi.common.service.model.InstitutionPoints;
import no.sikt.nva.nvi.index.model.document.ApprovalStatus;
import no.sikt.nva.nvi.index.model.document.ApprovalView;
import no.sikt.nva.nvi.index.model.document.Contributor;
import no.sikt.nva.nvi.index.model.document.ContributorType;
import no.sikt.nva.nvi.index.model.document.InstitutionPointsView;
import no.sikt.nva.nvi.index.model.document.NviCandidateIndexDocument;
import no.sikt.nva.nvi.index.model.document.NviContributor;
import no.sikt.nva.nvi.index.model.document.NviOrganization;
import no.sikt.nva.nvi.index.model.document.Organization;
import no.sikt.nva.nvi.index.model.document.OrganizationType;
import no.sikt.nva.nvi.index.model.document.Pages;
import no.sikt.nva.nvi.index.model.document.PublicationChannel;
import no.sikt.nva.nvi.index.model.document.PublicationDetails;
import no.sikt.nva.nvi.index.model.document.ReportingPeriod;
import no.unit.nva.auth.uriretriever.UriRetriever;
import org.apache.jena.rdf.model.Model;
import org.apache.jena.rdf.model.NodeIterator;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

// Should be refactored, technical debt task: https://sikt.atlassian.net/browse/NP-48093
@SuppressWarnings({"PMD.GodClass", "PMD.CouplingBetweenObjects"})
public final class NviCandidateIndexDocumentGenerator {

  private static final Logger LOGGER =
      LoggerFactory.getLogger(NviCandidateIndexDocumentGenerator.class);
  private static final TypeReference<Map<String, String>> TYPE_REF = new TypeReference<>() {};
  private final OrganizationRetriever organizationRetriever;
  private final JsonNode expandedResource;
  private final Candidate candidate;
  private final Map<URI, String> temporaryCache = new HashMap<>();

  public NviCandidateIndexDocumentGenerator(
      UriRetriever uriRetriever, JsonNode expandedResource, Candidate candidate) {
    this.organizationRetriever = new OrganizationRetriever(uriRetriever);
    this.expandedResource = expandedResource;
    this.candidate = candidate;
  }

  public NviCandidateIndexDocument generateDocument() {
    var expandedContributors = expandContributors();
    var approvals = createApprovals(expandedContributors);
    var expandedPublicationDetails = expandPublicationDetails(expandedContributors);
    return buildDocument(approvals, expandedPublicationDetails);
  }

  private static NviOrganization buildNviOrganization(URI id, List<URI> partOf) {
    return NviOrganization.builder().withId(id).withPartOf(partOf).build();
  }

  private static ApprovalStatus getApprovalStatus(Approval approval) {
    return approval.isPendingAndUnassigned()
        ? ApprovalStatus.NEW
        : ApprovalStatus.parse(approval.status().getValue());
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

  private static boolean isOrgWithInstitutionId(JsonNode organization, URI institutionId) {
    return organization.at(JSON_PTR_ID).asText().equals(institutionId.toString());
  }

  private static Optional<Map<String, String>> readAsStringMap(JsonNode node) {
    return attempt(() -> dtoObjectMapper.readValue(node.toString(), TYPE_REF)).toOptional();
  }

  private static NodeIterator listNextPartOf(Model model, String resourceId) {
    return model.listObjectsOfProperty(
        model.createResource(resourceId), model.createProperty(PART_OF_PROPERTY));
  }

  private static Optional<Map<String, String>> extractLabels(
      Approval approval, JsonNode topLevelOrganizations) {
    return streamNode(topLevelOrganizations)
        .filter(organization -> isOrgWithInstitutionId(organization, approval.institutionId()))
        .findFirst()
        .map(org -> org.at(JSON_PTR_LABELS))
        .flatMap(NviCandidateIndexDocumentGenerator::readAsStringMap);
  }

  private Optional<Map<String, String>> extractLabels(Approval approval) {
    return extractLabelsFromExpandedResource(expandedResource, approval);
  }

  private NviCandidateIndexDocument buildDocument(
      List<ApprovalView> approvals, PublicationDetails expandedPublicationDetails) {
    return NviCandidateIndexDocument.builder()
        .withId(candidate.getId())
        .withContext(candidate.getContextUri())
        .withIsApplicable(candidate.isApplicable())
        .withIdentifier(candidate.identifier())
        .withReportingPeriod(ReportingPeriod.fromCandidate(candidate))
        .withReported(candidate.isReported())
        .withApprovals(approvals)
        .withPublicationDetails(expandedPublicationDetails)
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

  private InstitutionPointsView getInstitutionPoints(Approval approval) {
    return candidate
        .getInstitutionPoints(approval.institutionId())
        .map(InstitutionPointsView::from)
        .orElse(null);
  }

  private PublicationDetails expandPublicationDetails(List<ContributorType> contributors) {
    return PublicationDetails.builder()
        .withId(candidate.publicationDetails().publicationId().toString())
        .withContributors(contributors)
        .withType(extractInstanceType())
        .withPublicationDate(extractPublicationDate())
        .withTitle(extractMainTitle())
        .withAbstract(extractAbstract())
        .withPublicationChannel(buildPublicationChannel())
        .withPages(extractPages())
        .withLanguage(extractLanguage())
        .build();
  }

  private String extractAbstract() {
    return extractJsonNodeTextValue(expandedResource, JSON_PTR_ABSTRACT);
  }

  private String extractLanguage() {
    return extractJsonNodeTextValue(expandedResource, JSON_PTR_LANGUAGE);
  }

  private Pages extractPages() {
    var pagesBuilder = Pages.builder();
    extractPagesBeginIfPresent(pagesBuilder);
    extractPagesEndIfPresent(pagesBuilder);
    extractNumberOfPagesIfPresent(pagesBuilder);
    return pagesBuilder.build();
  }

  private void extractNumberOfPagesIfPresent(Pages.Builder pagesBuilder) {
    var pages = extractOptJsonNodeTextValue(expandedResource, JSON_PTR_PAGES_NUMBER);
    pages.ifPresent(pagesBuilder::withNumberOfPages);
  }

  private void extractPagesEndIfPresent(Pages.Builder pagesBuilder) {
    var end = extractOptJsonNodeTextValue(expandedResource, JSON_PRT_PAGES_END);
    end.ifPresent(pagesBuilder::withEnd);
  }

  private void extractPagesBeginIfPresent(Pages.Builder pagesBuilder) {
    var begin = extractOptJsonNodeTextValue(expandedResource, JSON_PTR_PAGES_BEGIN);
    begin.ifPresent(pagesBuilder::withBegin);
  }

  private PublicationChannel buildPublicationChannel() {
    var publicationChannel = candidate.getPublicationChannel();
    var publicationChannelBuilder =
        PublicationChannel.builder()
            .withScientificValue(
                ScientificValue.parse(publicationChannel.scientificValue().getValue()));

    if (nonNull(publicationChannel.id())) { // Might be null for candidates imported via Cristin
      publicationChannelBuilder.withId(publicationChannel.id());
    }
    if (nonNull(
        publicationChannel.channelType())) { // Might be null for candidates imported via Cristin
      publicationChannelBuilder.withType(publicationChannel.channelType().getValue());
      publicationChannelBuilder.withName(extractName(publicationChannel.channelType()));
      publicationChannelBuilder.withPrintIssn(extractPrintIssn(publicationChannel.channelType()));
    }
    return publicationChannelBuilder.build();
  }

  private String extractPrintIssn(ChannelType channelType) {
    return switch (channelType) {
      case JOURNAL -> extractJournalPrintIssn();
      case SERIES -> extractSeriesPrintIssn();
      case PUBLISHER, NON_CANDIDATE -> null;
    };
  }

  private String extractSeriesPrintIssn() {
    return extractJsonNodeTextValue(expandedResource, JSON_PTR_SERIES_PISSN);
  }

  private String extractJournalPrintIssn() {
    return extractJsonNodeTextValue(expandedResource, JSON_POINTER_JOURNAL_PISSN);
  }

  private String extractName(ChannelType channelType) {
    return switch (channelType) {
      case JOURNAL -> extractJournalName();
      case PUBLISHER -> extractPublisherName();
      case SERIES -> extractSeriesName();
      case NON_CANDIDATE -> null;
    };
  }

  private String extractSeriesName() {
    return extractJsonNodeTextValue(expandedResource, JSON_PTR_SERIES_NAME);
  }

  private String extractPublisherName() {
    return extractJsonNodeTextValue(expandedResource, JSON_PTR_PUBLISHER_NAME);
  }

  private String extractJournalName() {
    return extractJsonNodeTextValue(expandedResource, JSON_PTR_JOURNAL_NAME);
  }

  private Stream<Approval> streamValues(Map<URI, Approval> approvals) {
    return approvals.values().stream();
  }

  private List<ApprovalView> createApprovals(List<ContributorType> expandedContributors) {
    return streamValues(candidate.approvals())
        .map(approval -> toApproval(approval, expandedContributors))
        .toList();
  }

  private Optional<Map<String, String>> extractLabelsFromExpandedResource(
      JsonNode resource, Approval approval) {
    var topLevelOrganizations = resource.at(JSON_PTR_TOP_LEVEL_ORGANIZATIONS);
    return topLevelOrganizations.isMissingNode() && !topLevelOrganizations.isArray()
        ? Optional.empty()
        : extractLabels(approval, topLevelOrganizations);
  }

  private ApprovalView toApproval(Approval approval, List<ContributorType> expandedContributors) {
    return ApprovalView.builder()
        .withInstitutionId(approval.institutionId())
        .withLabels(extractLabels(approval).orElse(Collections.emptyMap()))
        .withApprovalStatus(getApprovalStatus(approval))
        .withPoints(getInstitutionPoints(approval))
        .withInvolvedOrganizations(extractInvolvedOrganizations(approval, expandedContributors))
        .withAssignee(extractAssignee(approval))
        .withGlobalApprovalStatus(candidate.getGlobalApprovalStatus())
        .withSector(extractSector(approval.institutionId(), candidate))
        .build();
  }

  public static String extractSector(URI institutionId, Candidate candidate) {
    return candidate
        .getInstitutionPoints(institutionId)
        .map(InstitutionPoints::sector)
        .filter(not(Sector.UNKNOWN::equals))
        .map(Sector::toString)
        .orElse(null);
  }

  private String extractAssignee(Approval approval) {
    return Optional.of(approval).map(Approval::getAssigneeUsername).orElse(null);
  }

  private List<ContributorType> expandContributors() {
    return StreamSupport.stream(expandedResource.at(JSON_PTR_CONTRIBUTOR).spliterator(), false)
        .map(this::createContributor)
        .toList();
  }

  public static Optional<VerifiedNviCreatorDto> getVerifiedNviCreatorIfPresent(
      JsonNode contributor, Candidate candidate) {
    return candidate.publicationDetails().verifiedCreators().stream()
        .filter(
            creator -> creator.id().toString().equals(extractId(contributor.at(JSON_PTR_IDENTITY))))
        .findFirst();
  }

  public static Optional<UnverifiedNviCreatorDto> getUnverifiedNviCreatorIfPresent(
      JsonNode contributor, Candidate candidate) {
    return candidate.publicationDetails().unverifiedCreators().stream()
        .filter(creator -> creator.name().equals(extractName(contributor.at(JSON_PTR_IDENTITY))))
        .findFirst();
  }

  public static Optional<NviCreatorDto> getAnyNviCreatorIfPresent(
      JsonNode contributor, Candidate candidate) {
    return getVerifiedNviCreatorIfPresent(contributor, candidate)
        .map(NviCreatorDto.class::cast)
        .or(
            () ->
                getUnverifiedNviCreatorIfPresent(contributor, candidate)
                    .map(NviCreatorDto.class::cast));
  }

  private ContributorType createContributor(JsonNode contributor) {
    var identity = contributor.at(JSON_PTR_IDENTITY);
    return getAnyNviCreatorIfPresent(contributor, candidate)
        .map(value -> generateNviContributor(contributor, identity))
        .orElseGet(() -> generateContributor(contributor, identity));
  }

  private ContributorType generateContributor(JsonNode contributor, JsonNode identity) {
    return Contributor.builder()
        .withId(extractId(identity))
        .withName(extractJsonNodeTextValue(identity, JSON_PTR_NAME))
        .withOrcid(extractJsonNodeTextValue(identity, JSON_PTR_ORCID))
        .withRole(extractRoleType(contributor))
        .withAffiliations(expandAffiliations(contributor))
        .build();
  }

  private ContributorType generateNviContributor(JsonNode contributor, JsonNode identity) {
    return NviContributor.builder()
        .withId(extractId(identity))
        .withName(extractJsonNodeTextValue(identity, JSON_PTR_NAME))
        .withOrcid(extractJsonNodeTextValue(identity, JSON_PTR_ORCID))
        .withRole(extractRoleType(contributor))
        .withAffiliations(expandAffiliations(contributor))
        .build();
  }

  private List<OrganizationType> expandAffiliations(JsonNode contributor) {
    return streamNode(contributor.at(JSON_PTR_AFFILIATIONS))
        .map(affiliationNode -> expandAffiliation(affiliationNode, contributor))
        .filter(Objects::nonNull)
        .toList();
  }

  private OrganizationType expandAffiliation(JsonNode affiliation, JsonNode contributor) {
    var id = extractJsonNodeTextValue(affiliation, JSON_PTR_ID);
    if (isNull(id)) {
      LOGGER.info(
          "Skipping expansion of affiliation because of missing institutionId: {}", affiliation);
      return null;
    }
    var affiliationUri = URI.create(id);
    return isNviAffiliation(affiliation, contributor)
        ? generateAffiliationWithPartOf(affiliationUri)
        : Organization.builder().withId(affiliationUri).build();
  }

  private boolean isNviAffiliation(JsonNode affiliation, JsonNode contributor) {
    var nviCreator = getAnyNviCreatorIfPresent(contributor, candidate);
    return nviCreator.isPresent() && isNviAffiliation(nviCreator.get(), affiliation);
  }

  private boolean isNviAffiliation(NviCreatorDto creator, JsonNode affiliationNode) {
    var affiliationId = extractJsonNodeTextValue(affiliationNode, JSON_PTR_ID);

    return nonNull(affiliationId)
        && creator.affiliations().stream()
            .anyMatch(affiliation -> affiliation.toString().equals(affiliationId));
  }

  private NviOrganization generateAffiliationWithPartOf(URI id) {
    return attempt(() -> getRawContentFromUriCached(id))
        .map(Optional::get)
        .map(str -> createModel(dtoObjectMapper.readTree(str)))
        .map(model -> listPropertyPartOfObjects(model, id))
        .map(result -> buildNviOrganization(id, result))
        .orElseThrow();
  }

  private List<URI> listPropertyPartOfObjects(Model model, URI id) {
    var partOfList = new ArrayList<URI>();
    var nextPartOf = listNextPartOf(model, id.toString());
    while (nextPartOf.hasNext()) {
      var partOf = nextPartOf.next();
      partOfList.add(URI.create(partOf.toString()));
      nextPartOf = listNextPartOf(model, partOf.toString());
    }
    return partOfList;
  }

  private Optional<String> getRawContentFromUriCached(URI id) {
    if (temporaryCache.containsKey(id)) {
      return Optional.of(temporaryCache.get(id));
    }

    var rawContentFromUri = getRawContentFromUri(id);

    rawContentFromUri.ifPresent(content -> this.temporaryCache.put(id, content));

    return rawContentFromUri;
  }

  private Optional<String> getRawContentFromUri(URI uri) {
    return attempt(() -> organizationRetriever.fetchOrganization(uri).toJsonString()).toOptional();
  }

  private static String extractId(JsonNode jsonNode) {
    return extractJsonNodeTextValue(jsonNode, JSON_PTR_ID);
  }

  private static String extractName(JsonNode jsonNode) {
    return extractJsonNodeTextValue(jsonNode, JSON_PTR_NAME);
  }

  private String extractRoleType(JsonNode contributorNode) {
    return extractJsonNodeTextValue(contributorNode, JSON_PTR_ROLE_TYPE);
  }

  private PublicationDateDto extractPublicationDate() {
    return formatPublicationDate(expandedResource.at(JSON_PTR_PUBLICATION_DATE));
  }

  private String extractMainTitle() {
    return extractJsonNodeTextValue(expandedResource, JSON_PTR_MAIN_TITLE);
  }

  private String extractInstanceType() {
    return extractJsonNodeTextValue(expandedResource, JSON_PTR_INSTANCE_TYPE);
  }

  private PublicationDateDto formatPublicationDate(JsonNode publicationDateNode) {
    var year = extractJsonNodeTextValue(publicationDateNode, JSON_PTR_YEAR);
    var month = extractJsonNodeTextValue(publicationDateNode, JSON_PTR_MONTH);
    var day = extractJsonNodeTextValue(publicationDateNode, JSON_PTR_DAY);
    return new PublicationDateDto(year, month, day);
  }
}
