package no.sikt.nva.nvi.index.utils;

import static java.util.Objects.isNull;
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
import java.net.URI;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import java.util.stream.StreamSupport;
import no.sikt.nva.nvi.common.db.ApprovalStatusDao.DbApprovalStatus;
import no.sikt.nva.nvi.common.db.model.Username;
import no.sikt.nva.nvi.common.service.model.Approval;
import no.sikt.nva.nvi.common.service.model.Candidate;
import no.sikt.nva.nvi.common.utils.JsonPointers;
import no.sikt.nva.nvi.index.model.Affiliation;
import no.sikt.nva.nvi.index.model.ApprovalStatus;
import no.sikt.nva.nvi.index.model.Contexts;
import no.sikt.nva.nvi.index.model.Contributor;
import no.sikt.nva.nvi.index.model.ExpandedResource;
import no.sikt.nva.nvi.index.model.ExpandedResource.Organization;
import no.sikt.nva.nvi.index.model.NviCandidateIndexDocument;
import no.sikt.nva.nvi.index.model.PublicationDate;
import no.sikt.nva.nvi.index.model.PublicationDetails;
import no.unit.nva.auth.uriretriever.AuthorizedBackendUriRetriever;
import nva.commons.core.attempt.Failure;
import org.apache.jena.rdf.model.RDFNode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public final class NviCandidateIndexDocumentGenerator {

    private static final Logger LOGGER = LoggerFactory.getLogger(NviCandidateIndexDocumentGenerator.class);
    private static final String APPLICATION_JSON = "application/json";
    private final AuthorizedBackendUriRetriever uriRetriever;
    private final Map<String, String> temporaryCache = new HashMap<>();

    public NviCandidateIndexDocumentGenerator(AuthorizedBackendUriRetriever uriRetriever) {
        this.uriRetriever = uriRetriever;
    }

    public NviCandidateIndexDocument generateDocument(String resource, Candidate candidate) {
        LOGGER.info("Starting generateDocument for {}", candidate.getIdentifier());
        return createNviCandidateIndexDocument(
            attempt(() -> dtoObjectMapper.readTree(resource)).map(root -> root.at(JsonPointers.JSON_PTR_BODY))
                .orElseThrow(), candidate);
    }

    private NviCandidateIndexDocument createNviCandidateIndexDocument(JsonNode resource, Candidate candidate) {
        var approvals = createApprovals(resource, toDbApprovals(candidate.getApprovals()));
        return new NviCandidateIndexDocument.Builder().withContext(URI.create(Contexts.NVI_CONTEXT))
                   .withIdentifier(candidate.getIdentifier().toString())
                   .withApprovals(approvals)
                   .withPublicationDetails(extractPublicationDetails(resource))
                   .withNumberOfApprovals(approvals.size())
                   .withPoints(candidate.getTotalPoints())
                   .build();
    }

    private List<DbApprovalStatus> toDbApprovals(Map<URI, Approval> approvals) {
        return approvals.values()
                   .stream()
                   .map(approval -> approval.approval().approvalStatus())
                   .collect(Collectors.toList());
    }

    private List<no.sikt.nva.nvi.index.model.Approval> createApprovals(JsonNode resource,
                                                                       List<DbApprovalStatus> approvals) {
        return approvals.stream().map(approval -> toApproval(resource, approval)).toList();
    }

    private Map<String, String> extractLabel(JsonNode resource, DbApprovalStatus approval) {
        return getTopLevelOrgs(resource.toString()).stream()
                   .filter(organization -> organization.hasAffiliation(approval.institutionId().toString()))
                   .findFirst()
                   .orElseThrow()
                   .getLabels();
    }

    private no.sikt.nva.nvi.index.model.Approval toApproval(JsonNode resource, DbApprovalStatus approval) {
        return new no.sikt.nva.nvi.index.model.Approval.Builder().withId(approval.institutionId().toString())
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
        return streamNode(contributor.at(JSON_PTR_AFFILIATIONS)).map(this::extractAffiliation)
                   .filter(Objects::nonNull)
                   .toList();
    }

    private Affiliation extractAffiliation(JsonNode affiliation) {
        var id = extractJsonNodeTextValue(affiliation, JSON_PTR_ID);

        if (isNull(id)) {
            LOGGER.info("Skipping extraction of affiliation because of missing id: {}", affiliation);
            return null;
        }

        return attempt(() -> getRawContentFromUriCached(id)).map(
                rawContent -> rawContent.orElseThrow(() -> logFailingAffiliationHttpRequest(id)))
                   .map(str -> createModel(dtoObjectMapper.readTree(str)))
                   .map(model -> model.listObjectsOfProperty(model.createProperty(PART_OF_PROPERTY)))
                   .map(nodeIterator -> nodeIterator.toList().stream().map(RDFNode::toString).toList())
                   .map(result -> new Affiliation.Builder().withId(id).withPartOf(result).build())
                   .orElseThrow(this::logAndReThrow);
    }

    private Optional<String> getRawContentFromUriCached(String id) {
        if (temporaryCache.containsKey(id)) {
            return Optional.of(temporaryCache.get(id));
        }

        var rawContentFromUri = getRawContentFromUri(id);

        rawContentFromUri.ifPresent(content -> this.temporaryCache.put(id, content));

        return rawContentFromUri;
    }

    private RuntimeException logFailingAffiliationHttpRequest(String id) {
        LOGGER.error("Failure while retrieving affiliation. Uri: {}", id);
        return new RuntimeException("Failure while retrieving affiliation");
    }

    private RuntimeException logAndReThrow(Failure<Affiliation> failure) {
        LOGGER.error("Failure while mapping affiliation: {}", failure.getException().getMessage());
        return new RuntimeException(failure.getException());
    }

    private Optional<String> getRawContentFromUri(String uri) {
        return this.uriRetriever.getRawContent(URI.create(uri), APPLICATION_JSON);
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
