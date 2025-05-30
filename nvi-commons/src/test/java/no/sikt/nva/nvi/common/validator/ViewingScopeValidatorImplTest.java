package no.sikt.nva.nvi.common.validator;

import static no.unit.nva.commons.json.JsonUtils.dtoObjectMapper;
import static no.unit.nva.testutils.RandomDataGenerator.randomUri;
import static nva.commons.core.attempt.Try.attempt;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.JsonNode;
import java.net.URI;
import java.util.List;
import no.sikt.nva.nvi.common.client.OrganizationRetriever;
import no.sikt.nva.nvi.common.client.model.Organization;
import no.sikt.nva.nvi.common.client.model.Organization.Builder;
import no.unit.nva.clients.IdentityServiceClient;
import no.unit.nva.clients.UserDto;
import no.unit.nva.clients.UserDto.ViewingScope;
import nva.commons.apigateway.exceptions.NotFoundException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class ViewingScopeValidatorImplTest {

  private static final String SOME_USERNAME = "username";
  private OrganizationRetriever organizationRetriever;
  private IdentityServiceClient identityServiceClient;
  private ViewingScopeValidatorImpl viewingScopeValidator;

  @BeforeEach
  void setUp() {
    organizationRetriever = mock(OrganizationRetriever.class);
    identityServiceClient = mock(IdentityServiceClient.class);
    viewingScopeValidator =
        new ViewingScopeValidatorImpl(identityServiceClient, organizationRetriever);
  }

  @Test
  void shouldReturnFalseWhenUserIsNotAllowedToAccessAllOrgs() throws NotFoundException {
    var allowedOrg = randomUri();
    when(identityServiceClient.getUser(SOME_USERNAME)).thenReturn(userWithViewingScope(allowedOrg));
    when(organizationRetriever.fetchOrganization(allowedOrg)).thenReturn(createOrg(allowedOrg));
    var someOtherOrg = randomUri();
    assertFalse(
        viewingScopeValidator.userIsAllowedToAccessAll(
            SOME_USERNAME, List.of(allowedOrg, someOtherOrg)));
  }

  @Test
  void shouldReturnTrueWhenUserIsAllowedToAccessAllOrgs() throws NotFoundException {
    var allowedOrg = randomUri();
    when(identityServiceClient.getUser(SOME_USERNAME)).thenReturn(userWithViewingScope(allowedOrg));
    when(organizationRetriever.fetchOrganization(allowedOrg)).thenReturn(createOrg(allowedOrg));
    assertTrue(viewingScopeValidator.userIsAllowedToAccessAll(SOME_USERNAME, List.of(allowedOrg)));
  }

  @Test
  void shouldReturnTrueWhenUserIsAllowedToAccessOrgsSubOrg() throws NotFoundException {
    var org = URI.create("https://www.example.com/org");
    var subOrg = URI.create("https://www.example.com/subOrg");
    when(identityServiceClient.getUser(SOME_USERNAME)).thenReturn(userWithViewingScope(org));
    when(organizationRetriever.fetchOrganization(org)).thenReturn(createOrgWithSubOrg(org, subOrg));
    assertTrue(viewingScopeValidator.userIsAllowedToAccessAll(SOME_USERNAME, List.of(subOrg)));
  }

  @Test
  void shouldReturnTrueWhenUserIsAllowedToAccessOneOfOrgs() throws NotFoundException {
    var allowedOrg = randomUri();
    when(identityServiceClient.getUser(SOME_USERNAME)).thenReturn(userWithViewingScope(allowedOrg));
    when(organizationRetriever.fetchOrganization(allowedOrg)).thenReturn(createOrg(allowedOrg));
    var someOtherOrg = randomUri();
    assertTrue(
        viewingScopeValidator.userIsAllowedToAccessOneOf(
            SOME_USERNAME, List.of(allowedOrg, someOtherOrg)));
  }

  @Test
  void shouldReturnTrueWhenUserIsAllowedToAccessOneOfOrgsSubOrg() throws NotFoundException {
    var org = URI.create("https://www.example.com/org");
    var subOrg = URI.create("https://www.example.com/subOrg");
    when(identityServiceClient.getUser(SOME_USERNAME)).thenReturn(userWithViewingScope(org));
    when(organizationRetriever.fetchOrganization(org)).thenReturn(createOrgWithSubOrg(org, subOrg));
    var someOtherOrg = randomUri();
    assertTrue(
        viewingScopeValidator.userIsAllowedToAccessOneOf(
            SOME_USERNAME, List.of(subOrg, someOtherOrg)));
  }

  private static Organization createOrg(URI orgId) {
    return defaultBuilder(orgId).build();
  }

  private static Organization createOrgWithSubOrg(URI orgId, URI subOrgId) {
    return defaultBuilder(orgId).withHasPart(List.of(createOrg(subOrgId))).build();
  }

  private static Builder defaultBuilder(URI organizationId) {
    return Organization.builder().withId(organizationId).withContext(getOrganizationContext());
  }

  private static UserDto userWithViewingScope(URI allowedOrg) {
    return UserDto.builder()
        .withViewingScope(ViewingScope.builder().withIncludedUnits(List.of(allowedOrg)).build())
        .build();
  }

  private static JsonNode getOrganizationContext() {
    var context =
        """
        {
          "@vocab": "https://bibsysdev.github.io/src/organization-ontology.ttl#",
          "Organization": "https://nva.sikt.no/ontology/publication#Organization",
          "id": "@id",
          "type": "@type",
          "name": {
            "@id": "https://nva.sikt.no/ontology/publication#name",
            "@container": "@language"
          },
          "hasPart": {
            "@id": "https://nva.sikt.no/ontology/publication#hasPart",
            "@container": "@set"
          },
          "labels": {
            "@id": "https://nva.sikt.no/ontology/publication#label",
            "@container": "@language"
          },
          "partOf": {
            "@id": "https://nva.sikt.no/ontology/publication#partOf",
            "@container": "@set"
          }
        }
        """;
    return attempt(() -> dtoObjectMapper.readTree(context)).orElseThrow();
  }
}
