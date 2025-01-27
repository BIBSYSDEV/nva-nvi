package no.sikt.nva.nvi.common.service.model;

import static no.unit.nva.testutils.RandomDataGenerator.randomUri;
import static org.junit.jupiter.api.Assertions.assertEquals;

import java.math.BigDecimal;
import java.util.List;
import no.sikt.nva.nvi.common.db.CandidateDao;
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
}
