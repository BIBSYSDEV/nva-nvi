package no.sikt.nva.nvi.common.client;

import static no.sikt.nva.nvi.common.client.MockHttpResponseUtil.createResponse;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import java.net.URI;
import java.util.Optional;
import no.unit.nva.auth.uriretriever.AuthorizedBackendUriRetriever;
import nva.commons.core.paths.UriWrapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class UserRetrieverTest {

    public static final String API_HOST = "https://example.com";
    AuthorizedBackendUriRetriever authorizedBackendUriRetriever;
    UserRetriever userRetriever;

    @BeforeEach
    void setUp() {
        authorizedBackendUriRetriever = mock(AuthorizedBackendUriRetriever.class);
        userRetriever = new UserRetriever(authorizedBackendUriRetriever, API_HOST);
    }

    @Test
    void shouldFetchUser() {
        var userName = "userName";
        var includedUnit = "https://api.dev.nva.aws.unit.no/customer/123";
        var expectedUri = UriWrapper.fromUri(API_HOST).addChild("users-roles", "users").addChild(userName).getUri();
        mockAuthorizedBackendUriRetriever(expectedUri, includedUnit);
        var actualUser = userRetriever.fetchUser(userName);
        assertEquals(includedUnit, actualUser.viewingScope().includedUnits().get(0));
    }

    private static String createResponseBody(String includedUnit) {
        return String.format("""
                                 {
                                     "username": "userName",
                                     "institution": "https://api.dev.nva.aws.unit.no/customer/123",
                                     "viewingScope": {
                                         "type": "ViewingScope",
                                         "includedUnits": [
                                             "%s"
                                         ],
                                         "excludedUnits": []
                                     }
                                 }
                                 """, includedUnit);
    }

    private void mockAuthorizedBackendUriRetriever(URI uri, String includedUnit) {
        var response = createResponse(createResponseBody(includedUnit));
        when(authorizedBackendUriRetriever.fetchResponse(eq(uri), eq("application/json")))
            .thenReturn(Optional.of(response));
    }
}