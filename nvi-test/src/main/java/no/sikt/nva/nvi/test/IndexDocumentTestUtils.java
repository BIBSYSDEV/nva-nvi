package no.sikt.nva.nvi.test;

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
import java.util.Map.Entry;
import java.util.Objects;
import no.sikt.nva.nvi.common.service.model.Approval;
import no.sikt.nva.nvi.common.service.model.Candidate;
import no.sikt.nva.nvi.common.utils.JsonUtils;
import no.sikt.nva.nvi.index.model.Affiliation;
import no.sikt.nva.nvi.index.model.ApprovalStatus;
import no.sikt.nva.nvi.index.model.Contributor;
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
                   .map(IndexDocumentTestUtils::toApproval)
                   .toList();
    }

    public static PublicationDetails expandPublicationDetails(Candidate candidate, JsonNode expandedResource) {
        return PublicationDetails.builder()
                   .withType(ExpandedResourceGenerator.extractType(expandedResource))
                   .withId(candidate.getPublicationDetails().publicationId().toString())
                   .withTitle(ExpandedResourceGenerator.extractTitle(expandedResource))
                   .withPublicationDate(mapToPublicationDate(candidate.getPublicationDetails().publicationDate()))
                   .withContributors(mapToContributors(ExpandedResourceGenerator.extractContributors(expandedResource)))
                   .build();
    }

    private static no.sikt.nva.nvi.index.model.Approval toApproval(Entry<URI, Approval> approvalEntry) {
        var assignee = approvalEntry.getValue().getAssignee();
        return no.sikt.nva.nvi.index.model.Approval.builder()
                   .withId(approvalEntry.getKey().toString())
                   .withApprovalStatus(ApprovalStatus.fromValue(approvalEntry.getValue().getStatus().getValue()))
                   .withAssignee(Objects.nonNull(assignee) ? assignee.value() : null)
                   .withLabels(Map.of(EN_FIELD, HARDCODED_ENGLISH_LABEL, NB_FIELD, HARDCODED_NORWEGIAN_LABEL))
                   .build();
    }

    private static PublicationDate mapToPublicationDate(
        no.sikt.nva.nvi.common.service.model.PublicationDetails.PublicationDate publicationDate) {
        return new PublicationDate(publicationDate.year(), publicationDate.month(), publicationDate.day());
    }

    private static List<Contributor> mapToContributors(ArrayNode contributorNodes) {
        return JsonUtils.streamNode(contributorNodes)
                   .map(IndexDocumentTestUtils::toContributor)
                   .toList();
    }

    private static Contributor toContributor(JsonNode contributorNode) {
        return Contributor.builder()
                   .withId(ExpandedResourceGenerator.extractId(contributorNode))
                   .withName(ExpandedResourceGenerator.extractName(contributorNode))
                   .withOrcid(ExpandedResourceGenerator.extractOrcid(contributorNode))
                   .withRole(ExpandedResourceGenerator.extractRole(contributorNode))
                   .withAffiliations(mapToAffiliations(extractAffiliations(contributorNode)))
                   .build();
    }

    private static List<Affiliation> mapToAffiliations(List<URI> uris) {
        return uris.stream().map(IndexDocumentTestUtils::toAffiliation).toList();
    }

    private static Affiliation toAffiliation(URI uri) {
        return Affiliation.builder()
                   .withId(uri.toString())
                   .withPartOf(List.of(HARD_CODED_PART_OF))
                   .build();
    }
}