package no.sikt.nva.nvi.common.service.model;

import static java.util.Collections.emptyList;
import static no.sikt.nva.nvi.common.model.PointCalculationFixtures.randomPointCalculation;
import static no.unit.nva.testutils.RandomDataGenerator.randomBoolean;
import static no.unit.nva.testutils.RandomDataGenerator.randomUri;
import static org.junit.jupiter.api.Assertions.assertEquals;

import java.math.BigDecimal;
import java.math.RoundingMode;
import no.sikt.nva.nvi.common.db.CandidateDao.DbInstitutionPoints;
import no.sikt.nva.nvi.common.model.PointCalculationBuilder;
import no.sikt.nva.nvi.common.model.Sector;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class InstitutionPointsTest {

  @Test
  void shouldParseEntityWithMissingFields() {
    var institutionId = randomUri();
    var rboInstitution = randomBoolean();
    var dbPoints =
        new DbInstitutionPoints(institutionId, BigDecimal.ZERO, null, rboInstitution, null);
    var expectedPoints =
        new InstitutionPoints(
            institutionId, BigDecimal.ZERO, Sector.UNKNOWN, rboInstitution, emptyList());
    var actualPoints = InstitutionPoints.from(dbPoints);
    assertEquals(expectedPoints, actualPoints);
  }

  @Test
  void shouldPersistInstitutionSector() {
    var originalPoints =
        new InstitutionPoints(
            randomUri(), BigDecimal.ZERO, Sector.UHI, randomBoolean(), emptyList());
    var dbPoints = DbInstitutionPoints.from(originalPoints);
    var roundTrippedPoints = InstitutionPoints.from(dbPoints);
    assertEquals(originalPoints, roundTrippedPoints);
  }

  @ParameterizedTest
  @ValueSource(booleans = {true, false})
  void shouldPersistInstitutionRboStatus(boolean rboInstitution) {
    var originalPoints =
        new InstitutionPoints(
            randomUri(), BigDecimal.ZERO, Sector.UHI, rboInstitution, emptyList());

    var roundTrippedPoints = InstitutionPoints.from(DbInstitutionPoints.from(originalPoints));

    assertEquals(rboInstitution, roundTrippedPoints.rboInstitution());
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
