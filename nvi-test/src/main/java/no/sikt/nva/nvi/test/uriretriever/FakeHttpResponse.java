package no.sikt.nva.nvi.test.uriretriever;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpHeaders;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import javax.net.ssl.SSLSession;
import nva.commons.core.JacocoGenerated;

@JacocoGenerated // FIXME: Temporarily excluded from test coverage
public record FakeHttpResponse(URI uri, int statusCode, String mediaType, String body)
    implements HttpResponse<String> {

  private static final String CONTENT_TYPE = "Content-Type";

  @Override
  public HttpRequest request() {
    return null;
  }

  @Override
  public Optional<HttpResponse<String>> previousResponse() {
    return Optional.empty();
  }

  @Override
  public HttpHeaders headers() {
    return HttpHeaders.of(Map.of(CONTENT_TYPE, List.of(mediaType)), (a, b) -> true);
  }

  @Override
  public Optional<SSLSession> sslSession() {
    return Optional.empty();
  }

  @Override
  public HttpClient.Version version() {
    return null;
  }
}
