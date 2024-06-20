package no.sikt.nva.nvi.common.client;

import java.net.HttpURLConnection;
import java.net.URI;
import java.net.http.HttpResponse;
import java.util.Optional;
import no.sikt.nva.nvi.common.client.model.User;
import no.unit.nva.auth.uriretriever.AuthorizedBackendUriRetriever;
import nva.commons.core.paths.UriWrapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class UserRetriever {

    private static final Logger LOGGER = LoggerFactory.getLogger(UserRetriever.class);
    private static final String USERS_PATH = "users";
    private static final String APPLICATION_JSON = "application/json";
    private final AuthorizedBackendUriRetriever authorizedBackendUriRetriever;
    private final String identityServiceHost;

    public UserRetriever(AuthorizedBackendUriRetriever authorizedBackendUriRetriever, String identityServiceHost) {
        this.authorizedBackendUriRetriever = authorizedBackendUriRetriever;
        this.identityServiceHost = identityServiceHost;
    }

    public User fetchUser(String userName) {
        var response = getResponse(toIdentityServiceUri(userName));
        if (isHttpOk(response)) {
            return User.from(response.body());
        } else {
            LOGGER.error("Failed to fetch user {}. Status code: {}", userName, response.statusCode());
            throw new RuntimeException("Failed to fetch user " + userName);
        }
    }

    private static boolean isHttpOk(HttpResponse<String> response) {
        return response.statusCode() == HttpURLConnection.HTTP_OK;
    }

    private HttpResponse<String> getResponse(URI uri) {
        return Optional.ofNullable(authorizedBackendUriRetriever.fetchResponse(uri, APPLICATION_JSON))
                   .stream()
                   .filter(Optional::isPresent)
                   .map(Optional::get)
                   .findAny()
                   .orElseThrow();
    }

    private URI toIdentityServiceUri(String userName) {
        return UriWrapper.fromHost(identityServiceHost)
                   .addChild(USERS_PATH)
                   .addChild(userName)
                   .getUri();
    }
}
