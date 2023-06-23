package no.sikt.nva.nvi.index;

import static no.unit.nva.commons.json.JsonUtils.dtoObjectMapper;
import static nva.commons.core.attempt.Try.attempt;
import com.amazonaws.services.lambda.runtime.Context;
import com.amazonaws.services.lambda.runtime.RequestHandler;
import com.amazonaws.services.lambda.runtime.events.SQSEvent;
import com.amazonaws.services.lambda.runtime.events.SQSEvent.SQSMessage;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Stream;
import java.util.stream.StreamSupport;
import no.sikt.nva.nvi.common.IndexClient;
import no.sikt.nva.nvi.common.StorageReader;
import no.sikt.nva.nvi.index.model.Affiliation;
import no.sikt.nva.nvi.index.model.Contributor;
import no.sikt.nva.nvi.index.model.NviCandidate;
import no.sikt.nva.nvi.index.model.NviCandidateIndexDocument;
import no.sikt.nva.nvi.index.model.Publication;
import no.sikt.nva.nvi.index.model.PublicationChannel;
import nva.commons.core.paths.UriWrapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class IndexNviCandidateHandler implements RequestHandler<SQSEvent, Void> {

    public static final String ERROR_MESSAGE_BODY_INVALID = "Message body invalid: {}";
    public static final String TYPE_NVI_CANDIDATE = "NviCandidate";
    private static final Logger LOGGER = LoggerFactory.getLogger(IndexNviCandidateHandler.class);
    private final IndexClient<NviCandidateIndexDocument> indexClient;

    private final StorageReader<NviCandidate> storageReader;

    public IndexNviCandidateHandler(StorageReader<NviCandidate> storageReader,
                                    IndexClient<NviCandidateIndexDocument> indexClient) {
        this.storageReader = storageReader;
        this.indexClient = indexClient;
    }

    public static String formatPublicationDate(JsonNode publicationDateNode) {
        var year = publicationDateNode.get("year").asInt();
        var month = publicationDateNode.get("month").asInt();
        var day = publicationDateNode.get("day").asInt();

        var formattedMonth = String.format("%02d", month);
        var formattedDay = String.format("%02d", day);

        return year + "-" + formattedMonth + "-" + formattedDay;
    }

    @Override
    public Void handleRequest(SQSEvent input, Context context) {
        var candidates = input.getRecords()
                             .stream()
                             .map(SQSMessage::getBody)
                             .map(this::parseBody)
                             .filter(Objects::nonNull)
                             .toList();

        candidates.forEach(candidate -> {
            var indexedResource = storageReader.read(candidate);
            var indexedResourceAsJsonNode = attempt(() -> dtoObjectMapper.readTree(indexedResource)).orElseThrow();
            var nviCandidateIndexDocument = constructNviCandidateIndexDocument(candidate, indexedResourceAsJsonNode);
            indexClient.addDocumentToIndex(nviCandidateIndexDocument);
        });

        return null;
    }

    private static List<Affiliation> constructAffiliations(JsonNode resource, NviCandidate candidate) {
        return candidate.affiliationApprovals().stream()
                   .map(id -> getAffiliation(resource, id))
                   .toList();
    }

    private static Affiliation getAffiliation(JsonNode resource, String id) {
        return getJsonNodeStream(resource, "/entityDescription/contributors")
                   .flatMap(contributor -> getJsonNodeStream(contributor, "/affiliations"))
                   .filter(affiliation -> Objects.nonNull(affiliation.get("id")))
                   .filter(affiliation -> affiliation.get("id").textValue().equals(id))
                   .findFirst()
                   .map(affiliation -> new Affiliation(affiliation.get("id").textValue(),
                                                       dtoObjectMapper.convertValue(affiliation.get("labels"),
                                                                                    new TypeReference<Map<String,
                                                                                                             String>>() {
                                                                                    }),
                                                       "Pending"))
                   .orElse(null);
    }

    private static String extractPublicationId(JsonNode resource) {
        return resource.at("/id").textValue();
    }

    private static Stream<JsonNode> getJsonNodeStream(JsonNode jsonNode, String jsonPtr) {
        return StreamSupport.stream(jsonNode.at(jsonPtr).spliterator(), false);
    }

    private NviCandidateIndexDocument constructNviCandidateIndexDocument(NviCandidate candidate,
                                                                         JsonNode resource) {
        return new NviCandidateIndexDocument(extractPublicationIdentifier(resource),
                                             extractYear(resource),
                                             TYPE_NVI_CANDIDATE,
                                             extractPublication(resource),
                                             constructAffiliations(
                                                 resource, candidate));
    }

    private String extractPublicationIdentifier(JsonNode resource) {
        return UriWrapper.fromUri(extractPublicationId(resource)).getPath().getLastPathElement();
    }

    private Publication extractPublication(JsonNode resource) {
        return new Publication(extractPublicationId(resource), extractInstanceType(resource),
                               extractMainTitle(resource), extractPublicationDate(resource),
                               extractPublicationChannel(resource), extractContributors(resource));
    }

    private List<Contributor> extractContributors(JsonNode resource) {
        return getJsonNodeStream(resource, "/entityDescription/contributors")
                   .map(contributor -> contributor.get("identity"))
                   .map(identity -> new Contributor(identity.get("id").textValue(),
                                                    identity.get("name").textValue(),
                                                    identity.get("orcid").textValue()))
                   .toList();
    }

    private PublicationChannel extractPublicationChannel(JsonNode resource) {
        return new PublicationChannel(extractPublicationChannelId(resource), extractPublicationChannelName(resource),
                                      extractPublicationChannelLevel(resource),
                                      extractPublicationChannelType(resource));
    }

    private String extractPublicationChannelType(JsonNode resource) {
        return resource.at("/entityDescription/reference/publicationContext/type").textValue();
    }

    private String extractPublicationChannelLevel(JsonNode resource) {
        return resource.at("/entityDescription/reference/publicationContext/level").textValue();
    }

    private String extractPublicationChannelName(JsonNode resource) {
        return resource.at("/entityDescription/reference/publicationContext/name").textValue();
    }

    private String extractPublicationChannelId(JsonNode resource) {
        return resource.at("/entityDescription/reference/publicationContext/id").textValue();
    }

    private String extractPublicationDate(JsonNode resource) {
        var publicationDate = resource.at("/entityDescription/publicationDate");
        return formatPublicationDate(publicationDate);
    }

    private String extractMainTitle(JsonNode resource) {
        return resource.at("/entityDescription/mainTitle").textValue();
    }

    private String extractInstanceType(JsonNode resource) {
        return resource.at("/entityDescription/reference/publicationInstance/type").textValue();
    }

    private String extractYear(JsonNode resource) {
        return resource.at("/entityDescription/publicationDate/year").textValue();
    }

    private NviCandidate parseBody(String body) {
        return attempt(() -> dtoObjectMapper.readValue(body, NviCandidate.class))
                   .orElse(failure -> {
                       logInvalidMessageBody(body);
                       return null;
                   });
    }

    private void logInvalidMessageBody(String body) {
        LOGGER.error(ERROR_MESSAGE_BODY_INVALID, body);
    }
}
