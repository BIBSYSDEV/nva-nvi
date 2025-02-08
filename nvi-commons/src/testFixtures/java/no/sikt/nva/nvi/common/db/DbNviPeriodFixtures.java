package no.sikt.nva.nvi.common.db;

import static no.sikt.nva.nvi.common.db.UsernameFixtures.randomUsername;
import static no.sikt.nva.nvi.test.TestUtils.randomYear;

import java.time.Instant;
import no.sikt.nva.nvi.common.db.NviPeriodDao.DbNviPeriod;

public class DbNviPeriodFixtures {

  public static DbNviPeriod.Builder randomNviPeriodBuilder() {
    return DbNviPeriod.builder()
        .createdBy(randomUsername())
        .modifiedBy(randomUsername())
        .reportingDate(getNowWithMillisecondAccuracy())
        .publishingYear(randomYear());
  }

  private static Instant getNowWithMillisecondAccuracy() {
    var now = Instant.now();
    return Instant.ofEpochMilli(now.toEpochMilli());
  }
}
