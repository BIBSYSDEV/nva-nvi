package no.sikt.nva.nvi.common.db;

import static no.sikt.nva.nvi.common.db.model.Username.fromUserName;
import static no.sikt.nva.nvi.common.model.UsernameFixtures.randomUsername;

import no.sikt.nva.nvi.common.db.model.Username;

public class UsernameFixtures {

  public static Username randomDbUsername() {
    return fromUserName(randomUsername());
  }
}
