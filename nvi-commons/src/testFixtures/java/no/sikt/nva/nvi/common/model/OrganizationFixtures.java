package no.sikt.nva.nvi.common.model;

import static java.util.Collections.emptyList;
import static java.util.Objects.isNull;
import static java.util.Objects.nonNull;
import static no.sikt.nva.nvi.common.EnvironmentFixtures.API_HOST;
import static no.sikt.nva.nvi.test.TestConstants.COUNTRY_CODE_NORWAY;
import static no.sikt.nva.nvi.test.TestUtils.randomUriWithSuffix;
import static no.unit.nva.testutils.RandomDataGenerator.FAKER;
import static no.unit.nva.testutils.RandomDataGenerator.randomString;

import java.net.URI;
import java.util.Collection;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.stream.IntStream;
import java.util.stream.Stream;
import no.sikt.nva.nvi.common.client.model.Organization;
import no.sikt.nva.nvi.common.client.model.Organization.Builder;
import nva.commons.core.paths.UriWrapper;

public class OrganizationFixtures {

  public static String randomOrganizationIdentifier() {
    return FAKER.numerify("###.###.###.###");
  }

  public static URI randomOrganizationId() {
    return organizationIdFromIdentifier(randomOrganizationIdentifier());
  }

  public static URI organizationIdFromIdentifier(String identifier) {
    return UriWrapper.fromHost(API_HOST.getValue())
        .addChild("cristin")
        .addChild("organization")
        .addChild(identifier)
        .getUri();
  }

  public static Organization randomTopLevelOrganization() {
    return randomOrganization(COUNTRY_CODE_NORWAY, 2).build();
  }

  public static Organization randomOrganizationWithPartOf(Organization topLevelOrg) {
    return randomOrganization().withPartOf(List.of(topLevelOrg)).build();
  }

  public static Organization createOrganizationWithSubUnit(URI topLevelOrgId, URI subUnitId) {
    var subOrganization =
        randomOrganization()
            .withId(subUnitId)
            .withPartOf(List.of(Organization.builder().withId(topLevelOrgId).build()))
            .build();
    return randomOrganization()
        .withId(topLevelOrgId)
        .withHasPart(List.of(subOrganization))
        .withCountryCode(COUNTRY_CODE_NORWAY)
        .build();
  }

  public static Builder randomOrganization() {
    return Organization.builder()
        .withId(randomOrganizationId())
        .withCountryCode(COUNTRY_CODE_NORWAY)
        .withLabels(Map.of("nb", randomString(), "en", randomString()));
  }

  public static Builder randomOrganization(String countryCode) {
    return Organization.builder()
        .withId(randomOrganizationId())
        .withCountryCode(countryCode)
        .withLabels(Map.of("nb", randomString(), "en", randomString()));
  }

  public static Builder randomOrganization(String countryCode, int numberOfSubOrganizations) {
    var topLevelOrganizationId = randomUriWithSuffix("topLevelOrganization");
    var topLevelLeafNode = Organization.builder().withId(topLevelOrganizationId).build();
    var subOrganizations =
        IntStream.range(0, numberOfSubOrganizations)
            .mapToObj(
                i ->
                    randomOrganization()
                        .withId(randomUriWithSuffix("subOrganization"))
                        .withCountryCode(countryCode)
                        .withPartOf(List.of(topLevelLeafNode))
                        .withLabels(
                            (i % 2 == 0 && nonNull(countryCode))
                                ? Map.of(countryCode.toLowerCase(Locale.ROOT), randomString())
                                : null)
                        .build())
            .toList();

    return randomOrganization()
        .withId(topLevelOrganizationId)
        .withCountryCode(countryCode)
        .withHasPart(subOrganizations);
  }

  public static Organization getAsOrganizationLeafNode(URI organizationId) {
    return Organization.builder().withId(organizationId).build();
  }

  public static Organization createOrganizationHierarchy(
      URI topLevelId, URI departmentId, URI subDepartmentId, URI... groupIds) {
    var leafNodes =
        Stream.of(groupIds)
            .map(id -> createOrganization(id, subDepartmentId, emptyList()))
            .toList();
    var subDepartment = createOrganization(subDepartmentId, departmentId, leafNodes);
    var department = createOrganization(departmentId, topLevelId, List.of(subDepartment));
    return createOrganization(topLevelId, null, List.of(department));
  }

  private static Organization createOrganization(
      URI id, URI parentOrganization, Collection<Organization> subOrganizations) {
    return Organization.builder()
        .withCountryCode(COUNTRY_CODE_NORWAY)
        .withId(id)
        .withHasPart(List.copyOf(subOrganizations))
        .withPartOf(
            isNull(parentOrganization)
                ? emptyList()
                : List.of(getAsOrganizationLeafNode(parentOrganization)))
        .build();
  }
}
