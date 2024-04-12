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
import no.sikt.nva.nvi.common.service.model.Approval;
import no.sikt.nva.nvi.common.service.model.Candidate;
import no.sikt.nva.nvi.common.service.model.PublicationDetails.Creator;
import no.sikt.nva.nvi.common.service.model.Username;
import no.sikt.nva.nvi.index.model.ApprovalStatus;
import no.sikt.nva.nvi.index.model.Contributor;
import no.sikt.nva.nvi.index.model.ContributorType;
import no.sikt.nva.nvi.index.model.InstitutionPoints;
import no.sikt.nva.nvi.index.model.NviCandidateIndexDocument;
import no.sikt.nva.nvi.index.model.NviContributor;
import no.sikt.nva.nvi.index.model.NviOrganization;
import no.sikt.nva.nvi.index.model.Organization;
import no.sikt.nva.nvi.index.model.OrganizationType;
import no.sikt.nva.nvi.index.model.PublicationDate;
import no.sikt.nva.nvi.index.model.PublicationDetails;
import no.sikt.nva.nvi.index.model.ReportingPeriod;
import no.unit.nva.auth.uriretriever.UriRetriever;
import nva.commons.core.paths.UriWrapper;
import org.apache.jena.rdf.model.Model;
import org.apache.jena.rdf.model.NodeIterator;
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
        var approvals = createApprovals(expandedResource, candidate);
        return buildCandidate(expandedResource, candidate, approvals);
    }

    private static no.sikt.nva.nvi.common.model.Organization toOrganization(String response) {
        return attempt(
            () -> dtoObjectMapper.readValue(response, no.sikt.nva.nvi.common.model.Organization.class)).orElseThrow();
    }

    private static InstitutionPoints getInstitutionPoints(Approval approval, Candidate candidate) {
        return InstitutionPoints.from(candidate.getInstitutionPoints(approval.getInstitutionId()));
    }

    private static NviOrganization buildNviOrganization(String id, List<String> rdfNodes) {
        var partOfIdentifiers = rdfNodes.stream().map(node -> UriWrapper.fromUri(node).getLastPathElement());
        var partOfList = Stream.concat(rdfNodes.stream(), partOfIdentifiers).toList();
        return NviOrganization.builder()
                   .withId(id)
                   .withPartOf(partOfList)
                   .build();
    }

    private static NodeIterator listPropertyPartOfObjects(Model model) {
        return model.listObjectsOfProperty(model.createProperty(PART_OF_PROPERTY));
    }

    private static List<String> toListOfRdfNodes(NodeIterator nodeIterator) {
        return nodeIterator.toList().stream().map(RDFNode::toString).toList();
    }

    private NviCandidateIndexDocument buildCandidate(JsonNode resource, Candidate candidate,
                                                     List<no.sikt.nva.nvi.index.model.Approval> approvals) {
        return NviCandidateIndexDocument.builder()
                   .withId(candidate.getId())
                   .withContext(Candidate.getContextUri())
                   .withIsApplicable(candidate.isApplicable())
                   .withIdentifier(candidate.getIdentifier())
                   .withReportingPeriod(new ReportingPeriod(candidate.getPeriod().year()))
                   .withReported(candidate.isReported())
                   .withApprovals(approvals)
                   .withPublicationDetails(extractPublicationDetails(resource, candidate))
                   .withNumberOfApprovals(approvals.size())
                   .withPoints(candidate.getTotalPoints())
                   .withPublicationTypeChannelLevelPoints(candidate.getBasePoints())
                   .withGlobalApprovalStatus(candidate.getGlobalApprovalStatus())
                   .withCreatorShareCount(candidate.getCreatorShareCount())
                   .withInternationalCollaborationFactor(candidate.getCollaborationFactor())
                   .withModifiedDate(candidate.getModifiedDate().toString())
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

    private no.sikt.nva.nvi.common.model.Organization fetchOrganization(URI institutionId) {
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

    private List<no.sikt.nva.nvi.common.model.Organization> extractTopLevelOrganizations(JsonNode resource) {
        var topLevelOrganizations = resource.at("/topLevelOrganizations");
        return topLevelOrganizations.isMissingNode()
                   ? Collections.emptyList()
                   : mapToOrganizations((ArrayNode) topLevelOrganizations);
    }

    private List<no.sikt.nva.nvi.common.model.Organization> mapToOrganizations(ArrayNode topLevelOrgs) {
        return streamNode(topLevelOrgs).map(this::createOrganization).toList();
    }

    private no.sikt.nva.nvi.common.model.Organization createOrganization(JsonNode jsonNode) {
        return attempt(() -> dtoObjectMapper.readValue(jsonNode.toString(),
                                                       no.sikt.nva.nvi.common.model.Organization.class)).orElseThrow();
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

    private List<ContributorType> expandContributors(JsonNode resource, Candidate candidate) {
        return getJsonNodeStream(resource, JSON_PTR_CONTRIBUTOR).map(
            contributor -> createContributor(contributor, candidate)).toList();
    }

    private Optional<Creator> getNviCreatorIfPresent(JsonNode contributor, Candidate candidate) {
        return candidate.getPublicationDetails()
                   .creators()
                   .stream()
                   .filter(creator -> creator.id().toString().equals(extractId(contributor.at(JSON_PTR_IDENTITY))))
                   .findFirst();
    }

    private ContributorType createContributor(JsonNode contributor, Candidate candidate) {
        var identity = contributor.at(JSON_PTR_IDENTITY);
        return getNviCreatorIfPresent(contributor, candidate)
                   .map(value -> generateNviContributor(contributor, candidate, identity))
                   .orElseGet(() -> generateContributor(contributor, candidate, identity));
    }

    private ContributorType generateContributor(JsonNode contributor, Candidate candidate, JsonNode identity) {
        return Contributor.builder()
                   .withId(extractId(identity))
                   .withName(extractJsonNodeTextValue(identity, JSON_PTR_NAME))
                   .withOrcid(extractJsonNodeTextValue(identity, JSON_PTR_ORCID))
                   .withRole(extractRoleType(contributor))
                   .withAffiliations(expandAffiliations(contributor, candidate))
                   .build();
    }

    private ContributorType generateNviContributor(JsonNode contributor, Candidate candidate, JsonNode identity) {
        return NviContributor.builder()
                   .withId(extractId(identity))
                   .withName(extractJsonNodeTextValue(identity, JSON_PTR_NAME))
                   .withOrcid(extractJsonNodeTextValue(identity, JSON_PTR_ORCID))
                   .withRole(extractRoleType(contributor))
                   .withAffiliations(expandAffiliations(contributor, candidate))
                   .build();
    }

    private List<OrganizationType> expandAffiliations(JsonNode contributor, Candidate candidate) {
        return streamNode(contributor.at(JSON_PTR_AFFILIATIONS))
                   .map(affiliationNode -> expandAffiliation(affiliationNode, contributor, candidate))
                   .filter(Objects::nonNull)
                   .toList();
    }

    private OrganizationType expandAffiliation(JsonNode affiliation, JsonNode contributor, Candidate candidate) {
        var id = extractJsonNodeTextValue(affiliation, JSON_PTR_ID);

        if (isNull(id)) {
            LOGGER.info("Skipping expansion of affiliation because of missing institutionId: {}", affiliation);
            return null;
        }

        return isNviAffiliation(affiliation, contributor, candidate)
                   ? generateAffiliationWithPartOf(id)
                   : Organization.builder().withId(id).build();
    }

    private boolean isNviAffiliation(JsonNode affiliation, JsonNode contributor, Candidate candidate) {
        var nviCreator = getNviCreatorIfPresent(contributor, candidate);
        return nviCreator.isPresent() && isNviAffiliation(nviCreator.get(), affiliation);
    }

    private boolean isNviAffiliation(Creator creator, JsonNode affiliationNode) {
        var affiliationId = extractJsonNodeTextValue(affiliationNode, JSON_PTR_ID);
        if (isNull(affiliationId)) {
            return false;
        }
        return creator.affiliations().stream().anyMatch(affiliation -> affiliation.toString().equals(affiliationId));
    }

    private NviOrganization generateAffiliationWithPartOf(String id) {
        return attempt(() -> getRawContentFromUriCached(id)).map(Optional::get)
                   .map(str -> createModel(dtoObjectMapper.readTree(str)))
                   .map(NviCandidateIndexDocumentGenerator::listPropertyPartOfObjects)
                   .map(NviCandidateIndexDocumentGenerator::toListOfRdfNodes)
                   .map(result -> buildNviOrganization(id, result))
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
