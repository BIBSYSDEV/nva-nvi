package no.sikt.nva.nvi.rest.fetch;

import static java.util.UUID.randomUUID;
import static no.sikt.nva.nvi.rest.fetch.FetchNviCandidateHandler.CANDIDATE_IDENTIFIER;
import static no.sikt.nva.nvi.test.TestUtils.CURRENT_YEAR;
import static no.sikt.nva.nvi.test.TestUtils.createUpsertCandidateRequest;
import static no.sikt.nva.nvi.test.TestUtils.createUpsertNonCandidateRequest;
import static no.sikt.nva.nvi.test.TestUtils.periodRepositoryReturningOpenedPeriod;
import static no.unit.nva.commons.json.JsonUtils.dtoObjectMapper;
import static no.unit.nva.testutils.RandomDataGenerator.randomString;
import static no.unit.nva.testutils.RandomDataGenerator.randomUri;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;
import com.amazonaws.services.lambda.runtime.Context;
import com.fasterxml.jackson.core.JsonProcessingException;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URI;
import java.util.Map;
import java.util.UUID;
import no.sikt.nva.nvi.common.db.CandidateRepository;
import no.sikt.nva.nvi.common.db.PeriodRepository;
import no.sikt.nva.nvi.common.service.CandidateBO;
import no.sikt.nva.nvi.common.service.dto.CandidateDto;
import no.sikt.nva.nvi.test.LocalDynamoTest;
import no.unit.nva.testutils.HandlerRequestBuilder;
import nva.commons.apigateway.AccessRight;
import nva.commons.apigateway.GatewayResponse;
import org.apache.hc.core5.http.ContentType;
import org.apache.hc.core5.http.HttpStatus;
import org.eclipse.jetty.http.HttpHeader;
import org.hamcrest.Matchers;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.zalando.problem.Problem;
import software.amazon.awssdk.services.dynamodb.DynamoDbClient;

class FetchNviCandidateHandlerTest extends LocalDynamoTest {

    public static final String ACCESS_RIGHT_MANAGE_NVI_CANDIDATES = AccessRight.MANAGE_NVI_CANDIDATE.name();
    private static final Context CONTEXT = mock(Context.class);
    private final DynamoDbClient localDynamo = initializeTestDatabase();
    private ByteArrayOutputStream output;
    private FetchNviCandidateHandler handler;
    private CandidateRepository candidateRepository;
    private PeriodRepository periodRepository;

    @BeforeEach
    public void setUp() {
        output = new ByteArrayOutputStream();
        candidateRepository = new CandidateRepository(localDynamo);
        periodRepository = periodRepositoryReturningOpenedPeriod(CURRENT_YEAR);
        handler = new FetchNviCandidateHandler(candidateRepository, periodRepository);
    }

    @Test
    void shouldReturnNotFoundWhenCandidateDoesNotExist() throws IOException {
        handler.handleRequest(createRequest(randomUUID(), randomUri(), randomUri(), ACCESS_RIGHT_MANAGE_NVI_CANDIDATES),
                              output, CONTEXT);
        var gatewayResponse = getGatewayResponse();

        assertEquals(HttpStatus.SC_NOT_FOUND, gatewayResponse.getStatusCode());
    }

    @Test
    void shouldReturnNotFoundWhenCandidateExistsButNotApplicable() throws IOException {
        var institutionId = randomUri();
        var nonApplicableCandidate = setUpNonApplicableCandidate(institutionId);
        handler.handleRequest(createRequest(nonApplicableCandidate.getIdentifier(), institutionId, institutionId,
                                            ACCESS_RIGHT_MANAGE_NVI_CANDIDATES), output, CONTEXT);
        var gatewayResponse = getGatewayResponse();

        assertEquals(HttpStatus.SC_NOT_FOUND, gatewayResponse.getStatusCode());
    }

    @Test
    void shouldReturnUnauthorizedWhenUserDoesNotBelongToSameInstitutionAsAnyOfCandidateApprovals() throws IOException {
        var candidate = CandidateBO.fromRequest(createUpsertCandidateRequest(randomUri()),
                                                candidateRepository, periodRepository).orElseThrow();
        var request = createRequest(candidate.getIdentifier(), randomUri(), randomUri(),
                                    ACCESS_RIGHT_MANAGE_NVI_CANDIDATES);
        handler.handleRequest(request, output, CONTEXT);
        var response = GatewayResponse.fromOutputStream(output, Problem.class);

        assertThat(response.getStatusCode(), is(Matchers.equalTo(HttpURLConnection.HTTP_UNAUTHORIZED)));
    }

    @Test
    void shouldReturnUnauthorizedWhenUserDoesNotHaveSufficientAccessRight() throws IOException {
        var institutionId = randomUri();
        var candidate = CandidateBO.fromRequest(createUpsertCandidateRequest(institutionId),
                                                candidateRepository, periodRepository).orElseThrow();
        var request = createRequest(candidate.getIdentifier(), institutionId, institutionId, "SomeAccessRight");
        handler.handleRequest(request, output, CONTEXT);
        var response = GatewayResponse.fromOutputStream(output, Problem.class);

        assertThat(response.getStatusCode(), is(Matchers.equalTo(HttpURLConnection.HTTP_UNAUTHORIZED)));
    }

    @Test
    void shouldReturnValidCandidateWhenCandidateExists() throws IOException {
        var institutionId = randomUri();
        var candidate =
            CandidateBO.fromRequest(createUpsertCandidateRequest(institutionId), candidateRepository, periodRepository)
                .orElseThrow();
        var request = createRequest(candidate.getIdentifier(), institutionId, institutionId,
                                    ACCESS_RIGHT_MANAGE_NVI_CANDIDATES);

        handler.handleRequest(request, output, CONTEXT);
        var response = GatewayResponse.fromOutputStream(output, CandidateDto.class);
        var expectedResponse = candidate.toDto();
        var actualResponse = response.getBodyObject(CandidateDto.class);

        assertEquals(expectedResponse, actualResponse);
    }

    private static InputStream createRequest(UUID publicationId, URI customerId, URI institutionId, String accessRight)
        throws JsonProcessingException {
        return new HandlerRequestBuilder<InputStream>(dtoObjectMapper)
                   .withHeaders(Map.of(HttpHeader.ACCEPT.asString(), ContentType.APPLICATION_JSON.getMimeType()))
                   .withCurrentCustomer(customerId)
                   .withTopLevelCristinOrgId(institutionId)
                   .withAccessRights(customerId, accessRight)
                   .withUserName(randomString())
                   .withPathParameters(Map.of(CANDIDATE_IDENTIFIER, publicationId.toString()))
                   .build();
    }

    private CandidateBO setUpNonApplicableCandidate(URI institutionId) {
        var candidate =
            CandidateBO.fromRequest(createUpsertCandidateRequest(institutionId), candidateRepository, periodRepository)
                .orElseThrow();
        return CandidateBO.fromRequest(createUpsertNonCandidateRequest(candidate.getPublicationId()),
                                       candidateRepository).orElseThrow();
    }

    private GatewayResponse<CandidateDto> getGatewayResponse()
        throws JsonProcessingException {
        return dtoObjectMapper.readValue(
            output.toString(),
            dtoObjectMapper.getTypeFactory()
                .constructParametricType(
                    GatewayResponse.class,
                    CandidateDto.class
                ));
    }
}