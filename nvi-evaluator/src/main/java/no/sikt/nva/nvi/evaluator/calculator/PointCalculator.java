package no.sikt.nva.nvi.evaluator.calculator;

import static no.sikt.nva.nvi.common.utils.JsonPointers.JSON_POINTER_IDENTITY_VERIFICATION_STATUS;
import static no.sikt.nva.nvi.common.utils.JsonPointers.JSON_PTR_AFFILIATIONS;
import static no.sikt.nva.nvi.common.utils.JsonPointers.JSON_PTR_CHAPTER_PUBLISHER;
import static no.sikt.nva.nvi.common.utils.JsonPointers.JSON_PTR_CHAPTER_SERIES;
import static no.sikt.nva.nvi.common.utils.JsonPointers.JSON_PTR_CHAPTER_SERIES_LEVEL;
import static no.sikt.nva.nvi.common.utils.JsonPointers.JSON_PTR_CONTRIBUTOR;
import static no.sikt.nva.nvi.common.utils.JsonPointers.JSON_PTR_ID;
import static no.sikt.nva.nvi.common.utils.JsonPointers.JSON_PTR_INSTANCE_TYPE;
import static no.sikt.nva.nvi.common.utils.JsonPointers.JSON_PTR_JOURNAL;
import static no.sikt.nva.nvi.common.utils.JsonPointers.JSON_PTR_PUBLISHER;
import static no.sikt.nva.nvi.common.utils.JsonPointers.JSON_PTR_SERIES;
import static no.sikt.nva.nvi.common.utils.JsonPointers.JSON_PTR_SERIES_LEVEL;
import static no.sikt.nva.nvi.common.utils.JsonUtils.extractJsonNodeAsString;
import static no.sikt.nva.nvi.common.utils.JsonUtils.extractJsonNodeTextValue;
import static no.sikt.nva.nvi.common.utils.JsonUtils.streamNode;
import static no.unit.nva.commons.json.JsonUtils.dtoObjectMapper;
import static nva.commons.core.attempt.Try.attempt;
import com.fasterxml.jackson.databind.JsonNode;
import java.math.BigDecimal;
import java.math.MathContext;
import java.math.RoundingMode;
import java.net.URI;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Objects;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

public final class PointCalculator {

    public static final String DEFAULT_LEVEL = "0";
    public static final ChannelLevel PUBLISHER_LEVEL_ONE = ChannelLevel.builder()
                                                               .withLevel("1")
                                                               .withType("Publisher")
                                                               .build();
    public static final ChannelLevel SERIES_LEVEL_ONE = ChannelLevel.builder()
                                                            .withLevel("1")
                                                            .withType("Series")
                                                            .build();
    public static final ChannelLevel PUBLISHER_LEVEL_TWO = ChannelLevel.builder()
                                                               .withLevel("2")
                                                               .withType("Publisher")
                                                               .build();
    public static final ChannelLevel SERIES_LEVEL_TWO = ChannelLevel.builder()
                                                            .withLevel("2")
                                                            .withType("Series")
                                                            .build();
    public static final ChannelLevel JOURNAL_LEVEL_ONE = ChannelLevel.builder()
                                                             .withLevel("1")
                                                             .withType("Journal")
                                                             .build();
    public static final ChannelLevel JOURNAL_LEVEL_TWO = ChannelLevel.builder()
                                                             .withLevel("2")
                                                             .withType("Journal")
                                                             .build();
    private static final int SCALE = 10;
    private static final int RESULT_SCALE = 4;
    private static final RoundingMode ROUNDING_MODE = RoundingMode.HALF_UP;
    private static final MathContext MATH_CONTEXT = new MathContext(SCALE, ROUNDING_MODE);
    private static final String VERIFIED = "Verified";
    private static final BigDecimal INTERNATIONAL_COLLABORATION_FACTOR =
        new BigDecimal("1.3").setScale(SCALE, ROUNDING_MODE);
    private static final BigDecimal NOT_INTERNATIONAL_COLLABORATION_FACTOR =
        BigDecimal.ONE.setScale(SCALE, ROUNDING_MODE);
    private static final String ACADEMIC_MONOGRAPH = "AcademicMonograph";
    private static final String ACADEMIC_CHAPTER = "AcademicChapter";
    private static final String ACADEMIC_ARTICLE = "AcademicArticle";
    private static final String ACADEMIC_LITERATURE_REVIEW = "AcademicLiteratureReview";
    private static final Map<String, Map<ChannelLevel, BigDecimal>> INSTANCE_TYPE_AND_LEVEL_POINT_MAP =
        Map.of(
            ACADEMIC_MONOGRAPH, Map.of(PUBLISHER_LEVEL_ONE, BigDecimal.valueOf(5),
                                       SERIES_LEVEL_ONE, BigDecimal.valueOf(5),
                                       PUBLISHER_LEVEL_TWO, BigDecimal.valueOf(8),
                                       SERIES_LEVEL_TWO, BigDecimal.valueOf(8)),
            ACADEMIC_CHAPTER, Map.of(PUBLISHER_LEVEL_ONE, BigDecimal.valueOf(0.7),
                                     SERIES_LEVEL_ONE, BigDecimal.valueOf(1),
                                     PUBLISHER_LEVEL_TWO, BigDecimal.ONE,
                                     SERIES_LEVEL_TWO, BigDecimal.valueOf(3)),
            ACADEMIC_ARTICLE, Map.of(JOURNAL_LEVEL_ONE, BigDecimal.ONE,
                                     JOURNAL_LEVEL_TWO, BigDecimal.valueOf(3)),
            ACADEMIC_LITERATURE_REVIEW, Map.of(JOURNAL_LEVEL_ONE, BigDecimal.ONE,
                                               JOURNAL_LEVEL_TWO, BigDecimal.valueOf(3))
        );
    private static final boolean HARDCODED_INTERNATIONAL_COLLABORATION_BOOLEAN_TO_BE_REPLACED = false;

    private PointCalculator() {
    }

    public static Map<URI, BigDecimal> calculatePoints(JsonNode jsonNode, Set<URI> approvalInstitutions) {
        var instanceType = extractInstanceType(jsonNode);
        var channelType = extractChannelLevel(instanceType, jsonNode);
        var instanceTypeAndLevelPoints = calculateInstanceTypeAndLevelPoints(instanceType, channelType);
        //TODO: set isInternationalCollaboration when Cristin proxy api has implemented land code
        var isInternationalCollaboration = HARDCODED_INTERNATIONAL_COLLABORATION_BOOLEAN_TO_BE_REPLACED;
        return calculatePoints(instanceTypeAndLevelPoints,
                               countCreatorShares(jsonNode),
                               isInternationalCollaboration,
                               countInstitutionCreatorShares(jsonNode, approvalInstitutions));
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

    private static String extractInstanceType(JsonNode jsonNode) {
        return extractJsonNodeTextValue(jsonNode, JSON_PTR_INSTANCE_TYPE);
    }

    private static Map<URI, Long> countInstitutionCreatorShares(JsonNode jsonNode, Set<URI> approvalInstitutions) {
        var contributors = jsonNode.at(JSON_PTR_CONTRIBUTOR);
        return streamNode(contributors)
                   .filter(PointCalculator::isVerified)
                   .flatMap(contributor -> streamNode(contributor.at(JSON_PTR_AFFILIATIONS)))
                   .map(affiliation -> extractJsonNodeTextValue(affiliation, JSON_PTR_ID))
                   .map(URI::create)
                   .filter(approvalInstitutions::contains)
                   .collect(Collectors.groupingByConcurrent(Function.identity(), Collectors.counting()));
    }

    private static boolean isVerified(JsonNode contributor) {
        return VERIFIED.equals(extractJsonNodeTextValue(contributor, JSON_POINTER_IDENTITY_VERIFICATION_STATUS));
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

    private static BigDecimal calculateInstanceTypeAndLevelPoints(String instanceType, ChannelLevel channelLevel) {
        return INSTANCE_TYPE_AND_LEVEL_POINT_MAP.get(instanceType).get(channelLevel);
    }

    private static int countCreatorShares(JsonNode jsonNode) {
        return streamNode(jsonNode.at(JSON_PTR_CONTRIBUTOR))
                   .flatMap(contributor -> streamNode(contributor.at(JSON_PTR_AFFILIATIONS)))
                   .map(node -> 1)
                   .reduce(0, Integer::sum);
    }

    private static ChannelLevel extractChannelLevel(String instanceType, JsonNode jsonNode) {
        var channel = switch (instanceType) {
            case ACADEMIC_ARTICLE, ACADEMIC_LITERATURE_REVIEW -> extractJsonNodeAsString(jsonNode, JSON_PTR_JOURNAL);
            case ACADEMIC_MONOGRAPH -> extractAcademicMonographChannel(jsonNode);
            case ACADEMIC_CHAPTER -> extractAcademicChapterChannel(jsonNode);
            default -> DEFAULT_LEVEL;
        };
        return attempt(() -> dtoObjectMapper.readValue(channel, ChannelLevel.class)).orElseThrow();
    }

    private static String extractAcademicChapterChannel(JsonNode jsonNode) {
        return Objects.nonNull(extractJsonNodeTextValue(jsonNode, JSON_PTR_CHAPTER_SERIES_LEVEL))
                   ? extractJsonNodeAsString(jsonNode, JSON_PTR_CHAPTER_SERIES)
                   : extractJsonNodeAsString(jsonNode, JSON_PTR_CHAPTER_PUBLISHER);
    }

    private static String extractAcademicMonographChannel(JsonNode jsonNode) {
        return Objects.nonNull(extractJsonNodeTextValue(jsonNode, JSON_PTR_SERIES_LEVEL))
                   ? extractJsonNodeAsString(jsonNode, JSON_PTR_SERIES)
                   : extractJsonNodeAsString(jsonNode, JSON_PTR_PUBLISHER);
    }
}
