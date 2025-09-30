package no.sikt.nva.nvi.common.model;

import static no.sikt.nva.nvi.common.db.UsernameFixtures.randomUsername;
import static no.sikt.nva.nvi.common.service.model.Username.fromUserName;
import static nva.commons.apigateway.AccessRight.MANAGE_NVI;
import static nva.commons.apigateway.AccessRight.MANAGE_NVI_CANDIDATES;

import java.net.URI;
import java.util.Set;

public final class UserInstanceFixtures {

  private UserInstanceFixtures() {}

  public static UserInstance createCuratorUserInstance(URI topLevelOrganizationId) {
    var username = fromUserName(randomUsername());
    return new UserInstance(username, topLevelOrganizationId, Set.of(MANAGE_NVI_CANDIDATES));
  }

  public static UserInstance createAdminUserInstance(URI topLevelOrganizationId) {
    var username = fromUserName(randomUsername());
    return new UserInstance(username, topLevelOrganizationId, Set.of(MANAGE_NVI));
  }
}
