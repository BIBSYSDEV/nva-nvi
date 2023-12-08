package no.sikt.nva.nvi.events.evaluator.client;

import static no.unit.nva.auth.AuthorizedBackendClient.AUTHORIZATION_HEADER;
import static nva.commons.core.attempt.Try.attempt;
import static software.amazon.awssdk.http.Header.ACCEPT;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.net.http.HttpResponse.BodyHandler;
import java.nio.charset.StandardCharsets;
import no.unit.nva.auth.CachedJwtProvider;
import nva.commons.core.JacocoGenerated;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@JacocoGenerated
public class AuthorizedUriRetriever {

    private static final Logger LOGGER = LoggerFactory.getLogger(AuthorizedUriRetriever.class);
    private static final String FAILED_TO_RETRIEVE_URI_MESSAGE = "Failed to retrieve uri {}. Exception message: {}";
    private final HttpClient httpClient;
    private final BodyHandler<String> bodyHandler;
    private final CachedJwtProvider jwtProvider;

    public AuthorizedUriRetriever(HttpClient httpClient, CachedJwtProvider jwtProvider) {
        this.bodyHandler = HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8);
        this.httpClient = httpClient;
        this.jwtProvider = jwtProvider;
    }

    public HttpResponse<String> fetchResponse(URI uri, String mediaType) {
        return attempt(() -> httpClient.send(createRequest(uri, mediaType), bodyHandler)).orElseThrow(
            failure -> logAndRethrowException(uri, failure.getException()));
    }

    private HttpRequest createRequest(URI uri, String mediaType) {
        return HttpRequest.newBuilder(uri)
                   .headers(ACCEPT, mediaType,
                            AUTHORIZATION_HEADER, jwtProvider.getValue().getToken())
                   .GET()
                   .build();
    }

    private RuntimeException logAndRethrowException(URI uri, Exception exception) {
        LOGGER.error(FAILED_TO_RETRIEVE_URI_MESSAGE, uri, exception.getMessage());
        if (exception instanceof InterruptedException) {
            Thread.currentThread().interrupt();
        }
        return new RuntimeException(exception);
    }
}
