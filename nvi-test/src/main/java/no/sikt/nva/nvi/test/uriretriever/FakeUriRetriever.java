package no.sikt.nva.nvi.test.uriretriever;

import static java.util.Objects.nonNull;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpClient.Version;
import java.net.http.HttpHeaders;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import javax.net.ssl.SSLSession;
import no.unit.nva.auth.uriretriever.UriRetriever;
import nva.commons.core.JacocoGenerated;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * In-memory test double for {@link UriRetriever}. Register canned responses keyed by URI and the
 * fake returns them from {@link #getRawContent} and {@link #fetchResponse}. Extends {@link
 * UriRetriever} (rather than just implementing {@code RawContentRetriever}) so it satisfies APIs
 * that depend on the concrete class.
 */
// FIXME: Ignoring in test coverage temporarily
@JacocoGenerated
public final class FakeUriRetriever extends UriRetriever {

  private static final Logger LOGGER = LoggerFactory.getLogger(FakeUriRetriever.class);
  private static final String CONTENT_TYPE = "Content-Type";

  private final List<HttpResponse<String>> responses = new ArrayList<>();

  private FakeUriRetriever() {
    super(HttpClient.newHttpClient());
  }

  public static FakeUriRetriever newInstance() {
    return new FakeUriRetriever();
  }

  @Override
  public Optional<String> getRawContent(URI uri, String mediaType) {
    var match = findResponse(uri);
    if (match.isEmpty()) {
      LOGGER.warn("(getRawContent fake) No matching response registered for {}", uri);
    }
    return match.map(HttpResponse::body);
  }

  @Override
  public Optional<HttpResponse<String>> fetchResponse(URI uri, String mediaType) {
    var match = findResponse(uri);
    if (match.isEmpty()) {
      LOGGER.warn("(fetchResponse fake) No matching response registered for {}", uri);
    }
    return match;
  }

  public FakeUriRetriever registerResponse(URI uri, int statusCode, String mediaType, String body) {
    if (nonNull(uri)) {
      add(new FakeHttpResponse(uri, statusCode, mediaType, body));
    }
    return this;
  }

  private Optional<HttpResponse<String>> findResponse(URI uri) {
    return responses.stream().filter(response -> response.uri().equals(uri)).findFirst();
  }

  private void add(HttpResponse<String> response) {
    responses.stream()
        .filter(existing -> existing.uri().equals(response.uri()))
        .findFirst()
        .ifPresent(responses::remove);
    responses.add(response);
  }

  // FIXME: Ignoring in test coverage temporarily
  @JacocoGenerated
  public record FakeHttpResponse(URI uri, int statusCode, String mediaType, String body)
      implements HttpResponse<String> {

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
    public Version version() {
      return null;
    }
  }
}
