package no.sikt.nva.nvi.test;

import static no.sikt.nva.nvi.test.ExpandedResourceGenerator.EN_FIELD;
import static no.sikt.nva.nvi.test.ExpandedResourceGenerator.HARDCODED_ENGLISH_LABEL;
import static no.sikt.nva.nvi.test.ExpandedResourceGenerator.HARDCODED_NORWEGIAN_LABEL;
import static no.sikt.nva.nvi.test.ExpandedResourceGenerator.NB_FIELD;
import static no.sikt.nva.nvi.test.ExpandedResourceGenerator.extractAffiliations;
import static no.sikt.nva.nvi.test.TestUtils.randomBigDecimal;
import static no.sikt.nva.nvi.test.TestUtils.randomIntBetween;
import static no.unit.nva.testutils.RandomDataGenerator.randomElement;
import static no.unit.nva.testutils.RandomDataGenerator.randomString;
import static no.unit.nva.testutils.RandomDataGenerator.randomUri;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ArrayNode;
import java.net.URI;
import java.time.Instant;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import no.sikt.nva.nvi.common.service.model.Approval;
import no.sikt.nva.nvi.common.service.model.Candidate;
import no.sikt.nva.nvi.common.service.model.GlobalApprovalStatus;
import no.sikt.nva.nvi.common.service.model.PublicationDetails.Creator;
import no.sikt.nva.nvi.common.utils.JsonUtils;
import no.sikt.nva.nvi.index.model.document.ApprovalStatus;
import no.sikt.nva.nvi.index.model.document.Contributor;
import no.sikt.nva.nvi.index.model.document.ContributorType;
import no.sikt.nva.nvi.index.model.document.InstitutionPoints;
import no.sikt.nva.nvi.index.model.document.InstitutionPoints.CreatorAffiliationPoints;
import no.sikt.nva.nvi.index.model.document.NviCandidateIndexDocument;
import no.sikt.nva.nvi.index.model.document.NviContributor;
import no.sikt.nva.nvi.index.model.document.NviOrganization;
import no.sikt.nva.nvi.index.model.document.Organization;
import no.sikt.nva.nvi.index.model.document.OrganizationType;
import no.sikt.nva.nvi.index.model.document.PublicationDate;
import no.sikt.nva.nvi.index.model.document.PublicationDetails;
import no.sikt.nva.nvi.index.model.document.ReportingPeriod;
import nva.commons.core.paths.UnixPath;
import nva.commons.core.paths.UriWrapper;

public final class IndexDocumentTestUtils {

    public static final URI HARD_CODED_INTERMEDIATE_ORGANIZATION = URI.create(
        "https://example.org/organization/hardCodedIntermediateOrg");
    public static final URI HARD_CODED_TOP_LEVEL_ORG = URI.create(
        "https://example.org/organization/hardCodedPartOf");
    public static final URI NVI_CONTEXT = URI.create("https://bibsysdev.github.io/src/nvi-context.json");
    public static final String NVI_CANDIDATES_FOLDER = "nvi-candidates";
    public static final String GZIP_ENDING = ".gz";

    private IndexDocumentTestUtils() {
    }

    public static UnixPath createPath(Candidate candidate) {
        return UnixPath.of(NVI_CANDIDATES_FOLDER).addChild(candidate.getIdentifier().toString() + GZIP_ENDING);
    }

    public static List<no.sikt.nva.nvi.index.model.document.Approval> expandApprovals(Candidate candidate,
                                                                                      List<ContributorType> contributors) {
        return candidate.getApprovals()
                   .values()
                   .stream()
                   .map(approval -> toApproval(approval, candidate, contributors))
                   .toList();
    }

    public static PublicationDetails expandPublicationDetails(Candidate candidate, JsonNode expandedResource) {
        return PublicationDetails.builder()
                   .withType(ExpandedResourceGenerator.extractType(expandedResource))
                   .withId(candidate.getPublicationDetails().publicationId().toString())
                   .withTitle(ExpandedResourceGenerator.extractTitle(expandedResource))
                   .withPublicationDate(mapToPublicationDate(candidate.getPublicationDetails().publicationDate()))
                   .withContributors(
                       mapToContributors(ExpandedResourceGenerator.extractContributors(expandedResource), candidate))
                   .build();
    }

    public static NviCandidateIndexDocument randomIndexDocument(int year, URI institutionId) {
        var publicationDetails = randomPublicationDetails(institutionId);
        var approvals = createApprovals(institutionId, publicationDetails.contributors());
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
                   .withReportingPeriod(new ReportingPeriod(String.valueOf(year)))
                   .build();
    }

    public static URI randomCristinOrgUri() {
        var cristinIdentifier = randomIntBetween(0, 99) + "." + randomIntBetween(0, 99) + "." + randomIntBetween(0, 99)
                                + "." + randomIntBetween(0, 99);
        return UriWrapper.fromUri(randomUri()).addChild(cristinIdentifier).getUri();
    }

    private static no.sikt.nva.nvi.index.model.document.Approval toApproval(Approval approval, Candidate candidate,
                                                                            List<ContributorType> contributors) {
        var assignee = approval.getAssignee();
        return no.sikt.nva.nvi.index.model.document.Approval.builder()
                   .withInstitutionId(approval.getInstitutionId())
                   .withApprovalStatus(getApprovalStatus(approval))
                   .withAssignee(Objects.nonNull(assignee) ? assignee.value() : null)
                   .withPoints(getInstitutionPoints(approval, candidate))
                   .withInvolvedOrganizations(extractInvolvedOrganizations(approval, contributors))
                   .withLabels(Map.of(EN_FIELD, HARDCODED_ENGLISH_LABEL, NB_FIELD,
                                      HARDCODED_NORWEGIAN_LABEL))
                   .withGlobalApprovalStatus(candidate.getGlobalApprovalStatus())
                   .build();
    }

    private static InstitutionPoints getInstitutionPoints(Approval approval, Candidate candidate) {
        return candidate.getInstitutionPoints(approval.getInstitutionId())
                   .map(InstitutionPoints::from)
                   .orElse(null);
    }

    private static Set<URI> extractInvolvedOrganizations(Approval approval,
                                                         List<ContributorType> expandedContributors) {
        return expandedContributors.stream()
                   .filter(NviContributor.class::isInstance)
                   .map(NviContributor.class::cast)
                   .flatMap(contributor -> contributor.getOrganizationsPartOf(approval.getInstitutionId()).stream())
                   .collect(Collectors.toSet());
    }

    private static ApprovalStatus getApprovalStatus(Approval approval) {
        return isApprovalPendingAndUnassigned(approval)
                   ? ApprovalStatus.NEW
                   : ApprovalStatus.fromValue(approval.getStatus().getValue());
    }

    private static boolean isApprovalPendingAndUnassigned(Approval approval) {
        return no.sikt.nva.nvi.common.service.model.ApprovalStatus.PENDING.equals(approval.getStatus())
               && Objects.isNull(approval.getAssignee());
    }

    private static PublicationDate mapToPublicationDate(
        no.sikt.nva.nvi.common.service.model.PublicationDetails.PublicationDate publicationDate) {
        return new PublicationDate(publicationDate.year(), publicationDate.month(), publicationDate.day());
    }

    private static List<ContributorType> mapToContributors(ArrayNode contributorNodes, Candidate candidate) {
        return JsonUtils.streamNode(contributorNodes)
                   .map(contributorNode -> toContributorWithExpandedAffiliation(contributorNode, candidate))
                   .toList();
    }

    private static ContributorType toContributorWithExpandedAffiliation(JsonNode contributorNode, Candidate candidate) {
        var affiliations = extractAffiliations(contributorNode);
        var creator = getNviCreatorIfPresent(candidate, contributorNode);
        return creator.map(value -> generateNviContributor(contributorNode, value, affiliations))
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

    private static ContributorType generateNviContributor(JsonNode contributorNode, Creator value,
                                                          List<URI> affiliations) {
        return NviContributor.builder()
                   .withId(ExpandedResourceGenerator.extractId(contributorNode))
                   .withName(ExpandedResourceGenerator.extractName(contributorNode))
                   .withOrcid(ExpandedResourceGenerator.extractOrcid(contributorNode))
                   .withRole(ExpandedResourceGenerator.extractRole(contributorNode))
                   .withAffiliations(expandAffiliationsWithPartOf(value, affiliations))
                   .build();
    }

    private static Optional<Creator> getNviCreatorIfPresent(Candidate candidate, JsonNode contributorNode) {
        return candidate.getPublicationDetails()
                   .creators()
                   .stream()
                   .filter(
                       creator -> creator.id().toString().equals(ExpandedResourceGenerator.extractId(contributorNode)))
                   .findFirst();
    }

    private static List<OrganizationType> expandAffiliations(List<URI> uris) {
        return uris.stream()
                   .map(IndexDocumentTestUtils::generateOrganization)
                   .toList();
    }

    private static List<OrganizationType> expandAffiliationsWithPartOf(Creator creator, List<URI> uris) {
        return uris.stream()
                   .map(uri -> toAffiliationWithPartOf(uri, isNviAffiliation(creator, uri)))
                   .toList();
    }

    private static boolean isNviAffiliation(Creator creator, URI uri) {
        return creator.affiliations()
                   .stream()
                   .anyMatch(affiliation -> affiliation.equals(uri));
    }

    private static OrganizationType toAffiliationWithPartOf(URI id, boolean isNviAffiliation) {
        return isNviAffiliation ? generateNviOrganization(id) : generateOrganization(id);
    }

    private static OrganizationType generateOrganization(URI id) {
        return Organization.builder()
                   .withId(id)
                   .withPartOf(List.of(HARD_CODED_TOP_LEVEL_ORG))
                   .build();
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
        return NviOrganization.builder()
                   .withId(id)
                   .withPartOf(Collections.emptyList())
                   .build();
    }

    private static PublicationDetails randomPublicationDetails(URI institutionId) {
        return PublicationDetails.builder()
                   .withType(randomString())
                   .withId(randomUri().toString())
                   .withTitle(randomString())
                   .withPublicationDate(randomPublicationDate())
                   .withContributors(List.of(randomContributor(institutionId)))
                   .build();
    }

    private static NviContributor randomContributor(URI institutionId) {
        return NviContributor.builder()
                   .withId(randomUri().toString())
                   .withName(randomString())
                   .withOrcid(randomString())
                   .withRole(randomString())
                   .withAffiliations(List.of(randomNviAffiliation(institutionId)))
                   .build();
    }

    private static NviOrganization randomNviAffiliation(URI institutionId) {
        return NviOrganization.builder()
                   .withId(randomCristinOrgUri())
                   .withPartOf(List.of(institutionId))
                   .build();
    }

    private static PublicationDate randomPublicationDate() {
        return new PublicationDate(randomString(), randomString(), randomString());
    }

    private static List<no.sikt.nva.nvi.index.model.document.Approval> createApprovals(URI uri,
                                                                                       List<ContributorType> contributors) {
        return contributors.stream()
                   .filter(NviContributor.class::isInstance)
                   .map(NviContributor.class::cast)
                   .map(contributor -> createApproval(uri, contributor, randomElement(GlobalApprovalStatus.values())))
                   .toList();
    }

    private static no.sikt.nva.nvi.index.model.document.Approval createApproval(URI institutionId,
                                                                                NviContributor contributor,
                                                                                GlobalApprovalStatus globalApprovalStatus) {
        return no.sikt.nva.nvi.index.model.document.Approval.builder()
                   .withInstitutionId(institutionId)
                   .withApprovalStatus(ApprovalStatus.NEW)
                   .withAssignee(randomString())
                   .withPoints(generateInstitutionPoints(contributor, institutionId))
                   .withInvolvedOrganizations(new HashSet<>(contributor.getOrganizationsPartOf(institutionId)))
                   .withLabels(Map.of(EN_FIELD, HARDCODED_ENGLISH_LABEL, NB_FIELD,
                                      HARDCODED_NORWEGIAN_LABEL))
                   .withGlobalApprovalStatus(globalApprovalStatus)
                   .build();
    }

    private static InstitutionPoints generateInstitutionPoints(NviContributor contributor, URI institutionId) {
        return InstitutionPoints.builder()
                   .withInstitutionId(institutionId)
                   .withInstitutionPoints(randomBigDecimal())
                   .withCreatorAffiliationPoints(generateListOfCreatorAffiliationPoints(contributor))
                   .build();
    }

    private static List<CreatorAffiliationPoints> generateListOfCreatorAffiliationPoints(NviContributor contributor) {
        return contributor.affiliations().stream()
                   .filter(NviOrganization.class::isInstance)
                   .map(NviOrganization.class::cast)
                   .map(affiliation -> generateCreatorAffiliationPoints(contributor, affiliation))
                   .toList();
    }

    private static CreatorAffiliationPoints generateCreatorAffiliationPoints(NviContributor contributor,
                                                                             NviOrganization affiliation) {
        return CreatorAffiliationPoints.builder()
                   .withNviCreator(URI.create(contributor.id()))
                   .withAffiliationId(affiliation.id())
                   .withPoints(randomBigDecimal())
                   .build();
    }
}
