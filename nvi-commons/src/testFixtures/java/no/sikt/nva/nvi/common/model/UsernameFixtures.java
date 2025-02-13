package no.sikt.nva.nvi.common.model;

import static no.unit.nva.testutils.RandomDataGenerator.randomString;

import no.sikt.nva.nvi.common.service.model.Username;

public class UsernameFixtures {

  public static Username randomUserName() {
    return Username.fromString(randomString());
  }
}
