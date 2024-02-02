package no.sikt.nva.nvi.test;

import static no.sikt.nva.nvi.test.ExpandedResourceGenerator.EN_FIELD;
import static no.sikt.nva.nvi.test.ExpandedResourceGenerator.HARDCODED_ENGLISH_LABEL;
import static no.sikt.nva.nvi.test.ExpandedResourceGenerator.HARDCODED_NORWEGIAN_LABEL;
import static no.sikt.nva.nvi.test.ExpandedResourceGenerator.NB_FIELD;
import static no.sikt.nva.nvi.test.ExpandedResourceGenerator.extractAffiliations;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ArrayNode;
import java.math.BigDecimal;
import java.net.URI;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Objects;
import java.util.Optional;
import no.sikt.nva.nvi.common.service.model.Approval;
import no.sikt.nva.nvi.common.service.model.Candidate;
import no.sikt.nva.nvi.common.service.model.PublicationDetails.Creator;
import no.sikt.nva.nvi.common.utils.JsonUtils;
import no.sikt.nva.nvi.index.model.ApprovalStatus;
import no.sikt.nva.nvi.index.model.Contributor;
import no.sikt.nva.nvi.index.model.ContributorType;
import no.sikt.nva.nvi.index.model.NviContributor;
import no.sikt.nva.nvi.index.model.NviOrganization;
import no.sikt.nva.nvi.index.model.Organization;
import no.sikt.nva.nvi.index.model.OrganizationType;
import no.sikt.nva.nvi.index.model.PublicationDate;
import no.sikt.nva.nvi.index.model.PublicationDetails;
import nva.commons.core.paths.UnixPath;

public final class IndexDocumentTestUtils {

    public static final String HARD_CODED_PART_OF = "https://example.org/organization/hardCodedPartOf";
    public static final URI NVI_CONTEXT = URI.create("https://bibsysdev.github.io/src/nvi-context.json");
    public static final String NVI_CANDIDATES_FOLDER = "nvi-candidates";
    public static final String GZIP_ENDING = ".gz";

    private IndexDocumentTestUtils() {
    }

    public static UnixPath createPath(Candidate candidate) {
        return UnixPath.of(NVI_CANDIDATES_FOLDER).addChild(candidate.getIdentifier().toString() + GZIP_ENDING);
    }

    public static List<no.sikt.nva.nvi.index.model.Approval> expandApprovals(Candidate candidate) {
        return candidate.getApprovals()
                   .entrySet()
                   .stream()
                   .map(entry -> toApproval(entry, candidate))
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

    private static no.sikt.nva.nvi.index.model.Approval toApproval(Entry<URI, Approval> approvalEntry,
                                                                   Candidate candidate) {
        var assignee = approvalEntry.getValue().getAssignee();
        return no.sikt.nva.nvi.index.model.Approval.builder()
                   .withId(approvalEntry.getKey().toString())
                   .withInstitutionId(approvalEntry.getKey().toString())
                   .withApprovalStatus(ApprovalStatus.fromValue(approvalEntry.getValue().getStatus().getValue()))
                   .withAssignee(Objects.nonNull(assignee) ? assignee.value() : null)
                   .withPoints(getPoints(candidate, approvalEntry.getKey()))
                   .withLabels(Map.of(EN_FIELD, HARDCODED_ENGLISH_LABEL, NB_FIELD, HARDCODED_NORWEGIAN_LABEL))
                   .build();
    }

    private static BigDecimal getPoints(Candidate candidate, URI institutionId) {
        return candidate.getInstitutionPoints()
                   .entrySet()
                   .stream()
                   .filter(entry -> entry.getKey().equals(institutionId))
                   .findFirst()
                   .map(Entry::getValue).orElse(null);
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

    private static OrganizationType toAffiliationWithPartOf(URI uri, boolean isNviAffiliation) {
        return isNviAffiliation ? generateNviOrganization(uri)
                   : generateOrganization(uri);
    }

    private static OrganizationType generateOrganization(URI uri) {
        return Organization.builder()
                   .withId(uri.toString())
                   .withPartOf(List.of(HARD_CODED_PART_OF))
                   .build();
    }

    private static OrganizationType generateNviOrganization(URI uri) {
        return NviOrganization.builder()
                   .withId(uri.toString())
                   .withPartOf(List.of(HARD_CODED_PART_OF))
                   .build();
    }
}
