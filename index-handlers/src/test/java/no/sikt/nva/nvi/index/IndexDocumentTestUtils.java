package no.sikt.nva.nvi.index;

import static java.util.Collections.emptyList;
import static java.util.Objects.isNull;
import static java.util.Objects.nonNull;
import static no.sikt.nva.nvi.common.model.EnumFixtures.randomValidScientificValue;
import static no.sikt.nva.nvi.common.model.PublicationDateFixtures.getRandomDateInCurrentYearAsDto;
import static no.sikt.nva.nvi.common.utils.JsonPointers.JSON_PTR_PUBLICATION_CONTEXT;
import static no.sikt.nva.nvi.common.utils.JsonUtils.extractJsonNodeTextValue;
import static no.sikt.nva.nvi.index.ExpandedResourceGenerator.extractAffiliations;
import static no.sikt.nva.nvi.index.utils.NviCandidateIndexDocumentGenerator.getAnyNviCreatorIfPresent;
import static no.sikt.nva.nvi.test.TestConstants.EN_FIELD;
import static no.sikt.nva.nvi.test.TestConstants.HARDCODED_ENGLISH_LABEL;
import static no.sikt.nva.nvi.test.TestConstants.HARDCODED_NORWEGIAN_LABEL;
import static no.sikt.nva.nvi.test.TestConstants.NB_FIELD;
import static no.sikt.nva.nvi.test.TestUtils.randomBigDecimal;
import static no.sikt.nva.nvi.test.TestUtils.randomIntBetween;
import static no.unit.nva.testutils.RandomDataGenerator.randomElement;
import static no.unit.nva.testutils.RandomDataGenerator.randomString;
import static no.unit.nva.testutils.RandomDataGenerator.randomUri;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ArrayNode;
import java.net.URI;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import no.sikt.nva.nvi.common.model.ChannelType;
import no.sikt.nva.nvi.common.service.dto.NviCreatorDto;
import no.sikt.nva.nvi.common.service.model.Approval;
import no.sikt.nva.nvi.common.service.model.Candidate;
import no.sikt.nva.nvi.common.service.model.GlobalApprovalStatus;
import no.sikt.nva.nvi.common.utils.JsonUtils;
import no.sikt.nva.nvi.index.model.document.ApprovalStatus;
import no.sikt.nva.nvi.index.model.document.Contributor;
import no.sikt.nva.nvi.index.model.document.ContributorType;
import no.sikt.nva.nvi.index.model.document.InstitutionPoints;
import no.sikt.nva.nvi.index.model.document.InstitutionPoints.CreatorAffiliationPoints;
import no.sikt.nva.nvi.index.model.document.NviCandidateIndexDocument;
import no.sikt.nva.nvi.index.model.document.NviCandidateIndexDocument.Builder;
import no.sikt.nva.nvi.index.model.document.NviContributor;
import no.sikt.nva.nvi.index.model.document.NviOrganization;
import no.sikt.nva.nvi.index.model.document.Organization;
import no.sikt.nva.nvi.index.model.document.OrganizationType;
import no.sikt.nva.nvi.index.model.document.Pages;
import no.sikt.nva.nvi.index.model.document.PublicationChannel;
import no.sikt.nva.nvi.index.model.document.PublicationDetails;
import no.sikt.nva.nvi.index.model.document.ReportingPeriod;
import nva.commons.core.paths.UnixPath;
import nva.commons.core.paths.UriWrapper;

// Should be refactored, technical debt task: https://sikt.atlassian.net/browse/NP-48093
@SuppressWarnings({"PMD.GodClass", "PMD.CouplingBetweenObjects"})
public final class IndexDocumentTestUtils {

  public static final URI HARD_CODED_INTERMEDIATE_ORGANIZATION =
      URI.create("https://example.org/organization/hardCodedIntermediateOrg");
  public static final URI HARD_CODED_TOP_LEVEL_ORG =
      URI.create("https://example.org/organization/hardCodedPartOf");
  public static final URI NVI_CONTEXT =
      URI.create("https://bibsysdev.github.io/src/nvi-context.json");
  public static final String NVI_CANDIDATES_FOLDER = "nvi-candidates";
  public static final String GZIP_ENDING = ".gz";
  public static final String DELIMITER = "\\.";

  private IndexDocumentTestUtils() {}

  public static UnixPath createPath(Candidate candidate) {
    return UnixPath.of(NVI_CANDIDATES_FOLDER)
        .addChild(candidate.getIdentifier().toString() + GZIP_ENDING);
  }

  public static List<no.sikt.nva.nvi.index.model.document.Approval> expandApprovals(
      Candidate candidate, List<ContributorType> contributors) {
    return candidate.getApprovals().values().stream()
        .map(approval -> toApproval(approval, candidate, contributors))
        .toList();
  }

  public static PublicationDetails expandPublicationDetails(
      Candidate candidate, JsonNode expandedResource) {
    return PublicationDetails.builder()
        .withType(ExpandedResourceGenerator.extractType(expandedResource))
        .withId(candidate.getPublicationDetails().publicationId().toString())
        .withTitle(ExpandedResourceGenerator.extractTitle(expandedResource))
        .withAbstract(ExpandedResourceGenerator.extractOptionalAbstract(expandedResource))
        .withPublicationDate(
            candidate.getPublicationDetails().publicationDate().toDtoPublicationDate())
        .withContributors(
            mapToContributors(
                ExpandedResourceGenerator.extractContributors(expandedResource), candidate))
        .withPublicationChannel(getPublicationChannel(expandedResource, candidate))
        .withPages(getPages(expandedResource))
        .withLanguage(extractOptionalLanguage(expandedResource))
        .build();
  }

  public static URI randomCristinOrgUri() {
    return cristinOrgUriWithTopLevel(String.valueOf(randomIntBetween(100_000, 200_000)));
  }

  public static URI cristinOrgUriWithTopLevel(String topLevelIdentifier) {
    var cristinIdentifier =
        String.join(
            ".",
            topLevelIdentifier,
            String.valueOf(randomIntBetween(0, 99)),
            String.valueOf(randomIntBetween(0, 99)),
            String.valueOf(randomIntBetween(0, 99)));
    return UriWrapper.fromUri(randomUri()).addChild(cristinIdentifier).getUri();
  }

  public static NviCandidateIndexDocument randomIndexDocumentWith(int year, URI institutionId) {
    var publicationDetails =
        publicationDetailsWithNviContributorsAffiliatedWith(institutionId).build();
    var approvals = createApprovals(institutionId, publicationDetails.nviContributors());
    return getBuilder(year, approvals, publicationDetails).build();
  }

  public static NviCandidateIndexDocument indexDocumentWithoutPages(int year, URI institutionId) {
    var publicationDetails =
        publicationDetailsWithNviContributorsAffiliatedWith(institutionId).withPages(null).build();
    var approvals = createApprovals(institutionId, publicationDetails.nviContributors());
    return getBuilder(year, approvals, publicationDetails).build();
  }

  public static NviCandidateIndexDocument indexDocumentWithoutLanguage(
      int year, URI institutionId) {
    var publicationDetails =
        publicationDetailsWithNviContributorsAffiliatedWith(institutionId)
            .withLanguage(null)
            .build();
    var approvals = createApprovals(institutionId, publicationDetails.nviContributors());
    return getBuilder(year, approvals, publicationDetails).build();
  }

  public static NviCandidateIndexDocument indexDocumentWithLanguage(
      int currentYear, URI topLevelCristinOrg, String languageUri) {
    var publicationDetails =
        publicationDetailsWithNviContributorsAffiliatedWith(topLevelCristinOrg)
            .withLanguage(languageUri)
            .build();
    var approvals = createApprovals(topLevelCristinOrg, publicationDetails.nviContributors());
    return getBuilder(currentYear, approvals, publicationDetails).build();
  }

  public static NviCandidateIndexDocument indexDocumentWithoutIssn(int year, URI institutionId) {
    var publicationDetails =
        publicationDetailsWithNviContributorsAffiliatedWith(institutionId)
            .withPublicationChannel(randomPublicationChannelBuilder().withPrintIssn(null).build())
            .build();
    var approvals = createApprovals(institutionId, publicationDetails.nviContributors());
    return getBuilder(year, approvals, publicationDetails).build();
  }

  public static NviCandidateIndexDocument indexDocumentWithoutOptionalPublicationChannelData(
      int year, URI institutionId) {
    // This is not a valid state for candidates created in nva-nvi, but it may occur for candidates
    // imported via
    // Cristin.
    var publicationChannel =
        PublicationChannel.builder().withScientificValue(randomValidScientificValue()).build();
    var publicationDetails =
        publicationDetailsWithNviContributorsAffiliatedWith(institutionId)
            .withPublicationChannel(publicationChannel)
            .build();
    var approvals = createApprovals(institutionId, publicationDetails.nviContributors());
    return getBuilder(year, approvals, publicationDetails).build();
  }

  public static NviCandidateIndexDocument indexDocumentMissingVerifiedCreators(
      int year, URI institutionId) {
    var unverifiedCreator = randomNviContributorBuilder(institutionId).withId(null).build();
    var publicationDetails =
        publicationDetailsWithNviContributorsAffiliatedWith(institutionId)
            .withContributors(List.of(unverifiedCreator))
            .build();
    var approvalsWithoutCreatorAffiliationPoints = createApprovals(institutionId, emptyList());
    return getBuilder(year, approvalsWithoutCreatorAffiliationPoints, publicationDetails).build();
  }

  public static NviCandidateIndexDocument indexDocumentMissingApprovals(
      int year, URI institutionId) {
    var publicationDetails =
        publicationDetailsWithNviContributorsAffiliatedWith(institutionId).build();
    var noApprovals = new ArrayList<no.sikt.nva.nvi.index.model.document.Approval>();
    return getBuilder(year, noApprovals, publicationDetails).build();
  }

  public static PublicationChannel randomPublicationChannel() {
    return randomPublicationChannelBuilder().build();
  }

  public static PublicationChannel.Builder randomPublicationChannelBuilder() {
    return PublicationChannel.builder()
        .withId(randomUri())
        .withType(randomString())
        .withScientificValue(randomValidScientificValue())
        .withName(randomString());
  }

  public static Pages randomPages() {
    return Pages.builder()
        .withBegin(randomString())
        .withEnd(randomString())
        .withNumberOfPages(randomString())
        .build();
  }

  public static NviContributor.Builder randomNviContributorBuilder(URI institutionId) {
    return NviContributor.builder()
        .withId(randomUri().toString())
        .withName(randomString())
        .withOrcid(randomString())
        .withRole(randomString())
        .withAffiliations(
            List.of(
                randomSubUnitNviAffiliation(institutionId),
                nviOrganization(institutionId),
                nviOrganization(randomUri()),
                randomNonNviAffiliation()));
  }

  public static NviContributor randomNviContributor(URI institutionId) {
    return randomNviContributorBuilder(institutionId).build();
  }

  private static String extractOptionalLanguage(JsonNode expandedResource) {
    return extractJsonNodeTextValue(expandedResource, "/entityDescription/language");
  }

  private static Pages getPages(JsonNode expandedResource) {
    var pagesNode = expandedResource.at("/entityDescription/reference/publicationInstance/pages");
    return Pages.builder()
        .withBegin(extractJsonNodeTextValue(pagesNode, "/begin"))
        .withEnd(extractJsonNodeTextValue(pagesNode, "/end"))
        .withNumberOfPages(extractJsonNodeTextValue(pagesNode, "/pages"))
        .build();
  }

  private static PublicationChannel getPublicationChannel(
      JsonNode expandedResource, Candidate candidate) {
    var channel = candidate.getPublicationChannel();
    var channelType = channel.channelType();
    var publicationChannelBuilder =
        PublicationChannel.builder()
            .withScientificValue(channel.scientificValue())
            .withName(extractChannelName(expandedResource, channelType));
    if (nonNull(candidate.getPublicationChannel().id())) {
      publicationChannelBuilder.withId(channel.id());
    }
    if (nonNull(channelType)) {
      publicationChannelBuilder.withType(channelType.getValue());
      publicationChannelBuilder.withPrintIssn(extractPrintIssn(expandedResource, channelType));
    }
    return publicationChannelBuilder.build();
  }

  private static String extractPrintIssn(JsonNode expandedResource, ChannelType channelType) {
    return switch (channelType) {
      case JOURNAL -> extractJournalIssn(expandedResource);
      case SERIES -> extractSeriesIssn(expandedResource);
      case PUBLISHER, NON_CANDIDATE -> null;
    };
  }

  private static String extractSeriesIssn(JsonNode expandedResource) {
    return extractJsonNodeTextValue(
        expandedResource, JSON_PTR_PUBLICATION_CONTEXT + "/series/printIssn");
  }

  private static String extractJournalIssn(JsonNode expandedResource) {
    return extractJsonNodeTextValue(expandedResource, JSON_PTR_PUBLICATION_CONTEXT + "/printIssn");
  }

  private static String extractChannelName(JsonNode expandedResource, ChannelType channelType) {
    if (isNull(channelType)) {
      return null;
    }
    return switch (channelType) {
      case JOURNAL -> extractJournalName(expandedResource);
      case PUBLISHER -> extractPublisherName(expandedResource);
      case SERIES -> extractSeriesName(expandedResource);
      default -> null;
    };
  }

  private static String extractSeriesName(JsonNode expandedResource) {
    return extractJsonNodeTextValue(
        expandedResource, JSON_PTR_PUBLICATION_CONTEXT + "/series/name");
  }

  private static String extractPublisherName(JsonNode expandedResource) {
    return extractJsonNodeTextValue(
        expandedResource, JSON_PTR_PUBLICATION_CONTEXT + "/publisher/name");
  }

  private static String extractJournalName(JsonNode expandedResource) {
    return extractJsonNodeTextValue(expandedResource, JSON_PTR_PUBLICATION_CONTEXT + "/name");
  }

  private static Builder getBuilder(
      int year,
      List<no.sikt.nva.nvi.index.model.document.Approval> approvals,
      PublicationDetails publicationDetails) {
    return NviCandidateIndexDocument.builder()
        .withContext(Candidate.getContextUri())
        .withId(randomUri())
        .withIsApplicable(true)
        .withIdentifier(UUID.randomUUID())
        .withApprovals(approvals)
        .withPoints(randomBigDecimal())
        .withPublicationDetails(publicationDetails)
        .withNumberOfApprovals(approvals.size())
        .withCreatorShareCount(randomIntBetween(1, 10))
        .withReported(true)
        .withGlobalApprovalStatus(randomElement(GlobalApprovalStatus.values()))
        .withPublicationTypeChannelLevelPoints(randomBigDecimal())
        .withInternationalCollaborationFactor(randomBigDecimal())
        .withCreatedDate(Instant.now())
        .withModifiedDate(Instant.now())
        .withReportingPeriod(new ReportingPeriod(String.valueOf(year)));
  }

  private static no.sikt.nva.nvi.index.model.document.Approval toApproval(
      Approval approval, Candidate candidate, List<ContributorType> contributors) {
    return no.sikt.nva.nvi.index.model.document.Approval.builder()
        .withInstitutionId(approval.getInstitutionId())
        .withApprovalStatus(getApprovalStatus(approval))
        .withAssignee(approval.getAssigneeUsername())
        .withPoints(getInstitutionPoints(approval, candidate))
        .withInvolvedOrganizations(extractInvolvedOrganizations(approval, contributors))
        .withLabels(Map.of(EN_FIELD, HARDCODED_ENGLISH_LABEL, NB_FIELD, HARDCODED_NORWEGIAN_LABEL))
        .withGlobalApprovalStatus(candidate.getGlobalApprovalStatus())
        .build();
  }

  private static InstitutionPoints getInstitutionPoints(Approval approval, Candidate candidate) {
    return candidate
        .getInstitutionPoints(approval.getInstitutionId())
        .map(InstitutionPoints::from)
        .orElse(null);
  }

  private static Set<URI> extractInvolvedOrganizations(
      Approval approval, List<ContributorType> expandedContributors) {
    return expandedContributors.stream()
        .filter(NviContributor.class::isInstance)
        .map(NviContributor.class::cast)
        .flatMap(
            contributor -> contributor.getOrganizationsPartOf(approval.getInstitutionId()).stream())
        .collect(Collectors.toSet());
  }

  private static ApprovalStatus getApprovalStatus(Approval approval) {
    return isApprovalPendingAndUnassigned(approval)
        ? ApprovalStatus.NEW
        : ApprovalStatus.parse(approval.getStatus().getValue());
  }

  private static boolean isApprovalPendingAndUnassigned(Approval approval) {
    return no.sikt.nva.nvi.common.service.model.ApprovalStatus.PENDING.equals(approval.getStatus())
        && isNull(approval.getAssigneeUsername());
  }

  private static List<ContributorType> mapToContributors(
      ArrayNode contributorNodes, Candidate candidate) {
    return JsonUtils.streamNode(contributorNodes)
        .map(contributorNode -> toContributorWithExpandedAffiliation(contributorNode, candidate))
        .toList();
  }

  private static ContributorType toContributorWithExpandedAffiliation(
      JsonNode contributorNode, Candidate candidate) {
    var affiliations = extractAffiliations(contributorNode);
    var creator = getAnyNviCreatorIfPresent(contributorNode, candidate);
    return creator
        .map(value -> generateNviContributor(contributorNode, value, affiliations))
        .orElseGet(() -> generateContributor(contributorNode, affiliations));
  }

  private static Contributor generateContributor(JsonNode contributorNode, List<URI> affiliations) {
    return Contributor.builder()
        .withId(ExpandedResourceGenerator.extractId(contributorNode))
        .withName(ExpandedResourceGenerator.extractName(contributorNode))
        .withOrcid(ExpandedResourceGenerator.extractOrcid(contributorNode))
        .withRole(ExpandedResourceGenerator.extractRole(contributorNode))
        .withAffiliations(expandAffiliations(affiliations))
        .build();
  }

  private static ContributorType generateNviContributor(
      JsonNode contributorNode, NviCreatorDto value, List<URI> affiliations) {
    return NviContributor.builder()
        .withId(ExpandedResourceGenerator.extractId(contributorNode))
        .withName(ExpandedResourceGenerator.extractName(contributorNode))
        .withOrcid(ExpandedResourceGenerator.extractOrcid(contributorNode))
        .withRole(ExpandedResourceGenerator.extractRole(contributorNode))
        .withAffiliations(expandAffiliationsWithPartOf(value, affiliations))
        .build();
  }

  private static List<OrganizationType> expandAffiliations(List<URI> uris) {
    return uris.stream().map(IndexDocumentTestUtils::generateOrganization).toList();
  }

  private static List<OrganizationType> expandAffiliationsWithPartOf(
      NviCreatorDto creator, List<URI> uris) {
    return uris.stream()
        .map(uri -> toAffiliationWithPartOf(uri, isNviAffiliation(creator, uri)))
        .toList();
  }

  private static boolean isNviAffiliation(NviCreatorDto creator, URI uri) {
    return creator.affiliations().stream().anyMatch(affiliation -> affiliation.equals(uri));
  }

  private static OrganizationType toAffiliationWithPartOf(URI id, boolean isNviAffiliation) {
    return isNviAffiliation ? generateNviOrganization(id) : generateOrganization(id);
  }

  private static OrganizationType generateOrganization(URI id) {
    return Organization.builder().withId(id).withPartOf(List.of(HARD_CODED_TOP_LEVEL_ORG)).build();
  }

  private static OrganizationType generateNviOrganization(URI id) {
    return id.equals(HARD_CODED_TOP_LEVEL_ORG)
        ? generateTopLevelNviOrg(id)
        : generateIntermediateLevelNviOrg(id);
  }

  private static NviOrganization generateIntermediateLevelNviOrg(URI id) {
    return NviOrganization.builder()
        .withId(id)
        .withPartOf(List.of(HARD_CODED_INTERMEDIATE_ORGANIZATION, HARD_CODED_TOP_LEVEL_ORG))
        .build();
  }

  private static NviOrganization generateTopLevelNviOrg(URI id) {
    return NviOrganization.builder().withId(id).withPartOf(emptyList()).build();
  }

  private static PublicationDetails.Builder publicationDetailsWithNviContributorsAffiliatedWith(
      URI institutionId) {
    return PublicationDetails.builder()
        .withType(randomString())
        .withId(randomUri().toString())
        .withTitle(randomString())
        .withPublicationDate(getRandomDateInCurrentYearAsDto())
        .withContributors(
            List.of(randomNviContributor(institutionId), randomNviContributor(institutionId)))
        .withPublicationChannel(randomPublicationChannel())
        .withPages(randomPages());
  }

  private static NviOrganization nviOrganization(URI id) {
    return NviOrganization.builder().withId(id).withPartOf(emptyList()).build();
  }

  private static Organization randomNonNviAffiliation() {
    return Organization.builder().withId(randomUri()).withPartOf(List.of(randomUri())).build();
  }

  private static NviOrganization randomSubUnitNviAffiliation(URI institutionId) {
    var topLevelIdentifier =
        UriWrapper.fromUri(institutionId).getLastPathElement().split(DELIMITER)[0];
    var id = cristinOrgUriWithTopLevel(topLevelIdentifier);
    return NviOrganization.builder().withId(id).withPartOf(List.of(institutionId)).build();
  }

  private static List<no.sikt.nva.nvi.index.model.document.Approval> createApprovals(
      URI uri, List<NviContributor> contributors) {
    return List.of(createApproval(uri, contributors, randomElement(GlobalApprovalStatus.values())));
  }

  private static no.sikt.nva.nvi.index.model.document.Approval createApproval(
      URI institutionId,
      List<NviContributor> contributors,
      GlobalApprovalStatus globalApprovalStatus) {
    var involvedOrganizations =
        new HashSet<>(filterContributorsPartOf(institutionId, contributors));
    var institutionPoints = generateInstitutionPoints(contributors, institutionId);
    return getApprovalBuilder(
            institutionId, globalApprovalStatus, institutionPoints, involvedOrganizations)
        .build();
  }

  private static no.sikt.nva.nvi.index.model.document.Approval.Builder getApprovalBuilder(
      URI institutionId,
      GlobalApprovalStatus globalApprovalStatus,
      InstitutionPoints institutionPoints,
      Set<URI> involvedOrganizations) {
    return no.sikt.nva.nvi.index.model.document.Approval.builder()
        .withInstitutionId(institutionId)
        .withApprovalStatus(ApprovalStatus.NEW)
        .withAssignee(randomString())
        .withPoints(institutionPoints)
        .withInvolvedOrganizations(involvedOrganizations)
        .withLabels(Map.of(EN_FIELD, HARDCODED_ENGLISH_LABEL, NB_FIELD, HARDCODED_NORWEGIAN_LABEL))
        .withGlobalApprovalStatus(globalApprovalStatus);
  }

  private static List<URI> filterContributorsPartOf(
      URI institutionId, List<NviContributor> contributors) {
    return contributors.stream()
        .flatMap(contributor -> contributor.getOrganizationsPartOf(institutionId).stream())
        .toList();
  }

  private static InstitutionPoints generateInstitutionPoints(
      List<NviContributor> contributors, URI institutionId) {
    var creatorAffiliationPoints =
        contributors.stream()
            .flatMap(IndexDocumentTestUtils::generateListOfCreatorAffiliationPoints)
            .toList();
    return getInstitutionPointsBuilder(institutionId, creatorAffiliationPoints).build();
  }

  private static InstitutionPoints.Builder getInstitutionPointsBuilder(
      URI institutionId, List<CreatorAffiliationPoints> creatorAffiliationPoints) {
    return InstitutionPoints.builder()
        .withInstitutionId(institutionId)
        .withInstitutionPoints(randomBigDecimal())
        .withCreatorAffiliationPoints(creatorAffiliationPoints);
  }

  private static Stream<CreatorAffiliationPoints> generateListOfCreatorAffiliationPoints(
      NviContributor contributor) {
    return contributor.affiliations().stream()
        .filter(NviOrganization.class::isInstance)
        .map(NviOrganization.class::cast)
        .map(affiliation -> generateCreatorAffiliationPoints(contributor, affiliation));
  }

  private static CreatorAffiliationPoints generateCreatorAffiliationPoints(
      NviContributor contributor, NviOrganization affiliation) {
    return CreatorAffiliationPoints.builder()
        .withNviCreator(URI.create(contributor.id()))
        .withAffiliationId(affiliation.id())
        .withPoints(randomBigDecimal())
        .build();
  }
}
