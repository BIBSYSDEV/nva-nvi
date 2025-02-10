package no.sikt.nva.nvi.common.db;

import static no.unit.nva.testutils.RandomDataGenerator.randomString;

import no.sikt.nva.nvi.common.db.model.Username;

public class UsernameFixtures {

  public static Username randomUsername() {
    return Username.fromString(randomString());
  }
}
