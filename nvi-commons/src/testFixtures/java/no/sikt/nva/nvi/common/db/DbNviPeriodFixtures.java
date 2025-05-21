package no.sikt.nva.nvi.common.db;

import static no.sikt.nva.nvi.common.db.UsernameFixtures.randomUsername;
import static no.sikt.nva.nvi.test.TestUtils.randomYear;
import static no.unit.nva.testutils.RandomDataGenerator.randomInstant;
import static no.unit.nva.testutils.RandomDataGenerator.randomString;
import static no.unit.nva.testutils.RandomDataGenerator.randomUri;

import java.time.Instant;
import java.util.UUID;
import no.sikt.nva.nvi.common.db.NviPeriodDao.DbNviPeriod;

public class DbNviPeriodFixtures {

  public static NviPeriodDao randomPeriodDao() {
    return new NviPeriodDao(
        UUID.randomUUID().toString(),
        new DbNviPeriod(
            randomUri(),
            randomString(),
            randomInstant(),
            randomInstant(),
            randomUsername(),
            randomUsername()),
        UUID.randomUUID().toString());
  }

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
