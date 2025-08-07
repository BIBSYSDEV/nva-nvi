package no.sikt.nva.nvi.common.model;

import static java.util.Collections.emptyList;
import static java.util.Objects.isNull;
import static java.util.Objects.nonNull;
import static no.sikt.nva.nvi.common.EnvironmentFixtures.API_HOST;
import static no.sikt.nva.nvi.test.TestConstants.COUNTRY_CODE_NORWAY;
import static no.sikt.nva.nvi.test.TestUtils.generateUniqueIdAsString;
import static no.sikt.nva.nvi.test.TestUtils.randomUriWithSuffix;
import static no.unit.nva.commons.json.JsonUtils.dtoObjectMapper;
import static no.unit.nva.testutils.RandomDataGenerator.randomString;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.core.JsonProcessingException;
import java.net.URI;
import java.net.http.HttpResponse;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.stream.IntStream;
import java.util.stream.Stream;
import no.sikt.nva.nvi.common.client.model.Organization;
import no.sikt.nva.nvi.common.client.model.Organization.Builder;
import no.unit.nva.auth.uriretriever.UriRetriever;
import nva.commons.core.paths.UriWrapper;

public class OrganizationFixtures {

  public static String randomOrganizationIdentifier() {
    return String.format(
        "%s.%s.%s.%s",
        generateUniqueIdAsString(),
        generateUniqueIdAsString(),
        generateUniqueIdAsString(),
        generateUniqueIdAsString());
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
        Organization.builder()
            .withId(subUnitId)
            .withPartOf(List.of(Organization.builder().withId(topLevelOrgId).build()))
            .build();
    return Organization.builder()
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

  public static Organization setupRandomOrganization(
      String countryCode, int numberOfSubOrganizations, UriRetriever uriRetriever) {
    var topLevelOrganization = randomOrganization(countryCode, numberOfSubOrganizations).build();
    var subOrganizationIds = topLevelOrganization.hasPart().stream().map(Organization::id).toList();
    mockOrganizationResponseForAffiliations(
        topLevelOrganization.id(), subOrganizationIds, uriRetriever);
    return topLevelOrganization;
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

  public static void mockOrganizationResponseForAffiliation(
      URI topLevelInstitutionId, URI subUnitId, UriRetriever uriRetriever) {
    var subUnits = new ArrayList<Organization>();
    if (nonNull(subUnitId)) {
      var topLevelOrganizationAsLeafNode =
          Organization.builder().withId(topLevelInstitutionId).build();
      var subUnitOrganization =
          Organization.builder()
              .withId(subUnitId)
              .withPartOf(List.of(topLevelOrganizationAsLeafNode))
              .build();
      mockOrganizationResponse(subUnitOrganization, uriRetriever);
      subUnits.add(subUnitOrganization);
    }
    var topLevelOrganization =
        Organization.builder().withId(topLevelInstitutionId).withHasPart(subUnits).build();
    mockOrganizationResponse(topLevelOrganization, uriRetriever);
  }

  public static void mockOrganizationResponseForAffiliations(
      URI topLevelInstitutionId, Collection<URI> subUnitIds, UriRetriever uriRetriever) {
    var leafNode = Organization.builder().withId(topLevelInstitutionId).build();
    var subUnits =
        subUnitIds.stream()
            .map(subUnitId -> mockedSubOrganization(leafNode, subUnitId, uriRetriever))
            .toList();
    var topLevelOrganization =
        Organization.builder().withId(topLevelInstitutionId).withHasPart(subUnits).build();
    mockOrganizationResponse(topLevelOrganization, uriRetriever);
  }

  private static Organization mockedSubOrganization(
      Organization topLevelOrganization, URI subUnitId, UriRetriever uriRetriever) {
    var subUnitOrganization =
        Organization.builder().withId(subUnitId).withPartOf(List.of(topLevelOrganization)).build();
    mockOrganizationResponse(subUnitOrganization, uriRetriever);
    return subUnitOrganization;
  }

  private static void mockOrganizationResponse(
      Organization organization, UriRetriever uriRetriever) {
    var body = generateResponseBody(organization);
    var response = Optional.of(createResponse(200, body));
    when(uriRetriever.fetchResponse(eq(organization.id()), any())).thenReturn(response);
  }

  private static String generateResponseBody(Organization organization) {
    try {
      return dtoObjectMapper.writeValueAsString(organization);
    } catch (JsonProcessingException e) {
      throw new RuntimeException(e);
    }
  }

  private static HttpResponse<String> createResponse(int status, String body) {
    var response = (HttpResponse<String>) mock(HttpResponse.class);
    when(response.statusCode()).thenReturn(status);
    when(response.body()).thenReturn(body);
    return response;
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
