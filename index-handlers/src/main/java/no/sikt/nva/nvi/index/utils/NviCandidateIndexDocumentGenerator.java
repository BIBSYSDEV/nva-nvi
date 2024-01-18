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
import com.fasterxml.jackson.databind.node.ArrayNode;
import java.math.BigDecimal;
import java.net.URI;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.stream.Stream;
import java.util.stream.StreamSupport;
import no.sikt.nva.nvi.common.client.OrganizationRetriever;
import no.sikt.nva.nvi.common.model.Organization;
import no.sikt.nva.nvi.common.service.model.Approval;
import no.sikt.nva.nvi.common.service.model.Candidate;
import no.sikt.nva.nvi.common.service.model.Username;
import no.sikt.nva.nvi.index.model.Affiliation;
import no.sikt.nva.nvi.index.model.ApprovalStatus;
import no.sikt.nva.nvi.index.model.Contributor;
import no.sikt.nva.nvi.index.model.NviCandidateIndexDocument;
import no.sikt.nva.nvi.index.model.PublicationDate;
import no.sikt.nva.nvi.index.model.PublicationDetails;
import no.unit.nva.auth.uriretriever.UriRetriever;
import org.apache.jena.rdf.model.RDFNode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public final class NviCandidateIndexDocumentGenerator {

    private static final Logger LOGGER = LoggerFactory.getLogger(NviCandidateIndexDocumentGenerator.class);
    private final OrganizationRetriever organizationRetriever;
    private final Map<String, String> temporaryCache = new HashMap<>();

    public NviCandidateIndexDocumentGenerator(UriRetriever uriRetriever) {
        this.organizationRetriever = new OrganizationRetriever(uriRetriever);
    }

    public NviCandidateIndexDocument generateDocument(JsonNode expandedResource, Candidate candidate) {
        return createNviCandidateIndexDocument(expandedResource, candidate);
    }

    private static Organization toOrganization(String response) {
        return attempt(() -> dtoObjectMapper.readValue(response, Organization.class)).orElseThrow();
    }

    private static BigDecimal getInstitutionPoints(Approval approval, Candidate candidate) {
        return candidate.getInstitutionPoints().get(approval.getInstitutionId());
    }

    private NviCandidateIndexDocument createNviCandidateIndexDocument(JsonNode resource, Candidate candidate) {
        var approvals = createApprovals(resource, candidate);
        return new NviCandidateIndexDocument.Builder()
                   .withContext(Candidate.getContextUri())
                   .withIdentifier(candidate.getIdentifier())
                   .withApprovals(approvals)
                   .withPublicationDetails(extractPublicationDetails(resource, candidate))
                   .withNumberOfApprovals(approvals.size())
                   .withPoints(candidate.getTotalPoints())
                   .build();
    }

    private Stream<Approval> streamValues(Map<URI, Approval> approvals) {
        return approvals.values().stream();
    }

    private List<no.sikt.nva.nvi.index.model.Approval> createApprovals(JsonNode resource,
                                                                       Candidate candidate) {
        return streamValues(candidate.getApprovals()).map(approval -> toApproval(resource, approval, candidate))
                   .toList();
    }

    private Map<String, String> extractLabels(JsonNode resource, Approval approval) {
        return extractTopLevelOrganizations(resource).stream()
                   .filter(organization -> organization.id().equals(approval.getInstitutionId()))
                   .findFirst()
                   .orElse(fetchOrganization(approval.getInstitutionId()))
                   .labels();
    }

    private Organization fetchOrganization(URI institutionId) {
        return getRawContentFromUriCached(institutionId.toString())
                   .map(NviCandidateIndexDocumentGenerator::toOrganization)
                   .orElseThrow(() -> logFailingAffiliationHttpRequest(institutionId.toString()));
    }

    private no.sikt.nva.nvi.index.model.Approval toApproval(JsonNode resource, Approval approval, Candidate candidate) {
        return no.sikt.nva.nvi.index.model.Approval.builder()
                   .withInstitutionId(approval.getInstitutionId().toString())
                   .withLabels(extractLabels(resource, approval))
                   .withApprovalStatus(ApprovalStatus.fromValue(approval.getStatus().getValue()))
                   .withPoints(getInstitutionPoints(approval, candidate))
                   .withAssignee(extractAssignee(approval))
                   .build();
    }

    private String extractAssignee(Approval approval) {
        return Optional.of(approval).map(Approval::getAssignee).map(Username::value).orElse(null);
    }

    private List<Organization> extractTopLevelOrganizations(JsonNode resource) {
        var topLevelOrganizations = resource.at("/topLevelOrganizations");
        return topLevelOrganizations.isMissingNode()
                   ? Collections.emptyList()
                   : mapToOrganizations((ArrayNode) topLevelOrganizations);
    }

    private List<Organization> mapToOrganizations(ArrayNode topLevelOrgs) {
        return streamNode(topLevelOrgs).map(this::createOrganization).toList();
    }

    private Organization createOrganization(JsonNode jsonNode) {
        return attempt(() -> dtoObjectMapper.readValue(jsonNode.toString(), Organization.class)).orElseThrow();
    }

    private PublicationDetails extractPublicationDetails(JsonNode resource, Candidate candidate) {
        return PublicationDetails.builder()
                   .withId(extractId(resource))
                   .withContributors(expandContributors(resource, candidate))
                   .withType(extractInstanceType(resource))
                   .withPublicationDate(extractPublicationDate(resource))
                   .withTitle(extractMainTitle(resource))
                   .build();
    }

    private List<Contributor> expandContributors(JsonNode resource, Candidate candidate) {
        return getJsonNodeStream(resource, JSON_PTR_CONTRIBUTOR).map(
            contributor -> createContributor(contributor, isNviCreator(contributor, candidate))).toList();
    }

    private boolean isNviCreator(JsonNode contributor, Candidate candidate) {
        return candidate.getPublicationDetails()
                   .creators()
                   .stream()
                   .anyMatch(creator -> creator.id().toString().equals(extractId(contributor.at(JSON_PTR_IDENTITY))));
    }

    private Contributor createContributor(JsonNode contributor, boolean isNviCreator) {
        var identity = contributor.at(JSON_PTR_IDENTITY);
        return new Contributor.Builder().withId(extractId(identity))
                   .withName(extractJsonNodeTextValue(identity, JSON_PTR_NAME))
                   .withOrcid(extractJsonNodeTextValue(identity, JSON_PTR_ORCID))
                   .withRole(extractRoleType(contributor))
                   .withAffiliations(expandAffiliations(contributor, isNviCreator))
                   .build();
    }

    private List<Affiliation> expandAffiliations(JsonNode contributor, boolean expandPartOf) {
        return streamNode(contributor.at(JSON_PTR_AFFILIATIONS))
                   .map(affiliationNode -> expandAffiliation(affiliationNode, expandPartOf))
                   .filter(Objects::nonNull)
                   .toList();
    }

    private Affiliation expandAffiliation(JsonNode affiliation, boolean expandPartOf) {
        var id = extractJsonNodeTextValue(affiliation, JSON_PTR_ID);

        if (isNull(id)) {
            LOGGER.info("Skipping extraction of affiliation because of missing institutionId: {}", affiliation);
            return null;
        }

        return expandPartOf ? generateAffiliationWithPartOf(id) : new Affiliation.Builder().withId(id).build();
    }

    private Affiliation generateAffiliationWithPartOf(String id) {
        return attempt(() -> getRawContentFromUriCached(id)).map(Optional::get)
                   .map(str -> createModel(dtoObjectMapper.readTree(str)))
                   .map(model -> model.listObjectsOfProperty(model.createProperty(PART_OF_PROPERTY)))
                   .map(nodeIterator -> nodeIterator.toList().stream().map(RDFNode::toString).toList())
                   .map(result -> new Affiliation.Builder().withId(id).withPartOf(result).build())
                   .orElseThrow();
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

    private Optional<String> getRawContentFromUri(String uri) {
        return attempt(() -> organizationRetriever.fetchOrganization(URI.create(uri)).asJsonString()).toOptional();
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
