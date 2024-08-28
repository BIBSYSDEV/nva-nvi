package no.sikt.nva.nvi.index.apigateway;

import static com.google.common.net.HttpHeaders.ACCEPT;
import static com.google.common.net.HttpHeaders.CONTENT_TYPE;
import static com.google.common.net.MediaType.ANY_APPLICATION_TYPE;
import static com.google.common.net.MediaType.MICROSOFT_EXCEL;
import static no.unit.nva.commons.json.JsonUtils.dtoObjectMapper;
import static no.unit.nva.testutils.RandomDataGenerator.randomString;
import static no.unit.nva.testutils.RandomDataGenerator.randomUri;
import static nva.commons.apigateway.AccessRight.MANAGE_NVI_CANDIDATES;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;
import static org.mockito.Mockito.mock;
import com.amazonaws.services.lambda.runtime.Context;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URI;
import java.time.Year;
import java.util.Map;
import no.unit.nva.testutils.HandlerRequestBuilder;
import nva.commons.apigateway.AccessRight;
import nva.commons.apigateway.GatewayResponse;
import org.hamcrest.Matchers;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.zalando.problem.Problem;

public class FetchInstitutionReportTest {

    private static final String YEAR = "year";
    private static final int CURRENT_YEAR = Year.now().getValue();
    private static final Context CONTEXT = mock(Context.class);
    private ByteArrayOutputStream output;
    private FetchInstitutionReportHandler handler;

    @BeforeEach
    public void setUp() {
        output = new ByteArrayOutputStream();
        handler = new FetchInstitutionReportHandler();
    }

    @Test
    void shouldReturnUnauthorizedWhenUserDoesNotHaveSufficientAccessRight() throws IOException {
        var institutionId = randomUri();
        var request = createRequest(institutionId, CURRENT_YEAR, AccessRight.MANAGE_DOI).build();
        handler.handleRequest(request, output, CONTEXT);
        var response = GatewayResponse.fromOutputStream(output, Problem.class);

        assertThat(response.getStatusCode(), is(Matchers.equalTo(HttpURLConnection.HTTP_UNAUTHORIZED)));
    }

    @Test
    void shouldReturnMediaTypeMicrosoftExcelWhenRequested() throws IOException {
        var institutionId = randomUri();
        var request = createRequest(institutionId, CURRENT_YEAR, MANAGE_NVI_CANDIDATES)
                          .withHeaders(Map.of(ACCEPT, MICROSOFT_EXCEL.toString()))
                          .build();
        handler.handleRequest(request, output, CONTEXT);
        var response = GatewayResponse.fromOutputStream(output, String.class);
        assertThat(response.getHeaders().get(CONTENT_TYPE), is(MICROSOFT_EXCEL.toString()));
    }

    @Test
    void shouldReturnMediaTypeMicrosoftExcelAsDefault() throws IOException {
        var institutionId = randomUri();
        var request = createRequest(institutionId, CURRENT_YEAR, MANAGE_NVI_CANDIDATES)
                          .withHeaders(Map.of(ACCEPT, ANY_APPLICATION_TYPE.toString()))
                          .build();
        handler.handleRequest(request, output, CONTEXT);
        var response = GatewayResponse.fromOutputStream(output, String.class);
        assertThat(response.getHeaders().get(CONTENT_TYPE), is(MICROSOFT_EXCEL.toString()));
    }

    private static HandlerRequestBuilder<InputStream> createRequest(
        URI userTopLevelCristinInstitution, int year, AccessRight accessRight) {
        var customerId = randomUri();
        return new HandlerRequestBuilder<InputStream>(dtoObjectMapper)
                   .withCurrentCustomer(customerId)
                   .withAccessRights(customerId, accessRight)
                   .withTopLevelCristinOrgId(userTopLevelCristinInstitution)
                   .withUserName(randomString())
                   .withPathParameters(Map.of(YEAR, String.valueOf(year)));
    }
}
