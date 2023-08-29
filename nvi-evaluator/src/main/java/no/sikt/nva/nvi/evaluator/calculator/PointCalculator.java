package no.sikt.nva.nvi.evaluator.calculator;

import static no.sikt.nva.nvi.common.utils.JsonPointers.JSON_PTR_AFFILIATIONS;
import static no.sikt.nva.nvi.common.utils.JsonPointers.JSON_PTR_CHAPTER_PUBLISHER_LEVEL;
import static no.sikt.nva.nvi.common.utils.JsonPointers.JSON_PTR_CHAPTER_SERIES_LEVEL;
import static no.sikt.nva.nvi.common.utils.JsonPointers.JSON_PTR_CONTRIBUTOR;
import static no.sikt.nva.nvi.common.utils.JsonPointers.JSON_PTR_INSTANCE_TYPE;
import static no.sikt.nva.nvi.common.utils.JsonPointers.JSON_PTR_JOURNAL_LEVEL;
import static no.sikt.nva.nvi.common.utils.JsonPointers.JSON_PTR_SERIES_LEVEL;
import static no.sikt.nva.nvi.common.utils.JsonUtils.extractJsonNodeTextValue;
import static no.sikt.nva.nvi.common.utils.JsonUtils.streamNode;
import com.fasterxml.jackson.databind.JsonNode;
import java.math.BigDecimal;
import java.math.MathContext;
import java.math.RoundingMode;
import java.net.URI;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Optional;
import java.util.stream.Collectors;
import no.sikt.nva.nvi.common.utils.JsonPointers;

public final class PointCalculator {

    private static final int SCALE = 10;
    private static final RoundingMode ROUNDING_MODE = RoundingMode.HALF_UP;
    private static final MathContext MATH_CONTEXT = new MathContext(SCALE, ROUNDING_MODE);
    private static final BigDecimal INTERNATIONAL_COLLABORATION_FACTOR =
        new BigDecimal("1.3").setScale(SCALE, ROUNDING_MODE);
    private static final BigDecimal NOT_INTERNATIONAL_COLLABORATION_FACTOR =
        BigDecimal.ONE.setScale(SCALE, ROUNDING_MODE);
    private static final int RESULT_SCALE = 4;
    private static final String ACADEMIC_MONOGRAPH = "AcademicMonograph";
    private static final String ACADEMIC_CHAPTER = "AcademicChapter";
    private static final String ACADEMIC_ARTICLE = "AcademicArticle";
    private static final String ACADEMIC_LITERATURE_REVIEW = "AcademicLiteratureReview";
    private static final Map<String, Map<String, BigDecimal>> INSTANCE_TYPE_AND_LEVEL_POINT_MAP =
        Map.of(
            ACADEMIC_MONOGRAPH, Map.of("1", BigDecimal.valueOf(5), "2", BigDecimal.valueOf(8)),
            ACADEMIC_CHAPTER, Map.of("1", BigDecimal.ONE, "2", BigDecimal.valueOf(3)),
            ACADEMIC_ARTICLE, Map.of("1", BigDecimal.ONE, "2", BigDecimal.valueOf(3)),
            ACADEMIC_LITERATURE_REVIEW, Map.of("1", BigDecimal.ONE, "2", BigDecimal.valueOf(3))
        );
    private static final boolean HARDCODED_INTERNATIONAL_COLLABORATION_BOOLEAN_TO_BE_REPLACED = false;
    private static final String DEFAULT_LEVEL = "0";

    private PointCalculator() {
    }

    public static Map<URI, BigDecimal> calculatePoints(JsonNode jsonNode,
                                                       Map<URI, List<URI>> nviCreatorsWithInstitutionIds) {
        var instanceType = extractInstanceType(jsonNode);
        var instanceTypeAndLevelPoints = calculateInstanceTypeAndLevelPoints(instanceType,
                                                                             getLevel(instanceType, jsonNode));
        //TODO: set isInternationalCollaboration when Cristin proxy api has implemented land code
        var isInternationalCollaboration = HARDCODED_INTERNATIONAL_COLLABORATION_BOOLEAN_TO_BE_REPLACED;
        return calculatePoints(instanceTypeAndLevelPoints,
                               countCreatorShares(jsonNode),
                               isInternationalCollaboration,
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

    private static String extractInstanceType(JsonNode jsonNode) {
        return extractJsonNodeTextValue(jsonNode, JSON_PTR_INSTANCE_TYPE);
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

    private static BigDecimal calculateInstanceTypeAndLevelPoints(String instanceType, String level) {
        return INSTANCE_TYPE_AND_LEVEL_POINT_MAP.get(instanceType).get(level);
    }

    private static int countCreatorShares(JsonNode jsonNode) {
        return streamNode(jsonNode.at(JSON_PTR_CONTRIBUTOR))
                   .flatMap(contributor -> streamNode(contributor.at(JSON_PTR_AFFILIATIONS)))
                   .map(node -> 1)
                   .reduce(0, Integer::sum);
    }

    private static String getLevel(String instanceType, JsonNode jsonNode) {
        return switch (instanceType) {
            case ACADEMIC_ARTICLE, ACADEMIC_LITERATURE_REVIEW ->
                extractJsonNodeTextValue(jsonNode, JSON_PTR_JOURNAL_LEVEL);
            case ACADEMIC_MONOGRAPH -> extractAcademicMonographLevel(jsonNode);
            case ACADEMIC_CHAPTER -> extractAcademicChapterLevel(jsonNode);
            default -> DEFAULT_LEVEL;
        };
    }

    private static String extractAcademicMonographLevel(JsonNode jsonNode) {
        return Optional.ofNullable(extractJsonNodeTextValue(jsonNode, JSON_PTR_SERIES_LEVEL))
                   .orElse(extractJsonNodeTextValue(jsonNode, JsonPointers.JSON_PTR_PUBLISHER_LEVEL));
    }

    private static String extractAcademicChapterLevel(JsonNode jsonNode) {
        return Optional.ofNullable(extractJsonNodeTextValue(jsonNode, JSON_PTR_CHAPTER_SERIES_LEVEL))
                   .orElse(extractJsonNodeTextValue(jsonNode, JSON_PTR_CHAPTER_PUBLISHER_LEVEL));
    }
}
