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
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.net.URI;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Objects;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import java.util.stream.StreamSupport;
import no.sikt.nva.nvi.common.client.OrganizationRetriever;
import no.sikt.nva.nvi.common.model.Organization;
import no.sikt.nva.nvi.common.utils.JsonUtils;
import no.sikt.nva.nvi.events.evaluator.model.InstanceType;
import no.sikt.nva.nvi.events.evaluator.model.InstitutionPoints;
import no.sikt.nva.nvi.events.evaluator.model.InstitutionPoints.AffiliationPoints;
import no.sikt.nva.nvi.events.evaluator.model.Level;
import no.sikt.nva.nvi.events.evaluator.model.PointCalculation;
import no.sikt.nva.nvi.events.evaluator.model.PublicationChannel;
import no.sikt.nva.nvi.events.evaluator.model.VerifiedNviCreator;
import no.sikt.nva.nvi.events.evaluator.model.VerifiedNviCreator.NviOrganization;

public final class PointCalculator {

    private static final String COUNTRY_CODE_NORWAY = "NO";
    private static final String ROLE_CREATOR = "Creator";

    private final OrganizationRetriever organizationRetriever;

    public PointCalculator(OrganizationRetriever organizationRetriever) {
        this.organizationRetriever = organizationRetriever;
    }

    public PointCalculation calculatePoints(JsonNode jsonNode, List<VerifiedNviCreator> nviCreators) {
        var instanceType = extractInstanceType(jsonNode);
        var publicationChannel = extractChannel(instanceType, jsonNode);
        return calculatePoints(nviCreators, instanceType, publicationChannel,
                               isInternationalCollaboration(jsonNode), countCreatorShares(jsonNode));
    }

    private static PointCalculation calculatePoints(List<VerifiedNviCreator> nviCreators,
                                                    InstanceType instanceType, Channel channel,
                                                    boolean internationalCollaboration, int creatorShareCount) {
        var basePoints = getInstanceTypeAndLevelPoints(instanceType, channel);
        var collaborationFactor = getInternationalCollaborationFactor(internationalCollaboration);
        var institutionPoints = calculatePointsForAllInstitutions(basePoints, creatorShareCount,
                                                                  internationalCollaboration,
                                                                  nviCreators);
        var totalPoints = sumInstitutionPoints(institutionPoints);
        return new PointCalculation(instanceType, channel.type(), channel.id(), channel.level(),
                                    internationalCollaboration, collaborationFactor,
                                    basePoints,
                                    creatorShareCount,
                                    institutionPoints,
                                    totalPoints);
    }

    private static BigDecimal sumInstitutionPoints(List<InstitutionPoints> institutionPoints) {
        return institutionPoints.stream()
                   .map(InstitutionPoints::institutionPoints)
                   .reduce(ZERO, BigDecimal::add);
    }

    private static List<InstitutionPoints> calculatePointsForAllInstitutions(BigDecimal instanceTypeAndLevelPoints,
                                                                             int creatorShareCount,
                                                                             boolean isInternationalCollaboration,
                                                                             List<VerifiedNviCreator> nviCreators) {
        var institutionCreatorShareCount = countInstitutionCreatorShares(nviCreators);
        return institutionCreatorShareCount.entrySet()
                   .stream()
                   .map(entry -> calculateInstitutionPoints(instanceTypeAndLevelPoints, creatorShareCount,
                                                            isInternationalCollaboration, nviCreators, entry))
                   .toList();
    }

    private static InstitutionPoints calculateInstitutionPoints(BigDecimal instanceTypeAndLevelPoints,
                                                                int creatorShareCount,
                                                                boolean isInternationalCollaboration,
                                                                List<VerifiedNviCreator> nviCreators,
                                                                Entry<URI, Long> entry) {
        var internationalFactor = getInternationalCollaborationFactor(isInternationalCollaboration);
        var institutionContributorFraction = divideInstitutionShareOnTotalShares(entry.getValue(),
                                                                                 creatorShareCount);
        var institutionPoints = executeNviFormula(instanceTypeAndLevelPoints, internationalFactor,
                                                  institutionContributorFraction);
        return new InstitutionPoints(entry.getKey(),
                                     institutionPoints,
                                     calculateAffiliationPoints(entry.getKey(),
                                                                instanceTypeAndLevelPoints,
                                                                creatorShareCount,
                                                                isInternationalCollaboration,
                                                                nviCreators, entry.getValue()));
    }

    private static List<AffiliationPoints> calculateAffiliationPoints(URI institutionId,
                                                                      BigDecimal instanceTypeAndLevelPoints,
                                                                      int creatorShareCount,
                                                                      boolean isInternationalCollaboration,
                                                                      List<VerifiedNviCreator> nviCreators,
                                                                      Long institutionCreatorShareCount) {
        var internationalFactor = getInternationalCollaborationFactor(isInternationalCollaboration);
        var institutionContributorFraction = divideInstitutionShareOnTotalShares(institutionCreatorShareCount,
                                                                                 creatorShareCount);
        var institutionPoints = executeNviFormula(instanceTypeAndLevelPoints, internationalFactor,
                                                  institutionContributorFraction);
        var nviCreatorsForInstitution = getNviCreatorAffiliationsForInstitution(institutionId, nviCreators);
        return nviCreatorsForInstitution.entrySet()
                   .stream()
                   .flatMap(
                       entry -> calculatePointsForAffiliation(institutionCreatorShareCount, entry, institutionPoints))
                   .toList();
    }

    private static Stream<AffiliationPoints> calculatePointsForAffiliation(Long institutionCreatorShareCount,
                                                                           Entry<URI, List<URI>> nviCreator,
                                                                           BigDecimal institutionPoints) {
        return nviCreator.getValue().stream()
                   .map(affiliationId -> new AffiliationPoints(affiliationId, nviCreator.getKey(),
                                                           institutionPoints.divide(
                                                                   BigDecimal.valueOf(institutionCreatorShareCount),
                                                                   MATH_CONTEXT)
                                                               .setScale(RESULT_SCALE, RoundingMode.HALF_UP)));
    }

    private static Map<URI, List<URI>> getNviCreatorAffiliationsForInstitution(URI institutionId,
                                                                               List<VerifiedNviCreator> nviCreators) {
        return nviCreators.stream()
                   .filter(creator -> isAffiliated(institutionId, creator))
                   .collect(Collectors.toMap(VerifiedNviCreator::id,
                                             creator -> mapToAffiliations(institutionId, creator)));
    }

    private static List<URI> mapToAffiliations(URI institutionId, VerifiedNviCreator creator) {
        return creator.nviAffiliations().stream()
                   .filter(affiliation -> isPartOf(affiliation, institutionId))
                   .map(NviOrganization::id)
                   .toList();
    }

    private static boolean isAffiliated(URI institutionId, VerifiedNviCreator creator) {
        return creator.nviAffiliations().stream().anyMatch(affiliation -> isPartOf(affiliation, institutionId));
    }

    private static boolean isPartOf(NviOrganization affiliation, URI institutionId) {
        return affiliation.topLevelOrganization().id().equals(institutionId);
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

    private static Map<URI, Long> countInstitutionCreatorShares(List<VerifiedNviCreator> nviCreators) {
        return nviCreators.stream()
                   .flatMap(verifiedNviCreator -> verifiedNviCreator.nviAffiliations().stream())
                   .map(NviOrganization::topLevelOrganization)
                   .map(NviOrganization::id)
                   .distinct()
                   .collect(Collectors.toMap(institutionId -> institutionId,
                                             institutionId -> countCreators(institutionId, nviCreators)));
    }

    private static Long countCreators(URI institutionId, List<VerifiedNviCreator> nviCreators) {
        return nviCreators.stream()
                   .flatMap(creator -> creator.nviAffiliations().stream())
                   .filter(affiliation -> isPartOf(affiliation, institutionId))
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
