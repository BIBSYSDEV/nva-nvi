package no.sikt.nva.nvi.fetch;

import static no.sikt.nva.nvi.fetch.FetchNviCandidateHandler.PARAM_CANDIDATE_IDENTIFIER;
import static no.sikt.nva.nvi.test.TestUtils.randomCandidate;
import static no.unit.nva.commons.json.JsonUtils.dtoObjectMapper;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import com.amazonaws.services.lambda.runtime.Context;
import com.fasterxml.jackson.core.JsonProcessingException;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import no.sikt.nva.nvi.common.model.CandidateWithIdentifier;
import no.sikt.nva.nvi.common.model.business.Candidate;
import no.sikt.nva.nvi.common.service.NviService;
import no.unit.nva.testutils.HandlerRequestBuilder;
import nva.commons.apigateway.GatewayResponse;
import org.apache.hc.core5.http.ContentType;
import org.apache.hc.core5.http.HttpStatus;
import org.eclipse.jetty.http.HttpHeader;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class FetchNviCandidateHandlerTest {

    private static final Context CONTEXT = mock(Context.class);
    private ByteArrayOutputStream output;
    private FetchNviCandidateHandler handler;
    private NviService service;

    @BeforeEach
    public void setUp() {

        output = new ByteArrayOutputStream();
        service = mock(NviService.class);
        handler = new FetchNviCandidateHandler(service);
    }

    @Test
    void shouldReturn404whenCandidateDoesNotExist() throws IOException {

        var candidateIdentifier = UUID.randomUUID();
        var input = getInput(candidateIdentifier);
        when(service.findById(candidateIdentifier)).thenReturn(Optional.empty());
        handler.handleRequest(input, output, CONTEXT);
        var gatewayResponse = getGatewayResponse();

        assertEquals(HttpStatus.SC_NOT_FOUND, gatewayResponse.getStatusCode());
    }

    @Test
    void shouldReturnValidCandidateIfExists() throws IOException {
        var candidateId = UUID.randomUUID();
        var candidateWithIdentifier = getCandidate(candidateId);
        when(service.findById(candidateId)).thenReturn(Optional.of(candidateWithIdentifier));
        var input = getInput(candidateId);
        handler.handleRequest(input, output, CONTEXT);
        var gatewayResponse = getGatewayResponse();
        assertEquals(HttpStatus.SC_OK, gatewayResponse.getStatusCode());
        var bodyObject = gatewayResponse.getBodyObject(FetchCandidateResponse.class);
        var expectedResponse = getExpectedResponse(candidateId, candidateWithIdentifier);

        assertEquals(bodyObject, expectedResponse);
    }

    private static FetchCandidateResponse getExpectedResponse(UUID candidateId,
                                                              CandidateWithIdentifier candidateWithIdentifier) {
        var candidate = candidateWithIdentifier.candidate();
        return new FetchCandidateResponse(candidateId, candidate.publicationId(), getApprovalStatuses(candidate),
                                          candidate.points(),
                                          getNotes(candidate));
    }

    private static List<Note> getNotes(Candidate candidate) {
        return candidate.notes().stream()
                   .map(note -> new Note(note.user(), note.text(), note.createdDate()))
                   .toList();
    }

    private static List<ApprovalStatus> getApprovalStatuses(Candidate candidate) {
        return candidate.approvalStatuses().stream()
                   .map(approvalStatus -> new ApprovalStatus(
                       approvalStatus.institutionId(),
                       approvalStatus.status(), approvalStatus.finalizedBy(),
                       approvalStatus.finalizedDate()))
                   .toList();
    }

    private static InputStream getInput(UUID publicationId) throws JsonProcessingException {
        return new HandlerRequestBuilder<InputStream>(dtoObjectMapper)
                   .withHeaders(Map.of(HttpHeader.ACCEPT.asString(), ContentType.APPLICATION_JSON.getMimeType()))
                   .withPathParameters(Map.of(PARAM_CANDIDATE_IDENTIFIER, publicationId.toString()))
                   .build();
    }

    private static CandidateWithIdentifier getCandidate(UUID id) {
        return new CandidateWithIdentifier(
            randomCandidate(),
            id);
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