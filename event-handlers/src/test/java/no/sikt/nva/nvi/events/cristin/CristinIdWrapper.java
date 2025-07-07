package no.sikt.nva.nvi.events.cristin;

import static no.sikt.nva.nvi.common.model.OrganizationFixtures.getAsOrganizationLeafNode;
import static no.sikt.nva.nvi.common.model.OrganizationFixtures.organizationIdFromIdentifier;
import static no.sikt.nva.nvi.test.TestConstants.COUNTRY_CODE_NORWAY;

import java.net.URI;
import java.util.List;
import no.sikt.nva.nvi.common.client.model.Organization;

public record CristinIdWrapper(
    String institutionIdentifier,
    String departmentIdentifier,
    String subDepartmentIdentifier,
    String groupIdentifier) {

  public static CristinIdWrapper from(CristinLocale cristinLocale) {
    return new CristinIdWrapper(
        cristinLocale.getInstitutionIdentifier(),
        cristinLocale.getDepartmentIdentifier(),
        cristinLocale.getSubDepartmentIdentifier(),
        cristinLocale.getGroupIdentifier());
  }

  public static CristinIdWrapper from(ScientificPerson scientificPerson) {
    return new CristinIdWrapper(
        scientificPerson.getInstitutionIdentifier(),
        scientificPerson.getDepartmentIdentifier(),
        scientificPerson.getSubDepartmentIdentifier(),
        scientificPerson.getGroupIdentifier());
  }

  public String getInstitutionIdentifier() {
    return organizationIdentifierFromUnitIdentifiers(institutionIdentifier, "0", "0", "0");
  }

  public URI getInstitutionId() {
    return organizationIdFromIdentifier(getInstitutionIdentifier());
  }

  public String getDepartmentIdentifier() {
    return organizationIdentifierFromUnitIdentifiers(
        institutionIdentifier, departmentIdentifier, "0", "0");
  }

  public URI getDepartmentId() {
    return organizationIdFromIdentifier(getDepartmentIdentifier());
  }

  public String getSubDepartmentIdentifier() {
    return organizationIdentifierFromUnitIdentifiers(
        institutionIdentifier, departmentIdentifier, subDepartmentIdentifier, "0");
  }

  public URI getSubDepartmentId() {
    return organizationIdFromIdentifier(getSubDepartmentIdentifier());
  }

  public String getGroupIdentifier() {
    return organizationIdentifierFromUnitIdentifiers(
        institutionIdentifier, departmentIdentifier, subDepartmentIdentifier, groupIdentifier);
  }

  public URI getGroupId() {
    return organizationIdFromIdentifier(getGroupIdentifier());
  }

  public Organization getTopLevelOrganization() {
    var builder = Organization.builder().withCountryCode(COUNTRY_CODE_NORWAY);
    var group =
        builder
            .withId(getGroupId())
            .withPartOf(List.of(getAsOrganizationLeafNode(getSubDepartmentId())))
            .build();
    var subDepartment =
        builder
            .withId(getSubDepartmentId())
            .withHasPart(List.of(group))
            .withPartOf(List.of(getAsOrganizationLeafNode(getDepartmentId())))
            .build();
    var department =
        builder
            .withId(getDepartmentId())
            .withHasPart(List.of(subDepartment))
            .withPartOf(List.of(getAsOrganizationLeafNode(getInstitutionId())))
            .build();
    return builder.withId(getInstitutionId()).withHasPart(List.of(department)).build();
  }

  public Organization createLeafNodeOrganization() {
    var builder = Organization.builder().withCountryCode(COUNTRY_CODE_NORWAY);
    var topLevelOrganization =
        builder
            .withId(getInstitutionId())
            .withHasPart(List.of(getAsOrganizationLeafNode(getDepartmentId())))
            .build();
    var department =
        builder
            .withId(getDepartmentId())
            .withHasPart(List.of(getAsOrganizationLeafNode(getSubDepartmentId())))
            .withPartOf(List.of(topLevelOrganization))
            .build();
    var subDepartment =
        builder
            .withId(getSubDepartmentId())
            .withHasPart(List.of(getAsOrganizationLeafNode(getGroupId())))
            .withPartOf(List.of(department))
            .build();
    return builder.withId(getGroupId()).withPartOf(List.of(subDepartment)).build();
  }

  private static String organizationIdentifierFromUnitIdentifiers(
      String topLevelIdentifier,
      String departmentIdentifier,
      String subDepartmentIdentifier,
      String groupIdentifier) {
    return String.format(
        "%s.%s.%s.%s",
        topLevelIdentifier, departmentIdentifier, subDepartmentIdentifier, groupIdentifier);
  }
}
