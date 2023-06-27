package no.sikt.nva.nvi.index.utils;

import static java.util.Objects.isNull;
import static no.sikt.nva.nvi.index.utils.ResourceJsonConstants.FIELD_ID;
import static no.sikt.nva.nvi.index.utils.ResourceJsonConstants.FIELD_IDENTITY;
import static no.sikt.nva.nvi.index.utils.ResourceJsonConstants.FIELD_LABELS;
import static no.sikt.nva.nvi.index.utils.ResourceJsonConstants.FIELD_NAME;
import static no.sikt.nva.nvi.index.utils.ResourceJsonConstants.FIELD_ORCID;
import static no.sikt.nva.nvi.index.utils.ResourceJsonConstants.FIELD_PUBLICATION_DATE_DAY;
import static no.sikt.nva.nvi.index.utils.ResourceJsonConstants.FIELD_PUBLICATION_DATE_MONTH;
import static no.sikt.nva.nvi.index.utils.ResourceJsonConstants.FIELD_PUBLICATION_DATE_YEAR;
import static no.sikt.nva.nvi.index.utils.ResourceJsonConstants.JSON_PTR_AFFILIATIONS;
import static no.sikt.nva.nvi.index.utils.ResourceJsonConstants.JSON_PTR_CONTRIBUTOR;
import static no.sikt.nva.nvi.index.utils.ResourceJsonConstants.JSON_PTR_ID;
import static no.sikt.nva.nvi.index.utils.ResourceJsonConstants.JSON_PTR_INSTANCE_TYPE;
import static no.sikt.nva.nvi.index.utils.ResourceJsonConstants.JSON_PTR_MAIN_TITLE;
import static no.sikt.nva.nvi.index.utils.ResourceJsonConstants.JSON_PTR_PUBLICATION_DATE;
import static no.sikt.nva.nvi.index.utils.ResourceJsonConstants.JSON_PTR_PUBLICATION_DATE_YEAR;
import static no.unit.nva.commons.json.JsonUtils.dtoObjectMapper;
import static nva.commons.core.attempt.Try.attempt;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import java.net.URI;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Stream;
import java.util.stream.StreamSupport;
import no.sikt.nva.nvi.index.ApprovalStatus;
import no.sikt.nva.nvi.index.model.Affiliation;
import no.sikt.nva.nvi.index.model.Contexts;
import no.sikt.nva.nvi.index.model.Contributor;
import no.sikt.nva.nvi.index.model.NviCandidate;
import no.sikt.nva.nvi.index.model.NviCandidateIndexDocument;
import no.sikt.nva.nvi.index.model.Publication;
import nva.commons.core.paths.UriWrapper;

public final class NviCandidateIndexDocumentGenerator {

    private static final String TYPE_NVI_CANDIDATE = "NviCandidate";

    private NviCandidateIndexDocumentGenerator() {
    }

    public static NviCandidateIndexDocument generateNviCandidateIndexDocument(String resource, NviCandidate candidate) {
        return constructNviCandidateIndexDocument(candidate,
                                                  attempt(() -> dtoObjectMapper.readTree(
                                                      resource)).orElseThrow());
    }

    private static NviCandidateIndexDocument constructNviCandidateIndexDocument(NviCandidate candidate,
                                                                                JsonNode resource) {
        return new NviCandidateIndexDocument(URI.create(Contexts.NVI_CONTEXT),
                                             extractPublicationIdentifier(resource),
                                             extractYear(resource),
                                             TYPE_NVI_CANDIDATE,
                                             extractPublication(resource),
                                             constructAffiliations(
                                                 resource, candidate));
    }

    private static List<Affiliation> constructAffiliations(JsonNode resource, NviCandidate candidate) {
        return candidate.affiliationApprovals().stream()
                   .map(id -> expandAffiliation(resource, id))
                   .toList();
    }

    private static Affiliation expandAffiliation(JsonNode resource, String id) {
        return getJsonNodeStream(resource, JSON_PTR_CONTRIBUTOR)
                   .flatMap(contributor -> getJsonNodeStream(contributor, JSON_PTR_AFFILIATIONS))
                   .filter(affiliation -> Objects.nonNull(affiliation.get(FIELD_ID)))
                   .filter(affiliation -> affiliation.get(FIELD_ID).textValue().equals(id))
                   .findFirst()
                   .map(NviCandidateIndexDocumentGenerator::createAffiliation)
                   .orElse(null);
    }

    private static Affiliation createAffiliation(JsonNode affiliation) {
        return new Affiliation(affiliation.get(FIELD_ID).textValue(),
                               convertToMap(affiliation.get(FIELD_LABELS)),
                               ApprovalStatus.PENDING.getValue());
    }

    private static Map<String, String> convertToMap(JsonNode node) {
        return dtoObjectMapper.convertValue(node, new TypeReference<>() {
        });
    }

    private static String extractPublicationId(JsonNode resource) {
        return resource.at(JSON_PTR_ID).textValue();
    }

    private static Stream<JsonNode> getJsonNodeStream(JsonNode jsonNode, String jsonPtr) {
        return StreamSupport.stream(jsonNode.at(jsonPtr).spliterator(), false);
    }

    private static String extractPublicationIdentifier(JsonNode resource) {
        return UriWrapper.fromUri(extractPublicationId(resource)).getPath().getLastPathElement();
    }

    private static Publication extractPublication(JsonNode resource) {
        return new Publication(extractPublicationId(resource), extractInstanceType(resource),
                               extractMainTitle(resource), extractPublicationDate(resource),
                               extractContributors(resource));
    }

    private static List<Contributor> extractContributors(JsonNode resource) {
        return getJsonNodeStream(resource, JSON_PTR_CONTRIBUTOR)
                   .map(contributor -> contributor.get(FIELD_IDENTITY))
                   .map(NviCandidateIndexDocumentGenerator::createContributor)
                   .toList();
    }

    private static Contributor createContributor(JsonNode identity) {
        return new Contributor(identity.get(FIELD_ID).textValue(),
                               identity.get(FIELD_NAME).textValue(),
                               identity.get(FIELD_ORCID).textValue());
    }

    private static String extractPublicationDate(JsonNode resource) {
        var publicationDate = resource.at(JSON_PTR_PUBLICATION_DATE);
        return formatPublicationDate(publicationDate);
    }

    private static String extractMainTitle(JsonNode resource) {
        return resource.at(JSON_PTR_MAIN_TITLE).textValue();
    }

    private static String extractInstanceType(JsonNode resource) {
        return resource.at(JSON_PTR_INSTANCE_TYPE).textValue();
    }

    private static String extractYear(JsonNode resource) {
        return resource.at(JSON_PTR_PUBLICATION_DATE_YEAR).textValue();
    }

    private static String formatPublicationDate(JsonNode publicationDateNode) {
        var year = publicationDateNode.get(FIELD_PUBLICATION_DATE_YEAR);
        var month = publicationDateNode.get(FIELD_PUBLICATION_DATE_MONTH);
        var day = publicationDateNode.get(FIELD_PUBLICATION_DATE_DAY);

        if (isNull(month) || isNull(day)) {
            return year.textValue();
        }

        return LocalDate.of(year.asInt(), month.asInt(), day.asInt()).toString();
    }
}
