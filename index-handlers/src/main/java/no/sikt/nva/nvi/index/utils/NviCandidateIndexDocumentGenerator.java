package no.sikt.nva.nvi.index.utils;

import static no.sikt.nva.nvi.common.utils.GraphUtils.PART_OF_PROPERTY;
import static no.sikt.nva.nvi.common.utils.GraphUtils.createModel;
import static no.sikt.nva.nvi.common.utils.JsonPointers.JSON_PTR_AFFILIATIONS;
import static no.sikt.nva.nvi.common.utils.JsonPointers.JSON_PTR_CONTRIBUTOR;
import static no.sikt.nva.nvi.common.utils.JsonPointers.JSON_PTR_DAY;
import static no.sikt.nva.nvi.common.utils.JsonPointers.JSON_PTR_ID;
import static no.sikt.nva.nvi.common.utils.JsonPointers.JSON_PTR_IDENTITY;
import static no.sikt.nva.nvi.common.utils.JsonPointers.JSON_PTR_INSTANCE_TYPE;
import static no.sikt.nva.nvi.common.utils.JsonPointers.JSON_PTR_MAIN_TITLE;
import static no.sikt.nva.nvi.common.utils.JsonPointers.JSON_PTR_MONTH;
import static no.sikt.nva.nvi.common.utils.JsonPointers.JSON_PTR_NAME;
import static no.sikt.nva.nvi.common.utils.JsonPointers.JSON_PTR_ORCID;
import static no.sikt.nva.nvi.common.utils.JsonPointers.JSON_PTR_PUBLICATION_DATE;
import static no.sikt.nva.nvi.common.utils.JsonPointers.JSON_PTR_ROLE_TYPE;
import static no.sikt.nva.nvi.common.utils.JsonPointers.JSON_PTR_YEAR;
import static no.sikt.nva.nvi.common.utils.JsonUtils.extractJsonNodeTextValue;
import static no.sikt.nva.nvi.common.utils.JsonUtils.streamNode;
import static no.unit.nva.commons.json.JsonUtils.dtoObjectMapper;
import static nva.commons.core.attempt.Try.attempt;
import com.fasterxml.jackson.databind.JsonNode;
import java.math.RoundingMode;
import java.net.URI;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import java.util.stream.StreamSupport;
import no.sikt.nva.nvi.common.db.ApprovalStatusDao.DbApprovalStatus;
import no.sikt.nva.nvi.common.db.model.Username;
import no.sikt.nva.nvi.common.service.ApprovalBO;
import no.sikt.nva.nvi.common.service.CandidateBO;
import no.sikt.nva.nvi.common.utils.JsonPointers;
import no.sikt.nva.nvi.index.model.Affiliation;
import no.sikt.nva.nvi.index.model.Approval;
import no.sikt.nva.nvi.index.model.ApprovalStatus;
import no.sikt.nva.nvi.index.model.Contexts;
import no.sikt.nva.nvi.index.model.Contributor;
import no.sikt.nva.nvi.index.model.ExpandedResource;
import no.sikt.nva.nvi.index.model.ExpandedResource.Organization;
import no.sikt.nva.nvi.index.model.NviCandidateIndexDocument;
import no.sikt.nva.nvi.index.model.PublicationDate;
import no.sikt.nva.nvi.index.model.PublicationDetails;
import no.unit.nva.auth.uriretriever.AuthorizedBackendUriRetriever;
import org.apache.jena.rdf.model.RDFNode;

public final class NviCandidateIndexDocumentGenerator {

    public static final String APPLICATION_JSON = "application/json";
    private static final int POINTS_SCALE = 4;
    private static final RoundingMode ROUNDING_MODE = RoundingMode.HALF_UP;
    private final AuthorizedBackendUriRetriever uriRetriever;

    public NviCandidateIndexDocumentGenerator(AuthorizedBackendUriRetriever uriRetriever) {
        this.uriRetriever = uriRetriever;
    }

    public NviCandidateIndexDocument generateDocument(String resource, CandidateBO candidate) {
        return createNviCandidateIndexDocument(
            attempt(() -> dtoObjectMapper.readTree(resource)).map(root -> root.at(JsonPointers.JSON_PTR_BODY))
                .orElseThrow(), candidate);
    }

    private NviCandidateIndexDocument createNviCandidateIndexDocument(JsonNode resource, CandidateBO candidate) {
        var approvals = createApprovals(resource, toDbApprovals(candidate.getApprovals()));
        return new NviCandidateIndexDocument.Builder().withContext(URI.create(Contexts.NVI_CONTEXT))
                   .withIdentifier(candidate.identifier().toString())
                   .withApprovals(approvals)
                   .withPublicationDetails(extractPublicationDetails(resource))
                   .withNumberOfApprovals(approvals.size())
                   .withPoints(candidate.getTotalPoints())
                   .build();
    }

    private List<DbApprovalStatus> toDbApprovals(Map<URI, ApprovalBO> approvals) {
        return approvals.values()
                   .stream()
                   .map(approvalBO -> approvalBO.approval().approvalStatus())
                   .collect(Collectors.toList());
    }

    private List<Approval> createApprovals(JsonNode resource, List<DbApprovalStatus> approvals) {
        return approvals.stream().map(approval -> toApproval(resource, approval)).toList();
    }

    private Map<String, String> extractLabel(JsonNode resource, DbApprovalStatus approval) {
        return getTopLevelOrgs(resource.toString()).stream()
                   .filter(organization -> organization.hasAffiliation(approval.institutionId().toString()))
                   .findFirst()
                   .orElseThrow()
                   .getLabels();
    }

    private Approval toApproval(JsonNode resource, DbApprovalStatus approval) {
        return new Approval.Builder().withId(approval.institutionId().toString())
                   .withLabels(extractLabel(resource, approval))
                   .withApprovalStatus(ApprovalStatus.fromValue(approval.status().getValue()))
                   .withAssignee(extractAssignee(approval))
                   .build();
    }

    private String extractAssignee(DbApprovalStatus approval) {
        return Optional.of(approval).map(DbApprovalStatus::assignee).map(Username::value).orElse(null);
    }

    private List<Organization> getTopLevelOrgs(String s) {
        return attempt(() -> dtoObjectMapper.readValue(s, ExpandedResource.class)).orElseThrow()
                   .getTopLevelOrganization();
    }

    private PublicationDetails extractPublicationDetails(JsonNode resource) {
        return PublicationDetails.builder()
                   .withId(extractId(resource))
                   .withContributors(extractContributors(resource))
                   .withType(extractInstanceType(resource))
                   .withPublicationDate(extractPublicationDate(resource))
                   .withTitle(extractMainTitle(resource))
                   .build();
    }

    private List<Contributor> extractContributors(JsonNode resource) {
        return getJsonNodeStream(resource, JSON_PTR_CONTRIBUTOR).map(this::createContributor).toList();
    }

    private Contributor createContributor(JsonNode contributor) {
        var identity = contributor.at(JSON_PTR_IDENTITY);
        return new Contributor.Builder().withId(extractId(identity))
                   .withName(extractJsonNodeTextValue(identity, JSON_PTR_NAME))
                   .withOrcid(extractJsonNodeTextValue(identity, JSON_PTR_ORCID))
                   .withRole(extractRoleType(contributor))
                   .withAffiliations(extractAffiliations(contributor))
                   .build();
    }

    private List<Affiliation> extractAffiliations(JsonNode contributor) {
        return streamNode(contributor.at(JSON_PTR_AFFILIATIONS)).map(this::extractAffiliation).toList();
    }

    private Affiliation extractAffiliation(JsonNode affiliation) {
        var id = extractJsonNodeTextValue(affiliation, JSON_PTR_ID);
        return attempt(() -> this.uriRetriever.getRawContent(URI.create(id), APPLICATION_JSON)).map(
                Optional::orElseThrow)
                   .map(str -> createModel(dtoObjectMapper.readTree(str)))
                   .map(model -> model.listObjectsOfProperty(model.createProperty(PART_OF_PROPERTY)))
                   .map(nodeIterator -> nodeIterator.toList().stream().map(RDFNode::toString).toList())
                   .map(result -> new Affiliation.Builder().withId(id).withPartOf(result).build())
                   .orElseThrow();
    }

    private String extractId(JsonNode resource) {
        return extractJsonNodeTextValue(resource, JSON_PTR_ID);
    }

    private String extractRoleType(JsonNode resource) {
        return extractJsonNodeTextValue(resource, JSON_PTR_ROLE_TYPE);
    }

    private PublicationDate extractPublicationDate(JsonNode resource) {
        return formatPublicationDate(resource.at(JSON_PTR_PUBLICATION_DATE));
    }

    private String extractMainTitle(JsonNode resource) {
        return extractJsonNodeTextValue(resource, JSON_PTR_MAIN_TITLE);
    }

    private String extractInstanceType(JsonNode resource) {
        return extractJsonNodeTextValue(resource, JSON_PTR_INSTANCE_TYPE);
    }

    private Stream<JsonNode> getJsonNodeStream(JsonNode jsonNode, String jsonPtr) {
        return StreamSupport.stream(jsonNode.at(jsonPtr).spliterator(), false);
    }

    private PublicationDate formatPublicationDate(JsonNode publicationDateNode) {
        return PublicationDate.builder()
                   .withYear(extractJsonNodeTextValue(publicationDateNode, JSON_PTR_YEAR))
                   .withMonth(extractJsonNodeTextValue(publicationDateNode, JSON_PTR_MONTH))
                   .withDay(extractJsonNodeTextValue(publicationDateNode, JSON_PTR_DAY))
                   .build();
    }
}
