package no.sikt.nva.nvi.index.utils;

import static no.sikt.nva.nvi.common.EnvironmentFixtures.SEARCH_INFRASTRUCTURE_API_HOST;
import static no.sikt.nva.nvi.common.EnvironmentFixtures.SEARCH_INFRASTRUCTURE_AUTH_URI;
import static no.sikt.nva.nvi.common.EnvironmentFixtures.getGlobalEnvironment;
import static no.sikt.nva.nvi.index.utils.SearchConstants.getSearchInfrastructureApiHost;
import static no.sikt.nva.nvi.index.utils.SearchConstants.getSearchInfrastructureAuthUri;
import static org.assertj.core.api.Assertions.assertThat;

import java.net.URI;
import org.junit.jupiter.api.Test;

class SearchConstantsTest {

  @Test
  void shouldReadSearchInfrastructureApiHostFromEnvironmentAsUri() {
    var expectedApiHost = URI.create(SEARCH_INFRASTRUCTURE_API_HOST.getValue());
    assertThat(getSearchInfrastructureApiHost(getGlobalEnvironment())).isEqualTo(expectedApiHost);
  }

  @Test
  void shouldReadSearchInfrastructureAuthUriFromEnvironmentAsUri() {
    var expectedAuthUri = URI.create(SEARCH_INFRASTRUCTURE_AUTH_URI.getValue());
    assertThat(getSearchInfrastructureAuthUri(getGlobalEnvironment())).isEqualTo(expectedAuthUri);
  }
}
