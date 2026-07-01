package no.sikt.nva.nvi.test.uriretriever;

import static java.util.Objects.nonNull;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpResponse;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import no.unit.nva.auth.uriretriever.UriRetriever;
import nva.commons.core.JacocoGenerated;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Test double for {@link UriRetriever}. Register canned responses keyed by URI. Extends {@link
 * UriRetriever} (rather than just implementing {@code RawContentRetriever}) so it satisfies APIs
 * that depend on the concrete class.
 */
@JacocoGenerated // FIXME: Temporarily excluded from test coverage
public final class FakeUriRetriever extends UriRetriever {

  private static final Logger LOGGER = LoggerFactory.getLogger(FakeUriRetriever.class);

  private final Map<URI, HttpResponse<String>> responses = new HashMap<>();

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
      responses.put(uri, new FakeHttpResponse(uri, statusCode, mediaType, body));
    }
    return this;
  }

  private Optional<HttpResponse<String>> findResponse(URI uri) {
    return Optional.ofNullable(responses.get(uri));
  }
}
