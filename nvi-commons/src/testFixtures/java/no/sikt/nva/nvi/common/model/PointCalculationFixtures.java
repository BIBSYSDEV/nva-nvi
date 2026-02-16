package no.sikt.nva.nvi.common.model;

import static no.sikt.nva.nvi.common.model.Sector.ABM;
import static no.sikt.nva.nvi.common.model.Sector.HEALTH;
import static no.sikt.nva.nvi.common.model.Sector.INSTITUTE;
import static no.sikt.nva.nvi.common.model.Sector.OTHER;
import static no.sikt.nva.nvi.common.model.Sector.UHI;
import static no.sikt.nva.nvi.test.TestUtils.randomBigDecimal;
import static no.unit.nva.testutils.RandomDataGenerator.randomBoolean;
import static no.unit.nva.testutils.RandomDataGenerator.randomElement;
import static no.unit.nva.testutils.RandomDataGenerator.randomInteger;
import static no.unit.nva.testutils.RandomDataGenerator.randomUri;

import java.net.URI;
import java.util.List;
import no.sikt.nva.nvi.common.service.model.InstitutionPoints;

public final class PointCalculationFixtures {

  private PointCalculationFixtures() {}

  public static PointCalculation randomPointCalculation(int scale) {
    return randomPointCalculation(scale, randomElement(UHI, HEALTH, INSTITUTE, ABM, OTHER));
  }

  public static PointCalculation randomPointCalculation(int scale, Sector sector) {
    var firstPoint = randomInstitutionPoints(scale, sector);
    var secondPoint = randomInstitutionPoints(scale, sector);
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

  private static InstitutionPoints randomInstitutionPoints(int scale, Sector sector) {
    var institutionId = randomUri();
    var firstCreator = randomCreatorAffiliationPoints(institutionId, scale);
    var secondCreator = randomCreatorAffiliationPoints(institutionId, scale);
    var totalPoints = firstCreator.points().add(secondCreator.points());
    return new InstitutionPoints(
        institutionId, totalPoints, sector, List.of(firstCreator, secondCreator));
  }

  private static InstitutionPoints.CreatorAffiliationPoints randomCreatorAffiliationPoints(
      URI affiliationId, int scale) {
    return new InstitutionPoints.CreatorAffiliationPoints(
        randomUri(), affiliationId, randomBigDecimal(scale));
  }

  private static PublicationChannel createLevelOneJournal() {
    return new PublicationChannel(randomUri(), ChannelType.JOURNAL, ScientificValue.LEVEL_ONE);
  }
}
