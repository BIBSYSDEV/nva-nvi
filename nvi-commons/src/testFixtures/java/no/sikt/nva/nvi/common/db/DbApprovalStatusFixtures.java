package no.sikt.nva.nvi.common.db;

import static no.sikt.nva.nvi.common.db.UsernameFixtures.randomUsername;
import static no.unit.nva.testutils.RandomDataGenerator.randomElement;
import static no.unit.nva.testutils.RandomDataGenerator.randomInstant;
import static no.unit.nva.testutils.RandomDataGenerator.randomString;
import static no.unit.nva.testutils.RandomDataGenerator.randomUri;

import java.net.URI;
import no.sikt.nva.nvi.common.db.ApprovalStatusDao.DbApprovalStatus;
import no.sikt.nva.nvi.common.db.ApprovalStatusDao.DbStatus;

public class DbApprovalStatusFixtures {

  public static DbApprovalStatus randomApproval() {
    return randomApproval(randomUri());
  }

  public static DbApprovalStatus randomApproval(URI institutionId) {
    return new DbApprovalStatus(
        institutionId,
        randomElement(DbStatus.values()),
        randomUsername(),
        randomUsername(),
        randomInstant(),
        randomString());
  }
}
