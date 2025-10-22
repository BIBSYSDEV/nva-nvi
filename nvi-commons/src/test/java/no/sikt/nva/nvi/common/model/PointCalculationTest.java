package no.sikt.nva.nvi.common.model;

import static no.unit.nva.testutils.RandomDataGenerator.randomUri;
import static org.junit.jupiter.api.Assertions.assertEquals;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;
import no.sikt.nva.nvi.common.service.model.InstitutionPoints;
import org.junit.jupiter.api.Test;

class PointCalculationTest {

  @Test
  void shouldBeEqualDespiteDifferentInputScales() {
    var institutionId = randomUri();
    var numberWithScale2 = BigDecimal.valueOf(1.23).setScale(2, RoundingMode.HALF_UP);
    var numberWithScale4 = BigDecimal.valueOf(1.23).setScale(4, RoundingMode.HALF_UP);

    var creatorId = randomUri();
    var creator1 =
        new InstitutionPoints.CreatorAffiliationPoints(creatorId, institutionId, numberWithScale2);
    var creator2 =
        new InstitutionPoints.CreatorAffiliationPoints(creatorId, institutionId, numberWithScale4);

    var point1 = new InstitutionPoints(institutionId, numberWithScale2, List.of(creator1));
    var point2 = new InstitutionPoints(institutionId, numberWithScale4, List.of(creator2));
    assertEquals(point1, point2);
  }
}
