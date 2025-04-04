package no.sikt.nva.nvi.common.model;

import static java.util.Objects.nonNull;
import static no.sikt.nva.nvi.test.TestConstants.COUNTRY_CODE_NORWAY;
import static no.sikt.nva.nvi.test.TestUtils.randomUriWithSuffix;
import static no.unit.nva.commons.json.JsonUtils.dtoObjectMapper;
import static no.unit.nva.testutils.RandomDataGenerator.randomString;
import static no.unit.nva.testutils.RandomDataGenerator.randomUri;
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
import java.util.Map;
import java.util.Optional;
import java.util.stream.IntStream;
import no.sikt.nva.nvi.common.client.model.Organization;
import no.sikt.nva.nvi.common.client.model.Organization.Builder;
import no.unit.nva.auth.uriretriever.UriRetriever;

public class OrganizationFixtures {

  public static Organization randomOrganizationWithPartOf(Organization topLevelOrg) {
    return randomOrganization().withPartOf(List.of(topLevelOrg)).build();
  }

  public static Builder randomOrganization() {
    return Organization.builder()
        .withId(randomUri())
        .withCountryCode(COUNTRY_CODE_NORWAY)
        .withLabels(Map.of(randomString(), randomString()));
  }

  public static Builder randomOrganizationWithSubUnits(int numberOfSubUnits) {
    var topLevelOrganizationId = randomUriWithSuffix("topLevel");
    var topLevelLeafNode = Organization.builder().withId(topLevelOrganizationId).build();
    var subOrganizations =
        IntStream.range(0, numberOfSubUnits)
            .mapToObj(
                i ->
                    randomOrganization()
                        .withId(randomUriWithSuffix("subUnit"))
                        .withPartOf(List.of(topLevelLeafNode))
                        .build())
            .toList();

    return randomOrganization().withId(topLevelOrganizationId).withHasPart(subOrganizations);
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
}
