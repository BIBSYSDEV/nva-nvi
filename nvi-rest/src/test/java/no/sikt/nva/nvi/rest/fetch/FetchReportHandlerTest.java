package no.sikt.nva.nvi.rest.fetch;

import static no.sikt.nva.nvi.rest.fetch.FetchNviCandidateHandler.CANDIDATE_IDENTIFIER;
import static no.sikt.nva.nvi.test.TestUtils.periodRepositoryReturningOpenedPeriod;
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
import no.sikt.nva.nvi.common.db.CandidateRepository;
import no.sikt.nva.nvi.common.db.PeriodRepository;
import no.sikt.nva.nvi.test.LocalDynamoTest;
import no.sikt.nva.nvi.test.TestUtils;
import no.unit.nva.testutils.HandlerRequestBuilder;
import nva.commons.apigateway.AccessRight;
import nva.commons.apigateway.GatewayResponse;
import org.hamcrest.Matchers;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.zalando.problem.Problem;
import software.amazon.awssdk.services.dynamodb.DynamoDbClient;

class FetchReportHandlerTest extends LocalDynamoTest {

    private static final String INSTITUTION_ID = "institutionId";
    private static final String YEAR = "year";
    private static final int CURRENT_YEAR = Year.now().getValue();
    private static final Context CONTEXT = mock(Context.class);
    private final DynamoDbClient localDynamo = initializeTestDatabase();
    private ByteArrayOutputStream output;
    private FetchReportHandler handler;
    private CandidateRepository candidateRepository;
    private PeriodRepository periodRepository;

    @BeforeEach
    public void setUp() {
        output = new ByteArrayOutputStream();
        candidateRepository = new CandidateRepository(localDynamo);
        periodRepository = periodRepositoryReturningOpenedPeriod(TestUtils.CURRENT_YEAR);
        handler = new FetchReportHandler(candidateRepository, periodRepository);
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
