package no.sikt.nva.nvi.common.service.model;

import static no.sikt.nva.nvi.common.model.PointCalculationFixtures.randomPointCalculation;
import static no.unit.nva.testutils.RandomDataGenerator.randomUri;
import static org.junit.jupiter.api.Assertions.assertEquals;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;
import no.sikt.nva.nvi.common.db.CandidateDao;
import no.sikt.nva.nvi.common.model.PointCalculationBuilder;
import org.junit.jupiter.api.Test;

class InstitutionPointsTest {

  @Test
  void shouldParseEntityWithNoCreatorPoints() {
    var institutionId = randomUri();
    var dbPoints =
        List.of(new CandidateDao.DbInstitutionPoints(institutionId, BigDecimal.ZERO, null));
    var expectedPoints = new InstitutionPoints(institutionId, BigDecimal.ZERO, List.of());
    var actualPoints = InstitutionPoints.from(dbPoints.getFirst());
    assertEquals(expectedPoints, actualPoints);
  }

  @Test
  void shouldBeEqualDespiteDifferentInputScales() {

    var originalPoints = randomPointCalculation(2);

    var newScale = 4;
    var pointsWithScale4 =
        new PointCalculationBuilder(originalPoints)
            .withBasePoints(adjustScale(originalPoints.basePoints(), newScale))
            .withTotalPoints(adjustScale(originalPoints.totalPoints(), newScale))
            .build();

    assertEquals(originalPoints, pointsWithScale4);
  }

  private static BigDecimal adjustScale(BigDecimal bigDecimal, int scale) {
    return bigDecimal.setScale(scale, RoundingMode.HALF_UP);
  }
}
