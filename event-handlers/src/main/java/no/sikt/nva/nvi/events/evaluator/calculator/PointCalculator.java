package no.sikt.nva.nvi.events.evaluator.calculator;

import static java.math.BigDecimal.ZERO;
import static no.sikt.nva.nvi.events.evaluator.calculator.PointCalculationConstants.INSTANCE_TYPE_AND_LEVEL_POINT_MAP;
import static no.sikt.nva.nvi.events.evaluator.calculator.PointCalculationConstants.INTERNATIONAL_COLLABORATION_FACTOR;
import static no.sikt.nva.nvi.events.evaluator.calculator.PointCalculationConstants.MATH_CONTEXT;
import static no.sikt.nva.nvi.events.evaluator.calculator.PointCalculationConstants.NOT_INTERNATIONAL_COLLABORATION_FACTOR;
import static no.sikt.nva.nvi.events.evaluator.calculator.PointCalculationConstants.RESULT_SCALE;
import static no.sikt.nva.nvi.events.evaluator.calculator.PointCalculationConstants.ROUNDING_MODE;
import static no.sikt.nva.nvi.events.evaluator.calculator.PointCalculationConstants.SCALE;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.net.URI;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import no.sikt.nva.nvi.common.service.model.InstanceType;
import no.sikt.nva.nvi.common.service.model.InstitutionPoints;
import no.sikt.nva.nvi.common.service.model.InstitutionPoints.CreatorAffiliationPoints;
import no.sikt.nva.nvi.common.service.model.NviCreator;
import no.sikt.nva.nvi.common.service.model.UnverifiedNviCreator;
import no.sikt.nva.nvi.events.evaluator.model.Channel;
import no.sikt.nva.nvi.events.evaluator.model.PointCalculation;
import no.sikt.nva.nvi.common.service.model.VerifiedNviCreator;
import no.sikt.nva.nvi.common.service.model.NviOrganization;

public class PointCalculator {

    private final InstanceType instanceType;
    private final Channel publicationChannel;
    private final boolean internationalCollaborationFactor;
    private final BigDecimal collaborationFactor;
    private final BigDecimal basePoints;
    private final int creatorShareCount;
    private final List<VerifiedNviCreator> verifiedNviCreators;
    private final List<UnverifiedNviCreator> unverifiedNviCreators;

    public PointCalculator(Channel publicationChannel, InstanceType instanceType,
                           List<VerifiedNviCreator> verifiedNviCreators,
                           List<UnverifiedNviCreator> unverifiedNviCreators,  boolean isInternationalCollaboration,
                           int creatorShareCount) {
        this.publicationChannel = publicationChannel;
        this.instanceType = instanceType;
        this.verifiedNviCreators = verifiedNviCreators;
        this.unverifiedNviCreators = unverifiedNviCreators;
        this.internationalCollaborationFactor = isInternationalCollaboration;
        this.collaborationFactor = getInternationalCollaborationFactor(isInternationalCollaboration);
        this.basePoints = getInstanceTypeAndLevelPoints(instanceType, publicationChannel);
        this.creatorShareCount = creatorShareCount;
    }

    public PointCalculation calculatePoints() {
        var institutionPoints = calculatePointsForAllInstitutions();
        var totalPoints = sumInstitutionPoints(institutionPoints);
        return new PointCalculation(instanceType, publicationChannel.type(), publicationChannel.id(),
                                    publicationChannel.level(),
                                    internationalCollaborationFactor, collaborationFactor,
                                    basePoints,
                                    creatorShareCount,
                                    institutionPoints,
                                    totalPoints);
    }

    private static BigDecimal getInstanceTypeAndLevelPoints(InstanceType instanceType, Channel channel) {
        return INSTANCE_TYPE_AND_LEVEL_POINT_MAP.get(instanceType).get(channel.type()).get(channel.level());
    }

    private static BigDecimal getInternationalCollaborationFactor(boolean isInternationalCollaboration) {
        return isInternationalCollaboration
                   ? INTERNATIONAL_COLLABORATION_FACTOR
                   : NOT_INTERNATIONAL_COLLABORATION_FACTOR;
    }

    private static BigDecimal sumInstitutionPoints(List<InstitutionPoints> institutionPoints) {
        return institutionPoints.stream()
                   .map(InstitutionPoints::institutionPoints)
                   .reduce(ZERO, BigDecimal::add);
    }

    private static BigDecimal divide(long divisor, BigDecimal dividend) {
        return dividend.divide(BigDecimal.valueOf(divisor), MATH_CONTEXT).setScale(RESULT_SCALE, RoundingMode.HALF_UP);
    }

    private static Map<URI, Long> countNumberOfCreatorsForInstitutions(List<NviCreator> nviCreators) {
        return nviCreators.stream()
                   .flatMap(creator -> creator.nviAffiliations().stream())
                   .map(NviOrganization::topLevelOrganization)
                   .map(NviOrganization::id)
                   .distinct()
                   .collect(Collectors.toMap(institutionId -> institutionId,
                                             institutionId -> countCreators(institutionId, nviCreators)));
    }

    private static Long countCreators(URI institutionId, List<NviCreator> nviCreators) {
        return nviCreators.stream()
                   .filter(creator -> creator.isAffiliatedWith(institutionId))
                   .count();
    }

    private static Stream<CreatorAffiliationPoints> calculatePointsForAffiliation(Entry<URI, List<URI>> nviCreator,
                                                                                  BigDecimal institutionPoints,
                                                                                  Long institutionCreatorCount) {
        var pointsForCreator = divide(institutionCreatorCount, institutionPoints);
        var numberOfAffiliations = nviCreator.getValue().size();
        var pointsForAffiliation = divide(numberOfAffiliations, pointsForCreator);
        return nviCreator.getValue().stream()
                   .map(affiliationId -> new CreatorAffiliationPoints(nviCreator.getKey(), affiliationId,
                                                                      pointsForAffiliation));
    }

    // FIXME: Should use both verified and unverified creators
    private InstitutionPoints calculateInstitutionPoints(Entry<URI, Long> institutionCreatorCount) {
        var institutionContributorFraction = divideInstitutionShareOnTotalShares(institutionCreatorCount.getValue());
        var institutionPoints = executeNviFormula(institutionContributorFraction);
        return new InstitutionPoints(institutionCreatorCount.getKey(), institutionPoints,
                                     calculateAffiliationPoints(institutionCreatorCount, institutionPoints));
    }

    // FIXME: May need to change this too?
    private List<CreatorAffiliationPoints> calculateAffiliationPoints(Entry<URI, Long> institutionCreatorCount,
                                                                      BigDecimal institutionPoints) {
        var institutionId = institutionCreatorCount.getKey();
        return verifiedNviCreators.stream()
                   .filter(creator -> creator.isAffiliatedWith(institutionId))
                   .collect(Collectors.toMap(VerifiedNviCreator::id,
                                             creator -> creator.getAffiliationsPartOf(institutionId)))
                   .entrySet()
                   .stream()
                   .flatMap(entry -> calculatePointsForAffiliation(entry, institutionPoints,
                                                                   institutionCreatorCount.getValue()))
                   .toList();
    }

    private BigDecimal executeNviFormula(BigDecimal institutionContributorFraction) {
        return basePoints.multiply(collaborationFactor)
                   .multiply(institutionContributorFraction.sqrt(MATH_CONTEXT).setScale(SCALE, ROUNDING_MODE))
                   .setScale(RESULT_SCALE, ROUNDING_MODE);
    }

    private BigDecimal divideInstitutionShareOnTotalShares(Long institutionCreatorShareCount) {
        return BigDecimal.valueOf(institutionCreatorShareCount)
                   .divide(new BigDecimal(creatorShareCount), MATH_CONTEXT)
                   .setScale(SCALE, ROUNDING_MODE);
    }

    private List<InstitutionPoints> calculatePointsForAllInstitutions() {
        var institutionCreatorCount = countNumberOfCreatorsForInstitutions(verifiedNviCreators);
        return institutionCreatorCount.entrySet()
                   .stream()
                   .map(this::calculateInstitutionPoints)
                   .toList();
    }
}
