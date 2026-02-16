package no.sikt.nva.nvi.events.evaluator.calculator;

import static java.math.BigDecimal.ZERO;
import static java.util.Objects.isNull;
import static no.sikt.nva.nvi.events.evaluator.calculator.CreatorVerificationUtil.getUnverifiedCreators;
import static no.sikt.nva.nvi.events.evaluator.calculator.CreatorVerificationUtil.getVerifiedCreators;
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
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import no.sikt.nva.nvi.common.dto.PointCalculationDto;
import no.sikt.nva.nvi.common.dto.PublicationChannelDto;
import no.sikt.nva.nvi.common.model.Customer;
import no.sikt.nva.nvi.common.model.InstanceType;
import no.sikt.nva.nvi.common.model.Sector;
import no.sikt.nva.nvi.common.service.model.InstitutionPoints;
import no.sikt.nva.nvi.common.service.model.InstitutionPoints.CreatorAffiliationPoints;
import no.sikt.nva.nvi.events.evaluator.model.NviCreator;
import no.sikt.nva.nvi.events.evaluator.model.NviOrganization;
import no.sikt.nva.nvi.events.evaluator.model.UnverifiedNviCreator;
import no.sikt.nva.nvi.events.evaluator.model.VerifiedNviCreator;

public class PointCalculator {

  private final InstanceType instanceType;
  private final PublicationChannelDto publicationChannel;
  private final boolean internationalCollaborationFactor;
  private final BigDecimal collaborationFactor;
  private final BigDecimal basePoints;
  private final int creatorShareCount;
  private final Collection<VerifiedNviCreator> verifiedNviCreators;
  private final Collection<UnverifiedNviCreator> unverifiedNviCreators;
  private final Map<URI, Customer> customerMap;

  public PointCalculator(
      PublicationChannelDto publicationChannel,
      InstanceType instanceType,
      Collection<NviCreator> nviCreators,
      boolean isInternationalCollaboration,
      int creatorShareCount,
      Map<URI, Customer> customers) {
    this.publicationChannel = publicationChannel;
    this.instanceType = instanceType;
    this.verifiedNviCreators = getVerifiedCreators(nviCreators);
    this.unverifiedNviCreators = getUnverifiedCreators(nviCreators);
    this.internationalCollaborationFactor = isInternationalCollaboration;
    this.collaborationFactor = getInternationalCollaborationFactor(isInternationalCollaboration);
    this.basePoints = getInstanceTypeAndLevelPoints(instanceType, publicationChannel);
    this.creatorShareCount = creatorShareCount;
    this.customerMap = customers;
  }

  public PointCalculationDto calculatePoints() {
    var institutionPoints = calculatePointsForAllInstitutions();
    var totalPoints = sumInstitutionPoints(institutionPoints);
    return new PointCalculationDto(
        instanceType,
        publicationChannel,
        internationalCollaborationFactor,
        collaborationFactor,
        basePoints,
        creatorShareCount,
        institutionPoints,
        totalPoints);
  }

  private static BigDecimal getInstanceTypeAndLevelPoints(
      InstanceType instanceType, PublicationChannelDto channel) {
    return INSTANCE_TYPE_AND_LEVEL_POINT_MAP
        .get(instanceType)
        .get(channel.channelType())
        .get(channel.scientificValue());
  }

  private static BigDecimal getInternationalCollaborationFactor(
      boolean isInternationalCollaboration) {
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
    return dividend
        .divide(BigDecimal.valueOf(divisor), MATH_CONTEXT)
        .setScale(RESULT_SCALE, RoundingMode.HALF_UP);
  }

  private static Map<URI, Long> getCreatorCountPerInstitution(
      Collection<? extends NviCreator> nviCreators) {
    return nviCreators.stream()
        .flatMap(creator -> creator.nviAffiliations().stream())
        .map(NviOrganization::topLevelOrganization)
        .map(NviOrganization::id)
        .distinct()
        .collect(
            Collectors.toMap(
                institutionId -> institutionId,
                institutionId -> countCreators(institutionId, nviCreators)));
  }

  private static Long countCreators(
      URI institutionId, Collection<? extends NviCreator> nviCreators) {
    return nviCreators.stream().filter(creator -> creator.isAffiliatedWith(institutionId)).count();
  }

  private static Stream<CreatorAffiliationPoints> calculatePointsForAffiliation(
      Entry<URI, Collection<URI>> nviCreator,
      BigDecimal institutionPoints,
      Long institutionCreatorCount) {
    var pointsForCreator = divide(institutionCreatorCount, institutionPoints);
    var numberOfAffiliations = nviCreator.getValue().size();
    var pointsForAffiliation = divide(numberOfAffiliations, pointsForCreator);
    return nviCreator.getValue().stream()
        .map(
            affiliationId ->
                new CreatorAffiliationPoints(
                    nviCreator.getKey(), affiliationId, pointsForAffiliation));
  }

  private InstitutionPoints calculateInstitutionPoints(Entry<URI, Long> institutionCreatorCount) {
    var institution = institutionCreatorCount.getKey();
    var creatorCount = institutionCreatorCount.getValue();

    var institutionContributorFraction = getInstitutionContributorFraction(creatorCount);
    var institutionPoints = executeNviFormula(institutionContributorFraction);
    var creatorPoints = calculateAffiliationPoints(institutionCreatorCount, institutionPoints);
    var sector = Sector.fromString(customerMap.get(institution).sector()).orElse(Sector.UNKNOWN);
    return new InstitutionPoints(institution, institutionPoints, sector, creatorPoints);
  }

  private BigDecimal getInstitutionContributorFraction(Long institutionCreatorCount) {
    var value =
        isNullOrZero(institutionCreatorCount)
            ? ZERO
            : BigDecimal.valueOf(institutionCreatorCount)
                .divide(new BigDecimal(creatorShareCount), MATH_CONTEXT);
    return value.setScale(SCALE, ROUNDING_MODE);
  }

  private boolean isNullOrZero(Long creatorShareCount) {
    return isNull(creatorShareCount) || creatorShareCount == 0;
  }

  private List<CreatorAffiliationPoints> calculateAffiliationPoints(
      Entry<URI, Long> institutionCreatorCount, BigDecimal institutionPoints) {
    var institutionId = institutionCreatorCount.getKey();
    return verifiedNviCreators.stream()
        .filter(creator -> creator.isAffiliatedWith(institutionId))
        .collect(
            Collectors.toMap(
                VerifiedNviCreator::id, creator -> creator.getAffiliationsPartOf(institutionId)))
        .entrySet()
        .stream()
        .flatMap(
            entry ->
                calculatePointsForAffiliation(
                    entry, institutionPoints, institutionCreatorCount.getValue()))
        .toList();
  }

  private BigDecimal executeNviFormula(BigDecimal institutionContributorFraction) {
    return basePoints
        .multiply(collaborationFactor)
        .multiply(institutionContributorFraction.sqrt(MATH_CONTEXT).setScale(SCALE, ROUNDING_MODE))
        .setScale(RESULT_SCALE, ROUNDING_MODE);
  }

  private List<InstitutionPoints> calculatePointsForAllInstitutions() {
    var verifiedCreatorsPerInstitution = getCreatorCountPerInstitution(verifiedNviCreators);
    var unverifiedCreatorsPerInstitution = getCreatorCountPerInstitution(unverifiedNviCreators);

    // Add unverified creators to verified creators so that those institutions get processed,
    // but as if they had no contributors and end up with zero points.
    unverifiedCreatorsPerInstitution.forEach(
        (institution, count) -> verifiedCreatorsPerInstitution.putIfAbsent(institution, 0L));
    return verifiedCreatorsPerInstitution.entrySet().stream()
        .map(this::calculateInstitutionPoints)
        .toList();
  }
}
