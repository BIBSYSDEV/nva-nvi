package no.sikt.nva.nvi.common.model;

import static no.unit.nva.testutils.RandomDataGenerator.randomString;
import static nva.commons.apigateway.AccessRight.MANAGE_NVI_CANDIDATES;

import java.net.URI;
import java.util.Set;

public final class UserInstanceFixtures {

  private UserInstanceFixtures() {}

  public static UserInstance createCuratorUserInstance(URI topLevelOrganizationId) {
    return new UserInstance(randomString(), topLevelOrganizationId, Set.of(MANAGE_NVI_CANDIDATES));
  }
}
