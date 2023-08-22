package no.sikt.nva.nvi.evaluator.calculator;

import static no.sikt.nva.nvi.common.utils.JsonPointers.JSON_PTR_AFFILIATIONS;
import static no.sikt.nva.nvi.common.utils.JsonPointers.JSON_PTR_CHAPTER_PUBLISHER_LEVEL;
import static no.sikt.nva.nvi.common.utils.JsonPointers.JSON_PTR_CHAPTER_SERIES_LEVEL;
import static no.sikt.nva.nvi.common.utils.JsonPointers.JSON_PTR_CONTRIBUTOR;
import static no.sikt.nva.nvi.common.utils.JsonPointers.JSON_PTR_INSTANCE_TYPE;
import static no.sikt.nva.nvi.common.utils.JsonPointers.JSON_PTR_JOURNAL_LEVEL;
import static no.sikt.nva.nvi.common.utils.JsonPointers.JSON_PTR_SERIES_LEVEL;
import static no.sikt.nva.nvi.common.utils.JsonUtils.extractJsonNodeTextValue;
import com.fasterxml.jackson.databind.JsonNode;
import java.math.BigDecimal;
import java.math.MathContext;
import java.math.RoundingMode;
import java.net.URI;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Optional;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.StreamSupport;
import no.sikt.nva.nvi.common.utils.JsonPointers;

public class PointCalculator {

    private static final String ACADEMIC_MONOGRAPH = "AcademicMonograph";
    private static final String ACADEMIC_CHAPTER = "AcademicChapter";
    private static final String ACADEMIC_ARTICLE = "AcademicArticle";
    private static final String ACADEMIC_LITERATURE_REVIEW = "AcademicLiteratureReview";
    private static final String ACADEMIC_CHAPTER_ISBN = "AcademicChapterISBN";
    private static final Map<String, Map<String, BigDecimal>> INSTANCE_TYPE_AND_LEVEL_POINT_MAP =
        Map.of(
            ACADEMIC_MONOGRAPH, Map.of("1", BigDecimal.valueOf(5), "2", BigDecimal.valueOf(8)),
            ACADEMIC_CHAPTER, Map.of("1", BigDecimal.ONE, "2", BigDecimal.valueOf(3)),
            ACADEMIC_CHAPTER_ISBN, Map.of("1", new BigDecimal("0.7"), "2", BigDecimal.ONE),
            ACADEMIC_ARTICLE, Map.of("1", BigDecimal.ONE, "2", BigDecimal.valueOf(3)),
            ACADEMIC_LITERATURE_REVIEW, Map.of("1", BigDecimal.ONE, "2", BigDecimal.valueOf(3))
        );
    public static final MathContext MATH_CONTEXT = new MathContext(4, RoundingMode.HALF_UP);

    private PointCalculator() {
    }

    public static Map<URI, BigDecimal> calculatePoints(JsonNode jsonNode, Set<URI> approvalInstitutions) {
        var instanceType = extractJsonNodeTextValue(jsonNode, JSON_PTR_INSTANCE_TYPE);
        var instanceTypeAndLevelPoints = calculateInstanceTypeAndLevelPoints(instanceType,
                                                                             getLevel(instanceType, jsonNode));
        int creatorShareCount = countCreatorShares(jsonNode);
        //TODO: set isInternationalCollaboration when Cristin proxy api has implemented land code
        boolean isInternationalCollaboration = false;
        var institutionCreatorShareCount = institutionCreatorShareCount(jsonNode, approvalInstitutions);
        return calculatePoints(instanceTypeAndLevelPoints,
                               creatorShareCount,
                               isInternationalCollaboration,
                               institutionCreatorShareCount);
    }

    private static Map<URI, Long> institutionCreatorShareCount(JsonNode jsonNode, Set<URI> approvalInstitutions) {
        var contributors = jsonNode.at(JSON_PTR_CONTRIBUTOR);
        return StreamSupport.stream(contributors.spliterator(), false)
                   .filter(contributor -> "Verified".equals(extractJsonNodeTextValue(contributor, "/identity"
                                                                                                  +
                                                                                                  "/verificationStatus")))
                   .flatMap(contributor -> StreamSupport.stream(contributor.at("/affiliations").spliterator(), false))
                   .map(affiliation -> extractJsonNodeTextValue(affiliation, "/id"))
                   .map(URI::create)
                   .filter(approvalInstitutions::contains)
                   .collect(Collectors.groupingByConcurrent(Function.identity(), Collectors.counting()));
    }

    private static Map<URI, BigDecimal> calculatePoints(BigDecimal instanceTypeAndLevelPoints, int creatorShareCount,
                                                        boolean isInternationalCollaboration,
                                                        Map<URI, Long> institutionCreatorShareCounts) {
        return institutionCreatorShareCounts.entrySet().stream().collect(Collectors.toMap(Entry::getKey,
                                                                                          entry -> calculateInstitutionPoints(
                                                                                              instanceTypeAndLevelPoints,
                                                                                              isInternationalCollaboration,
                                                                                              entry.getValue(),
                                                                                              creatorShareCount)));
    }

    private static BigDecimal calculateInstitutionPoints(BigDecimal instanceTypeAndLevelPoints,
                                                         boolean isInternationalCollaboration,
                                                         Long institutionCreatorShareCount, int creatorShareCount) {
        var internationalFactor = isInternationalCollaboration ? new BigDecimal("1.3") : BigDecimal.ONE;
        var institutionContributorFactor =
            BigDecimal.valueOf(institutionCreatorShareCount).divide(new BigDecimal(creatorShareCount),
                                                                    new MathContext(5, RoundingMode.HALF_UP));
        return instanceTypeAndLevelPoints.multiply(internationalFactor)
                   .multiply(institutionContributorFactor.sqrt(MATH_CONTEXT));
    }

    private static BigDecimal calculateInstanceTypeAndLevelPoints(String instanceType, String level) {
        return INSTANCE_TYPE_AND_LEVEL_POINT_MAP.get(instanceType).get(level);
    }

    private static int countCreatorShares(JsonNode jsonNode) {
        return StreamSupport.stream(jsonNode.at(JSON_PTR_CONTRIBUTOR).spliterator(), false)
                   .flatMap(contributor ->
                                StreamSupport.stream(contributor.at(JSON_PTR_AFFILIATIONS).spliterator(), false))
                   .map(node -> 1)
                   .reduce(0, Integer::sum);
    }

    private static String getLevel(String instanceType, JsonNode jsonNode) {
        return switch (instanceType) {
            case ACADEMIC_ARTICLE, ACADEMIC_LITERATURE_REVIEW ->
                extractJsonNodeTextValue(jsonNode, JSON_PTR_JOURNAL_LEVEL);
            case ACADEMIC_MONOGRAPH -> getAcademicMonographChannelLevel(jsonNode);
            case ACADEMIC_CHAPTER -> extractLevelAcademicChapter(jsonNode);
            default -> "";
        };
    }

    private static String getAcademicMonographChannelLevel(JsonNode jsonNode) {
        var seriesLevel = extractJsonNodeTextValue(jsonNode, JSON_PTR_SERIES_LEVEL);
        return Optional.ofNullable(seriesLevel)
                   .orElse(extractJsonNodeTextValue(jsonNode, JsonPointers.JSON_PTR_PUBLISHER_LEVEL));
    }

    private static String extractLevelAcademicChapter(JsonNode jsonNode) {
        return Optional.ofNullable(extractJsonNodeTextValue(jsonNode, JSON_PTR_CHAPTER_SERIES_LEVEL))
                   .orElse(extractJsonNodeTextValue(jsonNode, JSON_PTR_CHAPTER_PUBLISHER_LEVEL));
    }
}
