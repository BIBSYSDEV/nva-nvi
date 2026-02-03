package no.sikt.nva.nvi.common.model;

import static no.sikt.nva.nvi.test.TestUtils.randomBigDecimal;
import static no.unit.nva.testutils.RandomDataGenerator.randomBoolean;
import static no.unit.nva.testutils.RandomDataGenerator.randomInteger;
import static no.unit.nva.testutils.RandomDataGenerator.randomUri;

import java.net.URI;
import java.util.List;
import no.sikt.nva.nvi.common.service.model.InstitutionPoints;

public final class PointCalculationFixtures {

  private PointCalculationFixtures() {}

  public static PointCalculation randomPointCalculation(int scale) {
    var firstPoint = randomInstitutionPoints(scale);
    var secondPoint = randomInstitutionPoints(scale);
    var totalPoints = firstPoint.institutionPoints().add(secondPoint.institutionPoints());
    return new PointCalculation(
        InstanceType.ACADEMIC_ARTICLE,
        createLevelOneJournal(),
        randomBoolean(),
        randomBigDecimal(),
        randomBigDecimal(),
        randomInteger(100),
        List.of(firstPoint, secondPoint),
        totalPoints);
  }

  public static InstitutionPoints randomInstitutionPoints(int scale) {
    var institutionId = randomUri();
    var firstCreator = randomCreatorAffiliationPoints(institutionId, scale);
    var secondCreator = randomCreatorAffiliationPoints(institutionId, scale);
    var totalPoints = firstCreator.points().add(secondCreator.points());
    return new InstitutionPoints(
        institutionId, totalPoints, Sector.OTHER, List.of(firstCreator, secondCreator));
  }

  public static InstitutionPoints.CreatorAffiliationPoints randomCreatorAffiliationPoints(
      URI affiliationId, int scale) {
    return new InstitutionPoints.CreatorAffiliationPoints(
        randomUri(), affiliationId, randomBigDecimal(scale));
  }

  public static PublicationChannel createLevelOneJournal() {
    return new PublicationChannel(randomUri(), ChannelType.JOURNAL, ScientificValue.LEVEL_ONE);
  }
}
