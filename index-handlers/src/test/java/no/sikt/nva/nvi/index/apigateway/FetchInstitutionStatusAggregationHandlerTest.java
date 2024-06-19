package no.sikt.nva.nvi.index.apigateway;

import static no.sikt.nva.nvi.index.apigateway.utils.AggregateResponseTestUtil.organizationApprovalStatusAggregate;
import static no.sikt.nva.nvi.index.apigateway.utils.MockOpenSearchUtil.createSearchResponse;
import static no.sikt.nva.nvi.index.query.SearchAggregation.ORGANIZATION_APPROVAL_STATUS_AGGREGATION;
import static no.unit.nva.commons.json.JsonUtils.dtoObjectMapper;
import static no.unit.nva.testutils.RandomDataGenerator.randomString;
import static no.unit.nva.testutils.RandomDataGenerator.randomUri;
import static nva.commons.apigateway.AccessRight.MANAGE_NVI_CANDIDATES;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
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
import no.sikt.nva.nvi.index.aws.OpenSearchClient;
import no.sikt.nva.nvi.index.model.search.CandidateSearchParameters;
import no.unit.nva.testutils.HandlerRequestBuilder;
import nva.commons.apigateway.AccessRight;
import nva.commons.apigateway.GatewayResponse;
import org.hamcrest.Matchers;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.zalando.problem.Problem;

class FetchInstitutionStatusAggregationHandlerTest {

    private static final String INSTITUTION_ID = "institutionId";
    private static final String YEAR = "year";
    private static final int CURRENT_YEAR = Year.now().getValue();
    private static final Context CONTEXT = mock(Context.class);
    private ByteArrayOutputStream output;
    private FetchInstitutionStatusAggregationHandler handler;
    private OpenSearchClient openSearchClient;

    @BeforeEach
    public void setUp() {
        output = new ByteArrayOutputStream();
        openSearchClient = mock(OpenSearchClient.class);
        handler = new FetchInstitutionStatusAggregationHandler(openSearchClient);
    }

    @Test
    void shouldReturnUnauthorizedWhenUserDoesNotHaveSufficientAccessRight() throws IOException {
        var institutionId = randomUri();
        var request = createRequestWithoutAccessRight(institutionId, institutionId, CURRENT_YEAR)
                          .build();
        handler.handleRequest(request, output, CONTEXT);
        var response = GatewayResponse.fromOutputStream(output, Problem.class);
        assertThat(response.getStatusCode(), is(Matchers.equalTo(HttpURLConnection.HTTP_UNAUTHORIZED)));
    }

    @Test
    void shouldReturnOrganizationApprovalStatusAggregation() throws IOException {
        var institutionId = randomUri();
        var request = createRequest(institutionId, institutionId, CURRENT_YEAR, MANAGE_NVI_CANDIDATES).build();
        var expectedNestedAggregation = """
            {
              "someOrgId" : {
                "docCount" : 3,
                "status" : {
                  "Pending" : {
                    "docCount" : 2
                  }
                }
              }
            }""";
        mockOpenSearchClientResponse(institutionId);
        handler.handleRequest(request, output, CONTEXT);
        var response = GatewayResponse.fromOutputStream(output, String.class);
        assertThat(response.getStatusCode(), is(Matchers.equalTo(HttpURLConnection.HTTP_OK)));
        assertEquals(expectedNestedAggregation, response.getBody());
    }

    @Test
    void shouldReturnNotFoundIfInstitutionDoesntExist() {

    }

    private static HandlerRequestBuilder<InputStream> createRequest(URI userTopLevelCristinInstitution,
                                                                    URI institutionId, int year,
                                                                    AccessRight accessRight) {
        var customerId = randomUri();
        return new HandlerRequestBuilder<InputStream>(dtoObjectMapper)
                   .withCurrentCustomer(customerId)
                   .withTopLevelCristinOrgId(userTopLevelCristinInstitution)
                   .withAccessRights(customerId, accessRight)
                   .withUserName(randomString())
                   .withPathParameters(Map.of(INSTITUTION_ID,
                                              URLEncoder.encode(institutionId.toString(), StandardCharsets.UTF_8),
                                              YEAR, String.valueOf(year)));
    }

    private static HandlerRequestBuilder<InputStream> createRequestWithoutAccessRight(
        URI userTopLevelCristinInstitution,
        URI institutionId, int year) {
        var customerId = randomUri();
        return new HandlerRequestBuilder<InputStream>(dtoObjectMapper)
                   .withCurrentCustomer(customerId)
                   .withTopLevelCristinOrgId(userTopLevelCristinInstitution)
                   .withUserName(randomString())
                   .withPathParameters(Map.of(INSTITUTION_ID,
                                              URLEncoder.encode(institutionId.toString(), StandardCharsets.UTF_8),
                                              YEAR, String.valueOf(year)));
    }

    private void mockOpenSearchClientResponse(URI institutionId) throws IOException {
        var searchParameters = CandidateSearchParameters.builder()
                                   .withTopLevelCristinOrg(institutionId)
                                   .withYear(String.valueOf(CURRENT_YEAR))
                                   .withAggregationType(ORGANIZATION_APPROVAL_STATUS_AGGREGATION.getAggregationName())
                                   .build();
        var searchResponse = createSearchResponse(ORGANIZATION_APPROVAL_STATUS_AGGREGATION.getAggregationName(),
                                                  organizationApprovalStatusAggregate(institutionId.toString()));
        when(openSearchClient.search(searchParameters)).thenReturn(searchResponse);
    }
}
