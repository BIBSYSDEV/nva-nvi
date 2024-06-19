package no.sikt.nva.nvi.index.apigateway;

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
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.Year;
import java.util.Map;
import java.util.UUID;
import no.sikt.nva.nvi.index.aws.OpenSearchClient;
import no.unit.nva.testutils.HandlerRequestBuilder;
import nva.commons.apigateway.AccessRight;
import nva.commons.apigateway.GatewayResponse;
import org.hamcrest.Matchers;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.zalando.problem.Problem;

class FetchReportHandlerTest {

    public static final String CANDIDATE_IDENTIFIER = "CANDIDATE_IDENTIFIER";
    private static final String INSTITUTION_ID = "institutionId";
    private static final String YEAR = "year";
    private static final int CURRENT_YEAR = Year.now().getValue();
    private static final Context CONTEXT = mock(Context.class);
    private ByteArrayOutputStream output;
    private FetchReportHandler handler;

    @BeforeEach
    public void setUp() {
        output = new ByteArrayOutputStream();
        var mockOpenSearchClient = mock(OpenSearchClient.class);
        handler = new FetchReportHandler(mockOpenSearchClient);
    }

    @Test
    void shouldReturnUnauthorizedWhenUserDoesNotHaveSufficientAccessRight() throws IOException {
        var institutionId = randomUri();
        var request = createRequestWithoutAccessRight(UUID.randomUUID(), institutionId, institutionId,
                                                      Year.now().getValue())
                          .build();
        handler.handleRequest(request, output, CONTEXT);
        var response = GatewayResponse.fromOutputStream(output, Problem.class);
        assertThat(response.getStatusCode(), is(Matchers.equalTo(HttpURLConnection.HTTP_UNAUTHORIZED)));
    }

    @Test
    void shouldReturnForbiddenWhenUserDoesNotBelongToSameInstitutionAsRequestedInstitution()
        throws IOException {
        var request = createRequest(UUID.randomUUID(), randomUri(), randomUri(), CURRENT_YEAR,
                                    MANAGE_NVI_CANDIDATES).build();
        handler.handleRequest(request, output, CONTEXT);
        var response = GatewayResponse.fromOutputStream(output, Problem.class);

        assertThat(response.getStatusCode(), is(Matchers.equalTo(HttpURLConnection.HTTP_FORBIDDEN)));
    }

    @Test
    void shouldReturnNotFoundIfInstitutionDoesntExist() {

    }

    @Test
    void shouldReturnOrganizationApprovalStatusAggregation() {

    }

    private static HandlerRequestBuilder<InputStream> createRequest(UUID candidateIdentifier,
                                                                    URI userTopLevelCristinInstitution,
                                                                    URI institutionId, int year,
                                                                    AccessRight accessRight) {
        var customerId = randomUri();
        return new HandlerRequestBuilder<InputStream>(dtoObjectMapper)
                   .withCurrentCustomer(customerId)
                   .withTopLevelCristinOrgId(userTopLevelCristinInstitution)
                   .withAccessRights(customerId, accessRight)
                   .withUserName(randomString())
                   .withPathParameters(Map.of(CANDIDATE_IDENTIFIER, candidateIdentifier.toString(),
                                              INSTITUTION_ID,
                                              URLEncoder.encode(institutionId.toString(), StandardCharsets.UTF_8),
                                              YEAR, String.valueOf(year)));
    }

    private static HandlerRequestBuilder<InputStream> createRequestWithoutAccessRight(UUID candidateIdentifier,
                                                                                      URI userTopLevelCristinInstitution,
                                                                                      URI institutionId, int year) {
        var customerId = randomUri();
        return new HandlerRequestBuilder<InputStream>(dtoObjectMapper)
                   .withCurrentCustomer(customerId)
                   .withTopLevelCristinOrgId(userTopLevelCristinInstitution)
                   .withUserName(randomString())
                   .withPathParameters(Map.of(CANDIDATE_IDENTIFIER, candidateIdentifier.toString(),
                                              INSTITUTION_ID,
                                              URLEncoder.encode(institutionId.toString(), StandardCharsets.UTF_8),
                                              YEAR, String.valueOf(year)));
    }
}
