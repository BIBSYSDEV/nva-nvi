package no.sikt.nva.nvi.index.aws;

import static no.sikt.nva.nvi.common.utils.ApplicationConstants.DEFAULT_TIME_ZONE;
import static no.sikt.nva.nvi.index.utils.SearchConstants.SEARCH_INFRASTRUCTURE_CREDENTIALS;
import static no.sikt.nva.nvi.index.utils.SearchConstants.getSearchInfrastructureApiHost;
import static no.sikt.nva.nvi.index.utils.SearchConstants.getSearchInfrastructureAuthUri;
import static org.apache.http.HttpHeaders.AUTHORIZATION;

import java.net.http.HttpClient;
import java.time.Clock;
import no.sikt.nva.nvi.common.model.UsernamePasswordWrapper;
import no.unit.nva.auth.CachedJwtProvider;
import no.unit.nva.auth.CognitoAuthenticator;
import no.unit.nva.auth.CognitoCredentials;
import nva.commons.core.Environment;
import nva.commons.core.JacocoGenerated;
import nva.commons.secrets.SecretsReader;
import org.apache.hc.core5.http.HttpHost;
import org.apache.hc.core5.http.HttpRequestInterceptor;
import org.opensearch.client.json.jackson.JacksonJsonpMapper;
import org.opensearch.client.opensearch.OpenSearchClient;
import org.opensearch.client.transport.httpclient5.ApacheHttpClient5Transport;
import org.opensearch.client.transport.httpclient5.ApacheHttpClient5TransportBuilder;

public final class OpenSearchClientFactory {

  private OpenSearchClientFactory() {}

  public static OpenSearchClient createClient(
      HttpHost httpHost, CachedJwtProvider cachedJwtProvider) {
    var transport = getHttpTransport(httpHost, cachedJwtProvider);
    return new OpenSearchClient(transport);
  }

  @JacocoGenerated
  public static OpenSearchClient createAuthenticatedClient(Environment environment) {
    var cachedJwtProvider = createCachedJwtProvider(environment);
    var httpHost = HttpHost.create(getSearchInfrastructureApiHost(environment));
    var transport = getHttpTransport(httpHost, cachedJwtProvider);
    return new OpenSearchClient(transport);
  }

  @JacocoGenerated
  private static CachedJwtProvider createCachedJwtProvider(Environment environment) {
    var cognitoCredentials = createCognitoCredentials(environment);
    var authenticator = new CognitoAuthenticator(HttpClient.newHttpClient(), cognitoCredentials);
    return new CachedJwtProvider(authenticator, Clock.system(DEFAULT_TIME_ZONE));
  }

  @JacocoGenerated
  private static CognitoCredentials createCognitoCredentials(Environment environment) {
    var credentials =
        new SecretsReader()
            .fetchClassSecret(SEARCH_INFRASTRUCTURE_CREDENTIALS, UsernamePasswordWrapper.class);
    var searchInfrastructureAuthUri = getSearchInfrastructureAuthUri(environment);
    return new CognitoCredentials(
        credentials::getUsername, credentials::getPassword, searchInfrastructureAuthUri);
  }

  private static ApacheHttpClient5Transport getHttpTransport(
      HttpHost httpHost, CachedJwtProvider cachedJwtProvider) {
    return ApacheHttpClient5TransportBuilder.builder(httpHost)
        .setMapper(new JacksonJsonpMapper())
        .setHttpClientConfigCallback(
            builder ->
                builder
                    .disableContentCompression()
                    .addRequestInterceptorFirst(getHttpRequestInterceptor(cachedJwtProvider)))
        .build();
  }

  private static HttpRequestInterceptor getHttpRequestInterceptor(
      CachedJwtProvider cachedJwtProvider) {
    return (request, entity, context) -> {
      var token = cachedJwtProvider.getValue().getToken();
      request.setHeader(AUTHORIZATION, token);
    };
  }
}
