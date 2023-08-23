package no.sikt.nva.nvi.fetch;

import static no.sikt.nva.nvi.fetch.FetchNviCandidateHandler.PARAM_CANDIDATE_IDENTIFIER;
import static no.unit.nva.commons.json.JsonUtils.dtoObjectMapper;
import static no.unit.nva.testutils.RandomDataGenerator.randomUri;
import static nva.commons.apigateway.ApiGatewayHandler.ALLOWED_ORIGIN_ENV;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import com.amazonaws.services.lambda.runtime.Context;
import com.fasterxml.jackson.core.JsonProcessingException;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import no.sikt.nva.nvi.common.model.business.ApprovalStatus;
import no.sikt.nva.nvi.common.model.business.Candidate;
import no.sikt.nva.nvi.common.model.business.Creator;
import no.sikt.nva.nvi.common.model.business.Level;
import no.sikt.nva.nvi.common.model.business.Note;
import no.sikt.nva.nvi.common.model.business.PublicationDate;
import no.sikt.nva.nvi.common.model.business.Status;
import no.sikt.nva.nvi.common.model.business.Username;
import no.sikt.nva.nvi.common.service.NviService;
import no.unit.nva.testutils.HandlerRequestBuilder;
import nva.commons.apigateway.GatewayResponse;
import nva.commons.core.Environment;
import org.apache.hc.core5.http.ContentType;
import org.apache.hc.core5.http.HttpStatus;
import org.eclipse.jetty.http.HttpHeader;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class FetchNviCandidateHandlerTest {

    private static final Context CONTEXT = mock(Context.class);
    private ByteArrayOutputStream output;
    private FetchNviCandidateHandler handler;
    private Environment environment;
    private NviService service;

    @BeforeEach
    public void setUp() {
        environment = mock(Environment.class);
        when(environment.readEnv(ALLOWED_ORIGIN_ENV)).thenReturn("*");

        output = new ByteArrayOutputStream();
        service = mock(NviService.class);
        handler = new FetchNviCandidateHandler(service, environment);
    }

    @Test
    void shouldReturn400whenMissingCandidateId() throws IOException {

        var input = new HandlerRequestBuilder<InputStream>(dtoObjectMapper)
                        .withHeaders(Map.of(HttpHeader.ACCEPT.asString(), ContentType.APPLICATION_JSON.getMimeType()))
                        .build();
        handler.handleRequest(input, output, CONTEXT);
        var gatewayResponse = getGatewayResponse();

        assertEquals(HttpStatus.SC_NOT_FOUND, gatewayResponse.getStatusCode());
    }

    @Test
    void shouldReturn404whenNoCandidateFound() throws IOException {

        var candidateIdentifier = randomUri();
        InputStream input = getInput(candidateIdentifier);
        when(service.getCandidate(URI.create(""))).thenReturn(Optional.empty());
        handler.handleRequest(input, output, CONTEXT);
        var gatewayResponse = getGatewayResponse();

        assertEquals(HttpStatus.SC_NOT_FOUND, gatewayResponse.getStatusCode());
    }

    @Test
    void shouldReturnValidCandidateIfExists() throws IOException {

        var publicationId = randomUri();
        var candidate = getCandidate(publicationId);
        when(service.getCandidate(publicationId)).thenReturn(Optional.of(candidate));
        var input = getInput(publicationId);
        handler.handleRequest(input, output, CONTEXT);
        var gatewayResponse = getGatewayResponse();

        assertEquals(HttpStatus.SC_OK, gatewayResponse.getStatusCode());
        var bodyObject = gatewayResponse.getBodyObject(FetchCandidateResponse.class);
        assertEquals(bodyObject.publicationId(), publicationId);
    }

    private static InputStream getInput(URI publicationId) throws JsonProcessingException {
        return new HandlerRequestBuilder<InputStream>(dtoObjectMapper)
                   .withHeaders(Map.of(HttpHeader.ACCEPT.asString(), ContentType.APPLICATION_JSON.getMimeType()))
                   .withPathParameters(Map.of(PARAM_CANDIDATE_IDENTIFIER, publicationId.toString()))
                   .build();
    }

    private static Candidate getCandidate(URI publicationId) {
        return new Candidate(publicationId,
                             randomUri(),
                             true,
                             "instanceType",
                             Level.LEVEL_ONE,
                             new PublicationDate("2023", "1", "1"),
                             false,
                             1,
                             List.of(new Creator(randomUri(), List.of(randomUri()))),
                             List.of(new ApprovalStatus(randomUri(), Status.PENDING,
                                                        new Username("uzah"), Instant.now())),
                             List.of(new Note(new Username("me"), "text", Instant.now())));
    }

    private GatewayResponse<FetchCandidateResponse> getGatewayResponse()
        throws JsonProcessingException {
        return dtoObjectMapper.readValue(
            output.toString(),
            dtoObjectMapper.getTypeFactory()
                .constructParametricType(
                    GatewayResponse.class,
                    FetchCandidateResponse.class
                ));
    }
}