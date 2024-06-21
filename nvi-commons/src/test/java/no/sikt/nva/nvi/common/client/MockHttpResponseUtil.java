package no.sikt.nva.nvi.common.client;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import java.net.http.HttpResponse;

public final class MockHttpResponseUtil {

    private MockHttpResponseUtil() {
    }

    @SuppressWarnings("unchecked")
    public static HttpResponse<String> createResponse(String body) {
        var response = (HttpResponse<String>) mock(HttpResponse.class);
        when(response.statusCode()).thenReturn(200);
        when(response.body()).thenReturn(body);
        return response;
    }

}
