package no.sikt.nva.nvi.index.utils;

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
import static no.sikt.nva.nvi.index.utils.ResourceJsonConstants.JSON_PTR_PUBLICATION_CHANNEL_ID;
import static no.sikt.nva.nvi.index.utils.ResourceJsonConstants.JSON_PTR_PUBLICATION_CHANNEL_LEVEL;
import static no.sikt.nva.nvi.index.utils.ResourceJsonConstants.JSON_PTR_PUBLICATION_CHANNEL_NAME;
import static no.sikt.nva.nvi.index.utils.ResourceJsonConstants.JSON_PTR_PUBLICATION_CHANNEL_TYPE;
import static no.sikt.nva.nvi.index.utils.ResourceJsonConstants.JSON_PTR_PUBLICATION_DATE;
import static no.sikt.nva.nvi.index.utils.ResourceJsonConstants.JSON_PTR_PUBLICATION_DATE_YEAR;
import static no.unit.nva.commons.json.JsonUtils.dtoObjectMapper;
import static nva.commons.core.attempt.Try.attempt;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Stream;
import java.util.stream.StreamSupport;
import no.sikt.nva.nvi.index.ApprovalStatus;
import no.sikt.nva.nvi.index.model.Affiliation;
import no.sikt.nva.nvi.index.model.Contributor;
import no.sikt.nva.nvi.index.model.NviCandidate;
import no.sikt.nva.nvi.index.model.NviCandidateIndexDocument;
import no.sikt.nva.nvi.index.model.Publication;
import no.sikt.nva.nvi.index.model.PublicationChannel;
import nva.commons.core.paths.UriWrapper;

public final class NviCandidateIndexDocumentGenerator {

    private static final String TYPE_NVI_CANDIDATE = "NviCandidate";
    private static final String DATE_FORMAT = "%02d";

    private NviCandidateIndexDocumentGenerator() {
    }

    public static NviCandidateIndexDocument generateNviCandidateIndexDocument(String resource, NviCandidate candidate) {
        return constructNviCandidateIndexDocument(candidate,
                                                  attempt(() -> dtoObjectMapper.readTree(
                                                      resource)).orElseThrow());
    }

    private static List<Affiliation> constructAffiliations(JsonNode resource, NviCandidate candidate) {
        return candidate.affiliationApprovals().stream()
                   .map(id -> getAffiliation(resource, id))
                   .toList();
    }

    private static Affiliation getAffiliation(JsonNode resource, String id) {
        return getJsonNodeStream(resource, JSON_PTR_CONTRIBUTOR)
                   .flatMap(contributor -> getJsonNodeStream(contributor, JSON_PTR_AFFILIATIONS))
                   .filter(affiliation -> Objects.nonNull(affiliation.get(FIELD_ID)))
                   .filter(affiliation -> affiliation.get(FIELD_ID).textValue().equals(id))
                   .findFirst()
                   .map(affiliation -> new Affiliation(affiliation.get(FIELD_ID).textValue(),
                                                       dtoObjectMapper.convertValue(affiliation.get(FIELD_LABELS),
                                                                                    new TypeReference<>() {
                                                                                    }),
                                                       ApprovalStatus.PENDING.getValue()))
                   .orElse(null);
    }

    private static String extractPublicationId(JsonNode resource) {
        return resource.at(JSON_PTR_ID).textValue();
    }

    private static Stream<JsonNode> getJsonNodeStream(JsonNode jsonNode, String jsonPtr) {
        return StreamSupport.stream(jsonNode.at(jsonPtr).spliterator(), false);
    }

    private static NviCandidateIndexDocument constructNviCandidateIndexDocument(NviCandidate candidate,
                                                                                JsonNode resource) {
        return new NviCandidateIndexDocument(extractPublicationIdentifier(resource),
                                             extractYear(resource),
                                             TYPE_NVI_CANDIDATE,
                                             extractPublication(resource),
                                             constructAffiliations(
                                                 resource, candidate));
    }

    private static String extractPublicationIdentifier(JsonNode resource) {
        return UriWrapper.fromUri(extractPublicationId(resource)).getPath().getLastPathElement();
    }

    private static Publication extractPublication(JsonNode resource) {
        return new Publication(extractPublicationId(resource), extractInstanceType(resource),
                               extractMainTitle(resource), extractPublicationDate(resource),
                               extractPublicationChannel(resource), extractContributors(resource));
    }

    private static List<Contributor> extractContributors(JsonNode resource) {
        return getJsonNodeStream(resource, JSON_PTR_CONTRIBUTOR)
                   .map(contributor -> contributor.get(FIELD_IDENTITY))
                   .map(identity -> new Contributor(identity.get(FIELD_ID).textValue(),
                                                    identity.get(FIELD_NAME).textValue(),
                                                    identity.get(FIELD_ORCID).textValue()))
                   .toList();
    }

    private static PublicationChannel extractPublicationChannel(JsonNode resource) {
        return new PublicationChannel(extractPublicationChannelId(resource), extractPublicationChannelName(resource),
                                      extractPublicationChannelLevel(resource),
                                      extractPublicationChannelType(resource));
    }

    private static String extractPublicationChannelType(JsonNode resource) {
        return resource.at(JSON_PTR_PUBLICATION_CHANNEL_TYPE).textValue();
    }

    private static String extractPublicationChannelLevel(JsonNode resource) {
        return resource.at(JSON_PTR_PUBLICATION_CHANNEL_LEVEL).textValue();
    }

    private static String extractPublicationChannelName(JsonNode resource) {
        return resource.at(JSON_PTR_PUBLICATION_CHANNEL_NAME).textValue();
    }

    private static String extractPublicationChannelId(JsonNode resource) {
        return resource.at(JSON_PTR_PUBLICATION_CHANNEL_ID).textValue();
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
        var year = publicationDateNode.get(FIELD_PUBLICATION_DATE_YEAR).asInt();
        var month = publicationDateNode.get(FIELD_PUBLICATION_DATE_MONTH).asInt();
        var day = publicationDateNode.get(FIELD_PUBLICATION_DATE_DAY).asInt();

        var formattedMonth = String.format(DATE_FORMAT, month);
        var formattedDay = String.format(DATE_FORMAT, day);

        return year + "-" + formattedMonth + "-" + formattedDay;
    }
}
