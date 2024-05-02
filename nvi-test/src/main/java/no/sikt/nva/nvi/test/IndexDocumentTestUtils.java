package no.sikt.nva.nvi.test;

import static java.util.Collections.emptySet;
import static no.sikt.nva.nvi.test.ExpandedResourceGenerator.EN_FIELD;
import static no.sikt.nva.nvi.test.ExpandedResourceGenerator.HARDCODED_ENGLISH_LABEL;
import static no.sikt.nva.nvi.test.ExpandedResourceGenerator.HARDCODED_NORWEGIAN_LABEL;
import static no.sikt.nva.nvi.test.ExpandedResourceGenerator.NB_FIELD;
import static no.sikt.nva.nvi.test.ExpandedResourceGenerator.extractAffiliations;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ArrayNode;
import java.net.URI;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;
import no.sikt.nva.nvi.common.service.model.Approval;
import no.sikt.nva.nvi.common.service.model.Candidate;
import no.sikt.nva.nvi.common.service.model.InstitutionPoints.CreatorAffiliationPoints;
import no.sikt.nva.nvi.common.service.model.PublicationDetails.Creator;
import no.sikt.nva.nvi.common.utils.JsonUtils;
import no.sikt.nva.nvi.index.model.document.ApprovalStatus;
import no.sikt.nva.nvi.index.model.document.Contributor;
import no.sikt.nva.nvi.index.model.document.ContributorType;
import no.sikt.nva.nvi.index.model.document.InstitutionPoints;
import no.sikt.nva.nvi.index.model.document.NviContributor;
import no.sikt.nva.nvi.index.model.document.NviOrganization;
import no.sikt.nva.nvi.index.model.document.Organization;
import no.sikt.nva.nvi.index.model.document.OrganizationType;
import no.sikt.nva.nvi.index.model.document.PublicationDate;
import no.sikt.nva.nvi.index.model.document.PublicationDetails;
import nva.commons.core.paths.UnixPath;

public final class IndexDocumentTestUtils {

    public static final String HARD_CODED_TOP_LEVEL_ORG = "hardCodedPartOf";
    public static final URI HARD_CODED_PART_OF = URI.create(
        "https://example.org/organization/" + HARD_CODED_TOP_LEVEL_ORG);
    public static final URI NVI_CONTEXT = URI.create("https://bibsysdev.github.io/src/nvi-context.json");
    public static final String NVI_CANDIDATES_FOLDER = "nvi-candidates";
    public static final String GZIP_ENDING = ".gz";

    private IndexDocumentTestUtils() {
    }

    public static UnixPath createPath(Candidate candidate) {
        return UnixPath.of(NVI_CANDIDATES_FOLDER).addChild(candidate.getIdentifier().toString() + GZIP_ENDING);
    }

    public static List<no.sikt.nva.nvi.index.model.document.Approval> expandApprovals(Candidate candidate) {
        return candidate.getApprovals()
                   .values()
                   .stream()
                   .map(approval -> toApproval(approval, candidate))
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

    private static no.sikt.nva.nvi.index.model.document.Approval toApproval(Approval approval, Candidate candidate) {
        var assignee = approval.getAssignee();
        return no.sikt.nva.nvi.index.model.document.Approval.builder()
                   .withInstitutionId(approval.getInstitutionId())
                   .withApprovalStatus(getApprovalStatus(approval))
                   .withAssignee(Objects.nonNull(assignee) ? assignee.value() : null)
                   .withPoints(getInstitutionPoints(approval, candidate))
                   .withInvolvedOrganizations(extractInvolvedOrganizations(approval, candidate))
                   .withLabels(Map.of(EN_FIELD, HARDCODED_ENGLISH_LABEL, NB_FIELD,
                                      HARDCODED_NORWEGIAN_LABEL))
                   .build();
    }

    private static InstitutionPoints getInstitutionPoints(Approval approval, Candidate candidate) {
        return candidate.getInstitutionPoints(approval.getInstitutionId())
                   .map(InstitutionPoints::from)
                   .orElse(null);
    }

    private static Set<URI> extractInvolvedOrganizations(Approval approval, Candidate candidate) {
        var creatorAffiliations = candidate.getInstitutionPoints(approval.getInstitutionId())
                                      .map(
                                          no.sikt.nva.nvi.common.service.model.InstitutionPoints::creatorAffiliationPoints)
                                      .map(IndexDocumentTestUtils::getAffiliationsWithPoints)
                                      .orElse(emptySet());
        creatorAffiliations.add(approval.getInstitutionId());
        return creatorAffiliations;
    }

    private static Set<URI> getAffiliationsWithPoints(List<CreatorAffiliationPoints> creatorAffiliationPoints) {
        return creatorAffiliationPoints.stream()
                   .map(CreatorAffiliationPoints::affiliationId)
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
        return NviOrganization.builder()
                   .withId(id)
                   .withPartOf(List.of(HARD_CODED_TOP_LEVEL_ORG))
                   .build();
    }
}
