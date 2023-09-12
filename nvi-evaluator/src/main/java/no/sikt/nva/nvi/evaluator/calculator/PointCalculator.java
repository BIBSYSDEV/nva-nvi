package no.sikt.nva.nvi.evaluator.calculator;

import static no.sikt.nva.nvi.common.utils.JsonPointers.JSON_PTR_AFFILIATIONS;
import static no.sikt.nva.nvi.common.utils.JsonPointers.JSON_PTR_CHAPTER_PUBLISHER;
import static no.sikt.nva.nvi.common.utils.JsonPointers.JSON_PTR_CHAPTER_SERIES;
import static no.sikt.nva.nvi.common.utils.JsonPointers.JSON_PTR_CHAPTER_SERIES_LEVEL;
import static no.sikt.nva.nvi.common.utils.JsonPointers.JSON_PTR_CONTRIBUTOR;
import static no.sikt.nva.nvi.common.utils.JsonPointers.JSON_PTR_COUNTRY_CODE;
import static no.sikt.nva.nvi.common.utils.JsonPointers.JSON_PTR_INSTANCE_TYPE;
import static no.sikt.nva.nvi.common.utils.JsonPointers.JSON_PTR_PUBLICATION_CONTEXT;
import static no.sikt.nva.nvi.common.utils.JsonPointers.JSON_PTR_PUBLISHER;
import static no.sikt.nva.nvi.common.utils.JsonPointers.JSON_PTR_ROLE_TYPE;
import static no.sikt.nva.nvi.common.utils.JsonPointers.JSON_PTR_SERIES;
import static no.sikt.nva.nvi.common.utils.JsonPointers.JSON_PTR_SERIES_LEVEL;
import static no.sikt.nva.nvi.common.utils.JsonUtils.extractJsonNodeTextValue;
import static no.sikt.nva.nvi.common.utils.JsonUtils.streamNode;
import static no.unit.nva.commons.json.JsonUtils.dtoObjectMapper;
import static nva.commons.core.attempt.Try.attempt;
import com.fasterxml.jackson.databind.JsonNode;
import java.math.BigDecimal;
import java.math.MathContext;
import java.math.RoundingMode;
import java.net.URI;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Objects;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import java.util.stream.StreamSupport;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public final class PointCalculator {

    public static final String ERROR_MSG_EXTRACT_PUBLICATION_CONTEXT = "Could not extract publication channel for "
                                                                       + "instanceType {}, candidate: {}";
    public static final String COUNTRY_CODE_NORWAY = "NO";
    public static final String ROLE_CREATOR = "Creator";
    private static final Logger LOGGER = LoggerFactory.getLogger(PointCalculator.class);
    private static final int SCALE = 10;
    private static final int RESULT_SCALE = 4;
    private static final RoundingMode ROUNDING_MODE = RoundingMode.HALF_UP;
    private static final MathContext MATH_CONTEXT = new MathContext(SCALE, ROUNDING_MODE);
    private static final BigDecimal INTERNATIONAL_COLLABORATION_FACTOR =
        new BigDecimal("1.3").setScale(SCALE, ROUNDING_MODE);
    private static final BigDecimal NOT_INTERNATIONAL_COLLABORATION_FACTOR =
        BigDecimal.ONE.setScale(SCALE, ROUNDING_MODE);
    private static final String ACADEMIC_MONOGRAPH = "AcademicMonograph";
    private static final String ACADEMIC_CHAPTER = "AcademicChapter";
    private static final String ACADEMIC_ARTICLE = "AcademicArticle";
    private static final String ACADEMIC_LITERATURE_REVIEW = "AcademicLiteratureReview";
    private static final String LEVEL_ONE = "1";
    private static final String LEVEL_TWO = "2";
    private static final String SERIES = "Series";
    private static final String JOURNAL = "Journal";
    private static final String PUBLISHER = "Publisher";
    private static final Map<String, Map<String, Map<String, BigDecimal>>> INSTANCE_TYPE_AND_LEVEL_POINT_MAP =
        Map.of(
            ACADEMIC_MONOGRAPH, Map.of(
                PUBLISHER, Map.of(
                    LEVEL_ONE, BigDecimal.valueOf(5),
                    LEVEL_TWO, BigDecimal.valueOf(8)),
                SERIES, Map.of(
                    LEVEL_ONE, BigDecimal.valueOf(5),
                    LEVEL_TWO, BigDecimal.valueOf(8))),
            ACADEMIC_CHAPTER, Map.of(
                PUBLISHER, Map.of(
                    LEVEL_ONE, BigDecimal.valueOf(0.7),
                    LEVEL_TWO, BigDecimal.valueOf(1)),
                SERIES, Map.of(
                    LEVEL_ONE, BigDecimal.valueOf(1),
                    LEVEL_TWO, BigDecimal.valueOf(3))),
            ACADEMIC_ARTICLE, Map.of(
                JOURNAL, Map.of(
                    LEVEL_ONE, BigDecimal.valueOf(1),
                    LEVEL_TWO, BigDecimal.valueOf(3))),
            ACADEMIC_LITERATURE_REVIEW, Map.of(
                JOURNAL, Map.of(
                    LEVEL_ONE, BigDecimal.valueOf(1),
                    LEVEL_TWO, BigDecimal.valueOf(3))));

    private PointCalculator() {
    }

    public static Map<URI, BigDecimal> calculatePoints(JsonNode jsonNode,
                                                       Map<URI, List<URI>> nviCreatorsWithInstitutionIds) {
        //TODO: set isInternationalCollaboration when Cristin proxy api has implemented land code
        var isInternationalCollaboration = HARDCODED_INTERNATIONAL_COLLABORATION_BOOLEAN_TO_BE_REPLACED;
        return calculatePoints(calculateInstanceTypeAndLevelPoints(jsonNode),
                               countCreatorShares(jsonNode),
                               isInternationalCollaboration(jsonNode),
                               countInstitutionCreatorShares(nviCreatorsWithInstitutionIds));
    }

    private static Map<URI, BigDecimal> calculatePoints(BigDecimal instanceTypeAndLevelPoints, int creatorShareCount,
                                                        boolean isInternationalCollaboration,
                                                        Map<URI, Long> institutionCreatorShareCounts) {
        return institutionCreatorShareCounts.entrySet()
                   .stream()
                   .collect(Collectors.toMap(
                       Entry::getKey,
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
        var creatorFactor = institutionContributorFraction.sqrt(MATH_CONTEXT).setScale(SCALE, ROUNDING_MODE);
        return instanceTypeAndLevelPoints
                   .multiply(internationalFactor)
                   .multiply(creatorFactor)
                   .setScale(RESULT_SCALE, ROUNDING_MODE);
    }

    private static BigDecimal calculateInstanceTypeAndLevelPoints(JsonNode jsonNode) {
        var instanceType = extractInstanceType(jsonNode);
        var channelLevel = extractChannelLevel(instanceType, jsonNode);
        return getInstanceTypeAndLevelPoints(instanceType, channelLevel.type(), channelLevel.level());
    }

    private static BigDecimal getInstanceTypeAndLevelPoints(String instanceType,
                                                            String channelType, String level) {
        return INSTANCE_TYPE_AND_LEVEL_POINT_MAP.get(instanceType).get(channelType).get(level);
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

    private static int countCreatorShares(JsonNode jsonNode) {
        return streamNode(jsonNode.at(JSON_PTR_CONTRIBUTOR))
                   .filter(PointCalculator::isCreator)
                   .flatMap(contributor -> streamNode(contributor.at(JSON_PTR_AFFILIATIONS)))
                   .map(node -> 1)
                   .reduce(0, Integer::sum);
    }

    private static String extractInstanceType(JsonNode jsonNode) {
        return extractJsonNodeTextValue(jsonNode, JSON_PTR_INSTANCE_TYPE);
    }

    private static ChannelLevel extractChannelLevel(String instanceType, JsonNode jsonNode) {
        var channel = switch (instanceType) {
            case ACADEMIC_ARTICLE, ACADEMIC_LITERATURE_REVIEW -> jsonNode.at(JSON_PTR_PUBLICATION_CONTEXT).toString();
            case ACADEMIC_MONOGRAPH -> extractAcademicMonographChannel(jsonNode);
            case ACADEMIC_CHAPTER -> extractAcademicChapterChannel(jsonNode);
            default -> {
                LOGGER.error(ERROR_MSG_EXTRACT_PUBLICATION_CONTEXT, instanceType, jsonNode.toString());
                throw new IllegalArgumentException();
            }
        };
        return attempt(() -> dtoObjectMapper.readValue(channel, ChannelLevel.class)).orElseThrow();
    }

    private static String extractAcademicChapterChannel(JsonNode jsonNode) {
        if (Objects.nonNull(extractJsonNodeTextValue(jsonNode, JSON_PTR_CHAPTER_SERIES_LEVEL))) {
            return jsonNode.at(JSON_PTR_CHAPTER_SERIES).toString();
        } else {
            return jsonNode.at(JSON_PTR_CHAPTER_PUBLISHER).toString();
        }
    }

    private static String extractAcademicMonographChannel(JsonNode jsonNode) {
        if (Objects.nonNull(extractJsonNodeTextValue(jsonNode, JSON_PTR_SERIES_LEVEL))) {
            return jsonNode.at(JSON_PTR_SERIES).toString();
        } else {
            return jsonNode.at(JSON_PTR_PUBLISHER).toString();
        }
    }

    private static Stream<JsonNode> getJsonNodeStream(JsonNode jsonNode, String jsonPtr) {
        return StreamSupport.stream(jsonNode.at(jsonPtr).spliterator(), false);
    }

    private record ChannelLevel(String type, String level) {

    }
}
