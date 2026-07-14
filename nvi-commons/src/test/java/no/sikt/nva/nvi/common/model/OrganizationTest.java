package no.sikt.nva.nvi.common.model;

import static no.sikt.nva.nvi.common.model.OrganizationFixtures.createOrganizationHierarchy;
import static no.sikt.nva.nvi.common.model.OrganizationFixtures.randomOrganization;
import static no.sikt.nva.nvi.common.model.OrganizationFixtures.randomOrganizationId;
import static no.sikt.nva.nvi.common.model.OrganizationFixtures.randomOrganizationWithPartOf;
import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.Collections;
import java.util.Set;
import no.sikt.nva.nvi.common.client.model.Organization;
import org.junit.jupiter.api.Test;

class OrganizationTest {

  @Test
  void shouldFindAncestorsOfNestedOrganization() {
    var topLevelId = randomOrganizationId();
    var departmentId = randomOrganizationId();
    var subDepartmentId = randomOrganizationId();
    var groupId = randomOrganizationId();
    var topLevelOrganization =
        createOrganizationHierarchy(topLevelId, departmentId, subDepartmentId, groupId);

    var ancestors = topLevelOrganization.findAncestorsOf(groupId);

    assertThat(ancestors).contains(Set.of(subDepartmentId, departmentId, topLevelId));
  }

  @Test
  void shouldFindEmptyAncestorsWhenOrganizationIsTheTopLevelItself() {
    var organization = randomOrganization().build();

    var ancestors = organization.findAncestorsOf(organization.id());

    assertThat(ancestors).contains(Collections.emptySet());
  }

  @Test
  void shouldNotFindAncestorsWhenOrganizationIsNotInTree() {
    var topLevelOrganization =
        createOrganizationHierarchy(
            randomOrganizationId(), randomOrganizationId(), randomOrganizationId());

    var ancestors = topLevelOrganization.findAncestorsOf(randomOrganizationId());

    assertThat(ancestors).isEmpty();
  }

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
