package no.sikt.nva.nvi.events.evaluator.calculator;

import static java.util.Objects.nonNull;
import static no.sikt.nva.nvi.common.utils.JsonPointers.JSON_PTR_AFFILIATIONS;
import static no.sikt.nva.nvi.common.utils.JsonPointers.JSON_PTR_CHAPTER_PUBLISHER;
import static no.sikt.nva.nvi.common.utils.JsonPointers.JSON_PTR_CHAPTER_SERIES;
import static no.sikt.nva.nvi.common.utils.JsonPointers.JSON_PTR_CHAPTER_SERIES_LEVEL;
import static no.sikt.nva.nvi.common.utils.JsonPointers.JSON_PTR_CHAPTER_SERIES_SCIENTIFIC_VALUE;
import static no.sikt.nva.nvi.common.utils.JsonPointers.JSON_PTR_CONTRIBUTOR;
import static no.sikt.nva.nvi.common.utils.JsonPointers.JSON_PTR_COUNTRY_CODE;
import static no.sikt.nva.nvi.common.utils.JsonPointers.JSON_PTR_ID;
import static no.sikt.nva.nvi.common.utils.JsonPointers.JSON_PTR_INSTANCE_TYPE;
import static no.sikt.nva.nvi.common.utils.JsonPointers.JSON_PTR_PUBLICATION_CONTEXT;
import static no.sikt.nva.nvi.common.utils.JsonPointers.JSON_PTR_PUBLISHER;
import static no.sikt.nva.nvi.common.utils.JsonPointers.JSON_PTR_ROLE_TYPE;
import static no.sikt.nva.nvi.common.utils.JsonPointers.JSON_PTR_SERIES;
import static no.sikt.nva.nvi.common.utils.JsonPointers.JSON_PTR_SERIES_LEVEL;
import static no.sikt.nva.nvi.common.utils.JsonPointers.JSON_PTR_SERIES_SCIENTIFIC_VALUE;
import static no.sikt.nva.nvi.common.utils.JsonUtils.extractJsonNodeTextValue;
import static no.sikt.nva.nvi.common.utils.JsonUtils.streamNode;
import static no.unit.nva.commons.json.JsonUtils.dtoObjectMapper;
import static nva.commons.core.attempt.Try.attempt;
import com.fasterxml.jackson.databind.JsonNode;
import java.util.List;
import java.util.Objects;
import java.util.stream.Stream;
import java.util.stream.StreamSupport;
import no.sikt.nva.nvi.common.client.OrganizationRetriever;
import no.sikt.nva.nvi.common.model.Organization;
import no.sikt.nva.nvi.common.utils.JsonUtils;
import no.sikt.nva.nvi.events.evaluator.model.Channel;
import no.sikt.nva.nvi.events.evaluator.model.InstanceType;
import no.sikt.nva.nvi.events.evaluator.model.PointCalculation;
import no.sikt.nva.nvi.events.evaluator.model.VerifiedNviCreator;

public final class PointService {

    private static final String COUNTRY_CODE_NORWAY = "NO";
    private static final String ROLE_CREATOR = "Creator";
    private final OrganizationRetriever organizationRetriever;

    public PointService(OrganizationRetriever organizationRetriever) {
        this.organizationRetriever = organizationRetriever;
    }

    public PointCalculation calculatePoints(JsonNode publication,
                                            List<VerifiedNviCreator> nviCreators) {
        var instanceType = extractInstanceType(publication);
        var publicationChannel = extractChannel(instanceType, publication);
        var internationalCollaborationFactor = isInternationalCollaboration(publication);
        var creatorShareCount = countCreatorShares(publication);
        var pointCalculator = new PointCalculator(publicationChannel, instanceType, nviCreators,
                                                  internationalCollaborationFactor,
                                                  creatorShareCount);
        return pointCalculator.calculatePoints();
    }

    private static boolean isInternationalCollaboration(JsonNode jsonNode) {
        return getJsonNodeStream(jsonNode, JSON_PTR_CONTRIBUTOR)
                   .filter(PointService::isCreator)
                   .flatMap(PointService::extractAffiliations)
                   .map(PointService::extractCountryCode)
                   .filter(Objects::nonNull)
                   .anyMatch(PointService::isInternationalCountryCode);
    }

    private static String extractCountryCode(JsonNode affiliationNode) {
        return extractJsonNodeTextValue(affiliationNode, JSON_PTR_COUNTRY_CODE);
    }

    private static Stream<JsonNode> extractAffiliations(JsonNode contributorNode) {
        return getJsonNodeStream(contributorNode, JSON_PTR_AFFILIATIONS);
    }

    private static boolean isCreator(JsonNode contributorNode) {
        return ROLE_CREATOR.equals(extractJsonNodeTextValue(contributorNode, JSON_PTR_ROLE_TYPE));
    }

    private static boolean isInternationalCountryCode(String countryCode) {
        return !COUNTRY_CODE_NORWAY.equals(countryCode);
    }

    private static Integer countCreatorsWithoutAffiliations(List<JsonNode> creators) {
        return creators.stream()
                   .filter(PointService::doesNotHaveAffiliations)
                   .map(node -> 1)
                   .reduce(0, Integer::sum);
    }

    private static Integer countCreatorsWithOnlyUnverifiedAffiliations(List<JsonNode> creators) {
        return creators.stream()
                   .filter(PointService::hasAffiliations)
                   .filter(PointService::isOnlyAffiliatedWithOrganizationsWithOutId)
                   .map(node -> 1)
                   .reduce(0, Integer::sum);
    }

    private static boolean isOnlyAffiliatedWithOrganizationsWithOutId(JsonNode contributor) {
        return extractAffiliations(contributor).allMatch(PointService::doesNotHaveId);
    }

    private static boolean hasId(JsonNode affiliation) {
        return nonNull(extractId(affiliation));
    }

    private static boolean doesNotHaveId(JsonNode affiliation) {
        return !hasId(affiliation);
    }

    private static String extractId(JsonNode affiliation) {
        return extractJsonNodeTextValue(affiliation, JSON_PTR_ID);
    }

    private static List<JsonNode> extractCreatorNodes(JsonNode jsonNode) {
        return streamNode(jsonNode.at(JSON_PTR_CONTRIBUTOR)).filter(PointService::isCreator).toList();
    }

    private static boolean hasAffiliations(JsonNode contributor) {
        return !doesNotHaveAffiliations(contributor);
    }

    private static boolean doesNotHaveAffiliations(JsonNode contributor) {
        return contributor.at(JSON_PTR_AFFILIATIONS).isEmpty();
    }

    private static InstanceType extractInstanceType(JsonNode jsonNode) {
        return InstanceType.parse(extractJsonNodeTextValue(jsonNode, JSON_PTR_INSTANCE_TYPE));
    }

    private static Channel extractChannel(InstanceType instanceType, JsonNode jsonNode) {
        var channel = switch (instanceType) {
            case ACADEMIC_ARTICLE, ACADEMIC_LITERATURE_REVIEW -> jsonNode.at(JSON_PTR_PUBLICATION_CONTEXT).toString();
            case ACADEMIC_MONOGRAPH -> extractAcademicMonographChannel(jsonNode);
            case ACADEMIC_CHAPTER -> extractAcademicChapterChannel(jsonNode);
        };
        return attempt(() -> dtoObjectMapper.readValue(channel, Channel.class)).orElseThrow();
    }

    private static String extractAcademicChapterChannel(JsonNode jsonNode) {
        if (nonNull(extractJsonNodeTextValue(jsonNode, JSON_PTR_CHAPTER_SERIES_LEVEL))
            || nonNull(extractJsonNodeTextValue(jsonNode, JSON_PTR_CHAPTER_SERIES_SCIENTIFIC_VALUE))) {
            return jsonNode.at(JSON_PTR_CHAPTER_SERIES).toString();
        } else {
            return jsonNode.at(JSON_PTR_CHAPTER_PUBLISHER).toString();
        }
    }

    private static String extractAcademicMonographChannel(JsonNode jsonNode) {
        if (nonNull(extractJsonNodeTextValue(jsonNode, JSON_PTR_SERIES_LEVEL))
            || nonNull(extractJsonNodeTextValue(jsonNode, JSON_PTR_SERIES_SCIENTIFIC_VALUE))) {
            return jsonNode.at(JSON_PTR_SERIES).toString();
        } else {
            return jsonNode.at(JSON_PTR_PUBLISHER).toString();
        }
    }

    private static Stream<JsonNode> getJsonNodeStream(JsonNode jsonNode, String jsonPtr) {
        return StreamSupport.stream(jsonNode.at(jsonPtr).spliterator(), false);
    }

    private int countCreatorShares(JsonNode jsonNode) {
        var creators = extractCreatorNodes(jsonNode);
        return Integer.sum(Integer.sum(countVerifiedTopLevelAffiliationsPerCreator(creators),
                                       countCreatorsWithoutAffiliations(creators)),
                           countCreatorsWithOnlyUnverifiedAffiliations(creators));
    }

    private Integer countVerifiedTopLevelAffiliationsPerCreator(List<JsonNode> creators) {
        return creators.stream()
                   .map(this::countVerifiedTopLevelAffiliations)
                   .reduce(0, Integer::sum);
    }

    private Integer countVerifiedTopLevelAffiliations(JsonNode creator) {
        return extractAffiliations(creator)
                   .filter(PointService::hasId)
                   .map(JsonUtils::extractId)
                   .distinct()
                   .map(organizationRetriever::fetchOrganization)
                   .map(Organization::getTopLevelOrg)
                   .map(Organization::id)
                   .distinct()
                   .map(node -> 1)
                   .reduce(0, Integer::sum);
    }
}
