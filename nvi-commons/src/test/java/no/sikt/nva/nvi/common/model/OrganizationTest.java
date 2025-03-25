package no.sikt.nva.nvi.common.model;

import static no.sikt.nva.nvi.common.model.OrganizationFixtures.randomOrganization;
import static no.sikt.nva.nvi.common.model.OrganizationFixtures.randomOrganizationWithPartOf;
import static org.junit.jupiter.api.Assertions.assertEquals;

import no.sikt.nva.nvi.common.client.model.Organization;
import org.junit.jupiter.api.Test;

class OrganizationTest {

  @Test
  void shouldReturnDeepestPartOfAsTopLevelOrg() {
    var topLevelOrg = randomOrganization().build();
    var organization = randomOrganizationWithPartOf(topLevelOrg);
    var actualTopLevelOrg = organization.getTopLevelOrg();
    assertEquals(topLevelOrg, actualTopLevelOrg);
  }

  @Test
  void shouldReturnSelfAsTopLevelOrgWhenNoPartOf() {
    var organization = randomOrganization().build();
    var actualTopLevelOrg = organization.getTopLevelOrg();
    assertEquals(organization, actualTopLevelOrg);
  }

  @Test
  void shouldSerializeAndDeserializeWithoutLossOfData() throws Exception {
    var organization = randomOrganizationWithPartOf(randomOrganization().build());
    var json = organization.toJsonString();
    var actualOrganization = Organization.from(json);
    assertEquals(organization, actualOrganization);
  }
}
