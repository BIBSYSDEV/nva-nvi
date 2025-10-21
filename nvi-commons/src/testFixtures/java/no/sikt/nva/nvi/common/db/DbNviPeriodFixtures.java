package no.sikt.nva.nvi.common.db;

import static java.util.UUID.randomUUID;
import static no.sikt.nva.nvi.common.db.UsernameFixtures.randomDbUsername;
import static no.unit.nva.testutils.RandomDataGenerator.randomInstant;
import static no.unit.nva.testutils.RandomDataGenerator.randomString;
import static no.unit.nva.testutils.RandomDataGenerator.randomUri;

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
            randomDbUsername(),
            randomDbUsername()),
        randomUUID().toString());
  }
}
