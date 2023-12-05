package no.sikt.nva.nvi.events.evaluator.calculator;

import static java.math.BigDecimal.ZERO;
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
import static no.sikt.nva.nvi.events.evaluator.calculator.PointCalculationConstants.INSTANCE_TYPE_AND_LEVEL_POINT_MAP;
import static no.sikt.nva.nvi.events.evaluator.calculator.PointCalculationConstants.INTERNATIONAL_COLLABORATION_FACTOR;
import static no.sikt.nva.nvi.events.evaluator.calculator.PointCalculationConstants.MATH_CONTEXT;
import static no.sikt.nva.nvi.events.evaluator.calculator.PointCalculationConstants.NOT_INTERNATIONAL_COLLABORATION_FACTOR;
import static no.sikt.nva.nvi.events.evaluator.calculator.PointCalculationConstants.RESULT_SCALE;
import static no.sikt.nva.nvi.events.evaluator.calculator.PointCalculationConstants.ROUNDING_MODE;
import static no.sikt.nva.nvi.events.evaluator.calculator.PointCalculationConstants.SCALE;
import static no.unit.nva.commons.json.JsonUtils.dtoObjectMapper;
import static nva.commons.core.attempt.Try.attempt;
import com.fasterxml.jackson.annotation.JsonAlias;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.math.BigDecimal;
import java.net.URI;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Objects;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import java.util.stream.StreamSupport;
import no.sikt.nva.nvi.common.utils.JsonUtils;
import no.sikt.nva.nvi.events.evaluator.model.InstanceType;
import no.sikt.nva.nvi.events.evaluator.model.Level;
import no.sikt.nva.nvi.events.evaluator.model.Organization;
import no.sikt.nva.nvi.events.evaluator.model.PointCalculation;
import no.sikt.nva.nvi.events.evaluator.model.PublicationChannel;

public final class PointCalculator {

    private static final String COUNTRY_CODE_NORWAY = "NO";
    private static final String ROLE_CREATOR = "Creator";

    private final OrganizationRetriever organizationRetriever;

    public PointCalculator(OrganizationRetriever organizationRetriever) {
        this.organizationRetriever = organizationRetriever;
    }

    public PointCalculation calculatePoints(JsonNode jsonNode,
                                            Map<URI, List<URI>> nviCreatorsWithInstitutionIds) {
        var instanceType = extractInstanceType(jsonNode);
        //TODO: Remove when migrating to publication channels v2
        massiveHackToFixObjectsWithMultipleTypes(jsonNode);
        var publicationChannel = extractChannel(instanceType, jsonNode);
        return calculatePoints(nviCreatorsWithInstitutionIds, instanceType, publicationChannel,
                               isInternationalCollaboration(jsonNode), countCreatorShares(jsonNode));
    }

    private static PointCalculation calculatePoints(Map<URI, List<URI>> nviCreatorsWithInstitutionIds,
                                                    InstanceType instanceType, Channel channel,
                                                    boolean internationalCollaboration, int creatorShareCount) {
        var basePoints = getInstanceTypeAndLevelPoints(instanceType, channel);
        var collaborationFactor = getInternationalCollaborationFactor(internationalCollaboration);
        var institutionPoints = calculatePointsForAllInstitutions(basePoints, creatorShareCount,
                                                                  internationalCollaboration,
                                                                  nviCreatorsWithInstitutionIds);
        var totalPoints = sumInstitutionPoints(institutionPoints);
        return new PointCalculation(instanceType, channel.type(), channel.id(), channel.level(),
                                    internationalCollaboration, collaborationFactor,
                                    basePoints,
                                    creatorShareCount,
                                    institutionPoints,
                                    totalPoints);
    }

    private static BigDecimal sumInstitutionPoints(Map<URI, BigDecimal> institutionPoints) {
        return institutionPoints.values().stream().reduce(BigDecimal::add).orElse(ZERO);
    }

    private static Map<URI, BigDecimal> calculatePointsForAllInstitutions(BigDecimal instanceTypeAndLevelPoints,
                                                                          int creatorShareCount,
                                                                          boolean isInternationalCollaboration,
                                                                          Map<URI, List<URI>>
                                                                              nviCreatorsWithInstitutionIds) {
        var institutionCreatorShareCount = countInstitutionCreatorShares(nviCreatorsWithInstitutionIds);
        return institutionCreatorShareCount.entrySet()
                   .stream()
                   .collect(Collectors.toMap(Entry::getKey,
                                             entry -> calculateInstitutionPoints(
                                                 instanceTypeAndLevelPoints,
                                                 isInternationalCollaboration,
                                                 entry.getValue(),
                                                 creatorShareCount)));
    }

    private static BigDecimal calculateInstitutionPoints(BigDecimal instanceTypeAndLevelPoints,
                                                         boolean isInternationalCollaboration,
                                                         Long institutionCreatorShareCount, int creatorShareCount) {
        var internationalFactor = getInternationalCollaborationFactor(isInternationalCollaboration);
        var institutionContributorFraction = divideInstitutionShareOnTotalShares(institutionCreatorShareCount,
                                                                                 creatorShareCount);
        return executeNviFormula(instanceTypeAndLevelPoints, internationalFactor, institutionContributorFraction);
    }

    private static BigDecimal executeNviFormula(BigDecimal basePoints, BigDecimal internationalFactor,
                                                BigDecimal creatorShareCount) {
        return basePoints.multiply(internationalFactor)
                   .multiply(creatorShareCount.sqrt(MATH_CONTEXT).setScale(SCALE, ROUNDING_MODE))
                   .setScale(RESULT_SCALE, ROUNDING_MODE);
    }

    private static BigDecimal getInstanceTypeAndLevelPoints(InstanceType instanceType, Channel channel) {
        return INSTANCE_TYPE_AND_LEVEL_POINT_MAP.get(instanceType).get(channel.type()).get(channel.level());
    }

    private static boolean isInternationalCollaboration(JsonNode jsonNode) {
        return getJsonNodeStream(jsonNode, JSON_PTR_CONTRIBUTOR)
                   .filter(PointCalculator::isCreator)
                   .flatMap(PointCalculator::extractAffiliations)
                   .map(PointCalculator::extractCountryCode)
                   .filter(Objects::nonNull)
                   .anyMatch(PointCalculator::isInternationalCountryCode);
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

    private static Map<URI, Long> countInstitutionCreatorShares(Map<URI, List<URI>> nviCreatorsWithInstitutions) {
        return nviCreatorsWithInstitutions.entrySet()
                   .stream()
                   .flatMap(entry -> entry.getValue().stream())
                   .distinct()
                   .collect(Collectors.toMap(institutionId -> institutionId,
                                             institutionId -> countCreators(institutionId,
                                                                            nviCreatorsWithInstitutions)));
    }

    private static Long countCreators(URI institutionId, Map<URI, List<URI>> nviCreatorsWithInstitutions) {
        return nviCreatorsWithInstitutions.entrySet()
                   .stream()
                   .filter(creator -> creator.getValue().contains(institutionId))
                   .count();
    }

    private static BigDecimal divideInstitutionShareOnTotalShares(Long institutionCreatorShareCount,
                                                                  int creatorShareCount) {
        return BigDecimal.valueOf(institutionCreatorShareCount)
                   .divide(new BigDecimal(creatorShareCount), MATH_CONTEXT)
                   .setScale(SCALE, ROUNDING_MODE);
    }

    private static BigDecimal getInternationalCollaborationFactor(boolean isInternationalCollaboration) {
        return isInternationalCollaboration
                   ? INTERNATIONAL_COLLABORATION_FACTOR
                   : NOT_INTERNATIONAL_COLLABORATION_FACTOR;
    }

    private static Integer countCreatorsWithoutAffiliations(List<JsonNode> creators) {
        return creators.stream()
                   .filter(PointCalculator::doesNotHaveAffiliations)
                   .map(node -> 1)
                   .reduce(0, Integer::sum);
    }

    private static Integer countCreatorsWithOnlyUnverifiedAffiliations(List<JsonNode> creators) {
        return creators.stream()
                   .filter(PointCalculator::hasAffiliations)
                   .filter(PointCalculator::isOnlyAffiliatedWithOrganizationsWithOutId)
                   .map(node -> 1)
                   .reduce(0, Integer::sum);
    }

    private static boolean isOnlyAffiliatedWithOrganizationsWithOutId(JsonNode contributor) {
        return extractAffiliations(contributor).allMatch(PointCalculator::doesNotHaveId);
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
        return streamNode(jsonNode.at(JSON_PTR_CONTRIBUTOR)).filter(PointCalculator::isCreator).toList();
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

    @Deprecated
    private static void massiveHackToFixObjectsWithMultipleTypes(JsonNode jsonNode) {
        var series = jsonNode.at(JSON_PTR_SERIES);
        if (!series.isMissingNode() && series.at("/type").isArray()) {
            var seriesObject = (ObjectNode) series;
            seriesObject.remove("type");
            seriesObject.put("type", "Series");
        }
        var chapterSeries = jsonNode.at(JSON_PTR_CHAPTER_SERIES);
        if (!chapterSeries.isMissingNode() && chapterSeries.at("/type").isArray()) {
            var chapterSeriesObject = (ObjectNode) chapterSeries;
            chapterSeriesObject.remove("type");
            chapterSeriesObject.put("type", "Series");
        }
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
                   .filter(PointCalculator::hasId)
                   .map(JsonUtils::extractId)
                   .distinct()
                   .map(organizationRetriever::fetchOrganization)
                   .map(Organization::getTopLevelOrg)
                   .map(Organization::id)
                   .distinct()
                   .map(node -> 1)
                   .reduce(0, Integer::sum);
    }

    private record Channel(URI id, PublicationChannel type, @JsonAlias("scientificValue") Level level) {

    }
}
