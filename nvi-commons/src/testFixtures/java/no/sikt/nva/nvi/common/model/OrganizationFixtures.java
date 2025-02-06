package no.sikt.nva.nvi.common.model;

import static no.unit.nva.testutils.RandomDataGenerator.randomString;
import static no.unit.nva.testutils.RandomDataGenerator.randomUri;

import java.util.List;
import java.util.Map;
import no.sikt.nva.nvi.common.client.model.Organization;
import no.sikt.nva.nvi.common.client.model.Organization.Builder;

public class OrganizationFixtures {

  public static Builder randomOrganization() {
    return Organization.builder()
        .withId(randomUri())
        .withContext(randomString())
        .withLabels(Map.of(randomString(), randomString()))
        .withType(randomString());
  }

  public static Organization randomOrganizationWithPartOf(Organization topLevelOrg) {
    return randomOrganization().withPartOf(List.of(topLevelOrg)).build();
  }
}
