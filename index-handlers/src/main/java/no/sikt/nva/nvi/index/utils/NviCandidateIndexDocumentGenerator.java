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
import static no.sikt.nva.nvi.common.utils.JsonPointers.JSON_PTR_PUBLICATION_DATE_YEAR;
import static no.sikt.nva.nvi.common.utils.JsonPointers.JSON_PTR_YEAR;
import static no.sikt.nva.nvi.common.utils.JsonUtils.extractJsonNodeTextValue;
import static no.unit.nva.commons.json.JsonUtils.dtoObjectMapper;
import static nva.commons.core.attempt.Try.attempt;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import java.net.URI;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;
import java.util.stream.StreamSupport;
import no.sikt.nva.nvi.index.model.Affiliation;
import no.sikt.nva.nvi.index.model.ApprovalStatus;
import no.sikt.nva.nvi.index.model.Contexts;
import no.sikt.nva.nvi.index.model.Contributor;
import no.sikt.nva.nvi.index.model.NviCandidateIndexDocument;
import no.sikt.nva.nvi.index.model.PublicationDetails;
import nva.commons.core.paths.UriWrapper;

public final class NviCandidateIndexDocumentGenerator {

    private static final String TYPE_NVI_CANDIDATE = "NviCandidate";

    private NviCandidateIndexDocumentGenerator() {
    }

    public static NviCandidateIndexDocument generateDocument(String resource, List<no.sikt.nva.nvi.common.model.business.ApprovalStatus> affiliationApprovals) {
        return createNviCandidateIndexDocument(attempt(() -> dtoObjectMapper.readTree(resource))
                                                   .map(root -> root.at("/body")).orElseThrow(),
                                               affiliationApprovals);
    }

    private static NviCandidateIndexDocument createNviCandidateIndexDocument(JsonNode resource,
                                                                             List<no.sikt.nva.nvi.common.model.business.ApprovalStatus> approvalAffiliations) {
        return new NviCandidateIndexDocument.Builder()
                   .withContext(URI.create(Contexts.NVI_CONTEXT))
                   .withIdentifier(extractPublicationIdentifier(resource))
                   .withType(TYPE_NVI_CANDIDATE)
                   .withAffiliations(createAffiliations(resource, approvalAffiliations))
                   .withPublicationDetails(extractPublication(resource))
                   .withYear(extractYear(resource))
                   .build();
    }

    private static List<Affiliation> createAffiliations(JsonNode resource, List<no.sikt.nva.nvi.common.model.business.ApprovalStatus> approvalAffiliations) {
        return approvalAffiliations.stream().map(id -> expandAffiliation(resource, id)).toList();
    }

    private static Affiliation expandAffiliation(JsonNode resource,
                                                 no.sikt.nva.nvi.common.model.business.ApprovalStatus approvalStatus) {
        return getJsonNodeStream(resource, JSON_PTR_CONTRIBUTOR)
                   .flatMap(contributor -> getJsonNodeStream(contributor, JSON_PTR_AFFILIATIONS))
                   .filter(affiliation -> nonNull(affiliation.at(JSON_PTR_ID)))
                   .filter(affiliation -> extractId(affiliation).equals(approvalStatus.institutionId().toString()))
                   .findFirst()
                   .map(NviCandidateIndexDocumentGenerator::createAffiliation)
                   .orElse(null);
    }

    private static Affiliation createAffiliation(JsonNode affiliation) {
        return new Affiliation(extractId(affiliation), convertToMap(affiliation.at(JSON_PTR_LABELS)),
                               ApprovalStatus.PENDING);
    }

    private static String extractPublicationIdentifier(JsonNode resource) {
        return attempt(() -> extractJsonNodeTextValue(resource, JSON_PTR_ID))
                   .map(UriWrapper::fromUri)
                   .map(UriWrapper::getLastPathElement)
                   .orElseThrow();
    }

    private static PublicationDetails extractPublication(JsonNode resource) {
        return new PublicationDetails(extractId(resource), extractInstanceType(resource), extractMainTitle(resource),
                                      extractPublicationDate(resource), extractContributors(resource));
    }

    private static List<Contributor> extractContributors(JsonNode resource) {
        return getJsonNodeStream(resource, JSON_PTR_CONTRIBUTOR)
                   .map(contributor -> contributor.at(JSON_PTR_IDENTITY))
                   .map(NviCandidateIndexDocumentGenerator::createContributor)
                   .toList();
    }

    private static Contributor createContributor(JsonNode identity) {
        return new Contributor(extractId(identity), extractJsonNodeTextValue(identity, JSON_PTR_NAME),
                               extractJsonNodeTextValue(identity, JSON_PTR_ORCID));
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

    private static String extractYear(JsonNode resource) {
        return extractJsonNodeTextValue(resource, JSON_PTR_PUBLICATION_DATE_YEAR);
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

        return attempt(() -> LocalDate.of(year.asInt(), month.asInt(), day.asInt())
                                      .toString()).orElse(failure -> year.textValue());
    }
}
