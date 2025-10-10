package no.sikt.nva.nvi.common.db;

import static java.util.UUID.randomUUID;
import static no.sikt.nva.nvi.common.db.UsernameFixtures.randomUsername;
import static no.sikt.nva.nvi.test.TestUtils.randomYear;
import static no.unit.nva.testutils.RandomDataGenerator.randomInstant;
import static no.unit.nva.testutils.RandomDataGenerator.randomString;
import static no.unit.nva.testutils.RandomDataGenerator.randomUri;

import java.time.Instant;
import no.sikt.nva.nvi.common.db.NviPeriodDao.DbNviPeriod;

public class DbNviPeriodFixtures {

  public static NviPeriodDao randomPeriodDao() {
    return new NviPeriodDao(
        randomUUID().toString(),
        new DbNviPeriod(
            randomUri(),
            randomString(),
            randomInstant(),
            randomInstant(),
            randomUsername(),
            randomUsername()),
        randomUUID().toString());
  }

  public static NviPeriodDao.Builder randomPeriodDaoBuilder() {
    var dbPeriod = randomNviPeriodBuilder().build();
    return NviPeriodDao.builder()
        .identifier(dbPeriod.publishingYear())
        .nviPeriod(dbPeriod)
        .version(randomUUID().toString());
  }

  public static DbNviPeriod.Builder randomNviPeriodBuilder() {
    return DbNviPeriod.builder()
        .id(randomUri())
        .createdBy(randomUsername())
        .modifiedBy(randomUsername())
        .startDate(randomInstant())
        .reportingDate(getNowWithMillisecondAccuracy())
        .publishingYear(randomYear());
  }

  private static Instant getNowWithMillisecondAccuracy() {
    var now = Instant.now();
    return Instant.ofEpochMilli(now.toEpochMilli());
  }
}
