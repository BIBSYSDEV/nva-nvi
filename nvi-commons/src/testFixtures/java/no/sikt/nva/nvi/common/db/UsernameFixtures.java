package no.sikt.nva.nvi.common.db;

import static no.unit.nva.testutils.RandomDataGenerator.FAKER;

import no.sikt.nva.nvi.common.db.model.Username;

public class UsernameFixtures {

  public static Username randomUsername() {
    var organizationIdentifier = FAKER.numerify("###.###.###.###");
    return randomUsername(organizationIdentifier);
  }

  public static Username randomUsername(String organizationIdentifier) {
    var username = FAKER.numerify("#######");
    return Username.fromString(String.format("%s@%s", username, organizationIdentifier));
  }
}
