package no.sikt.nva.nvi.rest.fetch;

import static no.sikt.nva.nvi.rest.fetch.FetchNviCandidateHandler.CANDIDATE_IDENTIFIER;
import static no.sikt.nva.nvi.test.TestUtils.randomCandidate;
import static no.sikt.nva.nvi.test.TestUtils.randomCandidateWithPublicationYear;
import static no.unit.nva.commons.json.JsonUtils.dtoObjectMapper;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;
import com.amazonaws.services.lambda.runtime.Context;
import com.fasterxml.jackson.core.JsonProcessingException;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.Map;
import java.util.UUID;
import no.sikt.nva.nvi.common.service.Candidate;
import no.sikt.nva.nvi.common.service.NviService;
import no.sikt.nva.nvi.rest.model.CandidateResponse;
import no.sikt.nva.nvi.rest.model.CandidateResponseMapper;
import no.sikt.nva.nvi.test.LocalDynamoTest;
import no.sikt.nva.nvi.test.TestUtils;
import no.unit.nva.testutils.HandlerRequestBuilder;
import nva.commons.apigateway.GatewayResponse;
import org.apache.hc.core5.http.ContentType;
import org.apache.hc.core5.http.HttpStatus;
import org.eclipse.jetty.http.HttpHeader;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class FetchNviCandidateHandlerTest extends LocalDynamoTest {

    private static final Context CONTEXT = mock(Context.class);
    private ByteArrayOutputStream output;
    private FetchNviCandidateHandler handler;
    private NviService nviService;

    @BeforeEach
    public void setUp() {
        output = new ByteArrayOutputStream();
        nviService = new NviService(initializeTestDatabase());
        handler = new FetchNviCandidateHandler(nviService);
    }

    @Test
    void shouldReturnNotFoundWhenCandidateDoesNotExist() throws IOException {
        handler.handleRequest(createRequest(UUID.randomUUID()), output, CONTEXT);
        var gatewayResponse = getGatewayResponse();

        assertEquals(HttpStatus.SC_NOT_FOUND, gatewayResponse.getStatusCode());
    }

    @Test
    void shouldReturnNotFoundWhenCandidateExistsButNotApplicable() throws IOException {
        var candidate = nviService.upsertCandidate(randomCandidate()).orElseThrow();
        updateCandidateToNotApplicable(candidate);
        handler.handleRequest(createRequest(candidate.identifier()), output, CONTEXT);
        var gatewayResponse = getGatewayResponse();

        assertEquals(HttpStatus.SC_NOT_FOUND, gatewayResponse.getStatusCode());
    }

    @Test
    void shouldReturnValidCandidateWhenCandidateExists() throws IOException {
        var candidate = nviService.upsertCandidate(randomCandidate()).orElseThrow();
        var input = createRequest(candidate.identifier());
        handler.handleRequest(input, output, CONTEXT);

        var gatewayResponse = getGatewayResponse();
        assertEquals(HttpStatus.SC_OK, gatewayResponse.getStatusCode());

        var expectedResponse = getExpectedResponse(candidate);
        var actualResponse = gatewayResponse.getBodyObject(CandidateResponse.class);

        assertEquals(actualResponse, expectedResponse);
    }

    @Test
    void shouldReturnCandidate() throws IOException {
        var nviService = TestUtils.nviServiceReturningOpenPeriod(initializeTestDatabase(), 2024);
        var candidate = nviService.upsertCandidate(randomCandidateWithPublicationYear(2024)).orElseThrow();
        var input = createRequest(candidate.identifier());
        var handler = new FetchNviCandidateHandler(nviService);
        handler.handleRequest(input, output, CONTEXT);

        var gatewayResponse = getGatewayResponse();
        assertEquals(HttpStatus.SC_OK, gatewayResponse.getStatusCode());
    }

    @Test
    void shouldHandleNullNotes() throws IOException {
        var candidate = nviService.upsertCandidate(randomCandidate()).orElseThrow();
        var input = createRequest(candidate.identifier());
        handler.handleRequest(input, output, CONTEXT);
        var gatewayResponse = getGatewayResponse();
        assertEquals(HttpStatus.SC_OK, gatewayResponse.getStatusCode());
        var bodyObject = gatewayResponse.getBodyObject(CandidateResponse.class);
        var expectedResponse = getExpectedResponse(candidate);

        assertEquals(bodyObject, expectedResponse);
    }

    private static CandidateResponse getExpectedResponse(Candidate candidate) {
        return CandidateResponseMapper.toDto(candidate);
    }

    private static InputStream createRequest(UUID publicationId) throws JsonProcessingException {
        return new HandlerRequestBuilder<InputStream>(dtoObjectMapper)
                   .withHeaders(Map.of(HttpHeader.ACCEPT.asString(), ContentType.APPLICATION_JSON.getMimeType()))
                   .withPathParameters(Map.of(CANDIDATE_IDENTIFIER, publicationId.toString()))
                   .build();
    }

    private void updateCandidateToNotApplicable(Candidate candidate) {
        nviService.upsertCandidate(candidate.candidate().copy().applicable(false).build());
    }

    private GatewayResponse<CandidateResponse> getGatewayResponse()
        throws JsonProcessingException {
        return dtoObjectMapper.readValue(
            output.toString(),
            dtoObjectMapper.getTypeFactory()
                .constructParametricType(
                    GatewayResponse.class,
                    CandidateResponse.class
                ));
    }
}