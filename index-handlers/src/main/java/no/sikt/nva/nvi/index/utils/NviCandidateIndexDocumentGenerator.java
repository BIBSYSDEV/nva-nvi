package no.sikt.nva.nvi.index.utils;

import static java.util.Objects.nonNull;
import static no.sikt.nva.nvi.common.utils.JsonPointers.JSON_PTR_AFFILIATIONS;
import static no.sikt.nva.nvi.common.utils.JsonPointers.JSON_PTR_CONTRIBUTOR;
import static no.sikt.nva.nvi.common.utils.JsonPointers.JSON_PTR_DAY;
import static no.sikt.nva.nvi.common.utils.JsonPointers.JSON_PTR_ID;
import static no.sikt.nva.nvi.common.utils.JsonPointers.JSON_PTR_IDENTITY;
import static no.sikt.nva.nvi.common.utils.JsonPointers.JSON_PTR_INSTANCE_TYPE;
import static no.sikt.nva.nvi.common.utils.JsonPointers.JSON_PTR_LABELS;
import static no.sikt.nva.nvi.common.utils.JsonPointers.JSON_PTR_MAIN_TITLE;
import static no.sikt.nva.nvi.common.utils.JsonPointers.JSON_PTR_MONTH;
import static no.sikt.nva.nvi.common.utils.JsonPointers.JSON_PTR_NAME;
import static no.sikt.nva.nvi.common.utils.JsonPointers.JSON_PTR_ORCID;
import static no.sikt.nva.nvi.common.utils.JsonPointers.JSON_PTR_PUBLICATION_DATE;
import static no.sikt.nva.nvi.common.utils.JsonPointers.JSON_PTR_YEAR;
import static no.sikt.nva.nvi.common.utils.JsonUtils.extractJsonNodeTextValue;
import static no.sikt.nva.nvi.common.utils.JsonUtils.streamNode;
import static no.unit.nva.commons.json.JsonUtils.dtoObjectMapper;
import static nva.commons.core.attempt.Try.attempt;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import java.net.URI;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.stream.Stream;
import java.util.stream.StreamSupport;
import no.sikt.nva.nvi.common.db.Candidate;
import no.sikt.nva.nvi.common.model.business.DbApprovalStatus;
import no.sikt.nva.nvi.index.model.Approval;
import no.sikt.nva.nvi.index.model.ApprovalStatus;
import no.sikt.nva.nvi.index.model.Contexts;
import no.sikt.nva.nvi.index.model.Contributor;
import no.sikt.nva.nvi.index.model.NviCandidateIndexDocument;
import no.sikt.nva.nvi.index.model.PublicationDetails;

public final class NviCandidateIndexDocumentGenerator {


    private NviCandidateIndexDocumentGenerator() {
    }

    public static NviCandidateIndexDocument generateDocument(
        String resource, Candidate candidateWithIdentifier) {
        return createNviCandidateIndexDocument(attempt(() -> dtoObjectMapper.readTree(resource))
                                                   .map(root -> root.at("/body")).orElseThrow(),
                                               candidateWithIdentifier);
    }

    private static NviCandidateIndexDocument createNviCandidateIndexDocument(
        JsonNode resource, Candidate candidate) {
        var approvals = createApprovals(resource, candidate.approvalStatuses());
        return new NviCandidateIndexDocument.Builder()
                   .withContext(URI.create(Contexts.NVI_CONTEXT))
                   .withIdentifier(candidate.identifier().toString())
                   .withApprovals(approvals)
                   .withPublicationDetails(extractPublicationDetails(resource))
                   .withNumberOfApprovals(approvals.size())
                   .build();
    }

    private static List<Approval> createApprovals(
        JsonNode resource, List<DbApprovalStatus> approvals) {
        return approvals.stream()
                   .map(approval -> expandApprovals(resource, toApproval(approval)))
                   .filter(Objects::nonNull)
                   .toList();
    }

    private static Approval toApproval(DbApprovalStatus approval) {
        return new Approval.Builder()
                   .withId(approval.institutionId().toString())
                   .withLabels(Map.of())
                   .withApprovalStatus(ApprovalStatus.fromValue(approval.status().getValue()))
                   .build();
    }

    private static Approval expandApprovals(JsonNode resource,
                                            Approval approval) {
        return getJsonNodeStream(resource, JSON_PTR_CONTRIBUTOR)
                   .flatMap(contributor -> getJsonNodeStream(contributor, JSON_PTR_AFFILIATIONS))
                   .filter(affiliation -> nonNull(affiliation.at(JSON_PTR_ID)))
                   .filter(affiliation -> extractId(affiliation).equals(approval.id()))
                   .findFirst()
                   .map(node -> createApproval(node, approval))
                   .orElse(null);
    }

    private static Approval createApproval(JsonNode affiliation,
                                           Approval approval) {
        return new Approval.Builder()
                   .withId(extractId(affiliation))
                   .withLabels(convertToMap(affiliation.at(JSON_PTR_LABELS)))
                   .withApprovalStatus(approval.approvalStatus())
                   .build();
    }

    private static PublicationDetails extractPublicationDetails(JsonNode resource) {
        return new PublicationDetails(extractId(resource), extractInstanceType(resource), extractMainTitle(resource),
                                      extractPublicationDate(resource), extractContributors(resource));
    }

    private static List<Contributor> extractContributors(JsonNode resource) {
        return getJsonNodeStream(resource, JSON_PTR_CONTRIBUTOR)
                   .map(NviCandidateIndexDocumentGenerator::createContributor)
                   .toList();
    }

    private static Contributor createContributor(JsonNode contributor) {
        var identity = contributor.at(JSON_PTR_IDENTITY);
        return new Contributor.Builder()
                   .withId(extractId(identity))
                   .withName(extractJsonNodeTextValue(identity, JSON_PTR_NAME))
                   .withOrcid(extractJsonNodeTextValue(identity, JSON_PTR_ORCID))
                   .withAffiliations(extractAffiliations(contributor))
                   .build();
    }

    private static List<String> extractAffiliations(JsonNode contributor) {
        return streamNode(contributor.at(JSON_PTR_AFFILIATIONS))
                   .map(affiliation -> extractJsonNodeTextValue(affiliation, JSON_PTR_ID))
                   .toList();
    }

    private static String extractId(JsonNode resource) {
        return extractJsonNodeTextValue(resource, JSON_PTR_ID);
    }

    private static String extractPublicationDate(JsonNode resource) {
        return formatPublicationDate(resource.at(JSON_PTR_PUBLICATION_DATE));
    }

    private static String extractMainTitle(JsonNode resource) {
        return extractJsonNodeTextValue(resource, JSON_PTR_MAIN_TITLE);
    }

    private static String extractInstanceType(JsonNode resource) {
        return extractJsonNodeTextValue(resource, JSON_PTR_INSTANCE_TYPE);
    }

    private static Stream<JsonNode> getJsonNodeStream(JsonNode jsonNode, String jsonPtr) {
        return StreamSupport.stream(jsonNode.at(jsonPtr).spliterator(), false);
    }

    private static Map<String, String> convertToMap(JsonNode node) {
        return dtoObjectMapper.convertValue(node, new TypeReference<>() {
        });
    }

    private static String formatPublicationDate(JsonNode publicationDateNode) {
        var year = publicationDateNode.at(JSON_PTR_YEAR);
        var month = publicationDateNode.at(JSON_PTR_MONTH);
        var day = publicationDateNode.at(JSON_PTR_DAY);

        return Optional.of(LocalDate.of(year.asInt(), month.asInt(), day.asInt()).toString())
                   .orElse(year.textValue());
    }
}
