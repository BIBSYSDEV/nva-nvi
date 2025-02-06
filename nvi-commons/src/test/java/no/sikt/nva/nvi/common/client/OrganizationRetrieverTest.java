package no.sikt.nva.nvi.common.client;

import static no.sikt.nva.nvi.common.client.MockHttpResponseUtil.createResponse;
import static no.sikt.nva.nvi.common.model.OrganizationFixtures.randomOrganization;
import static no.sikt.nva.nvi.common.model.OrganizationFixtures.randomOrganizationWithPartOf;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.Optional;
import no.sikt.nva.nvi.common.client.model.Organization;
import no.unit.nva.auth.uriretriever.UriRetriever;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class OrganizationRetrieverTest {

  private UriRetriever uriRetriever;
  private OrganizationRetriever organizationRetriever;

  @BeforeEach
  void setUp() {
    uriRetriever = mock(UriRetriever.class);
    organizationRetriever = new OrganizationRetriever(uriRetriever);
  }

  @Test
  void shouldFetchOrganization() {
    var expectedOrganization = randomOrganizationWithPartOf(randomOrganization().build());
    mockUriRetriever(expectedOrganization);
    var actualOrganization = organizationRetriever.fetchOrganization(expectedOrganization.id());
    assertEquals(expectedOrganization, actualOrganization);
  }

  private void mockUriRetriever(Organization expectedOrganization) {
    var response = createResponse(expectedOrganization.toJsonString());
    when(uriRetriever.fetchResponse(eq(expectedOrganization.id()), any()))
        .thenReturn(Optional.of(response));
  }
}
