package no.sikt.nva.nvi.fetch;

import static no.sikt.nva.nvi.fetch.FetchNviCandidateHandler.PARAM_CANDIDATE_IDENTIFIER;
import static no.sikt.nva.nvi.test.TestUtils.randomCandidate;
import static no.sikt.nva.nvi.test.TestUtils.randomCandidateBuilder;
import static no.unit.nva.commons.json.JsonUtils.dtoObjectMapper;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import com.amazonaws.services.lambda.runtime.Context;
import com.fasterxml.jackson.core.JsonProcessingException;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import no.sikt.nva.nvi.CandidateResponse;
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
        var candidateWithIdentifier = getCandidate(candidateId,randomCandidate());
        when(service.findById(candidateId)).thenReturn(Optional.of(candidateWithIdentifier));
        var input = getInput(candidateId);
        handler.handleRequest(input, output, CONTEXT);
        var gatewayResponse = getGatewayResponse();
        assertEquals(HttpStatus.SC_OK, gatewayResponse.getStatusCode());
        var bodyObject = gatewayResponse.getBodyObject(CandidateResponse.class);
        assertEquals(bodyObject.id(), candidateId);
        var expectedResponse = getExpectedResponse(candidateWithIdentifier);

        assertEquals(bodyObject, expectedResponse);
    }

    @Test
    void shouldHandleNullNotes() throws IOException {
        var candidateId = UUID.randomUUID();
        var candidateWithIdentifier = getCandidate(candidateId, randomCandidateBuilder().withNotes(null).build());
        when(service.findById(candidateId)).thenReturn(Optional.of(candidateWithIdentifier));
        var input = getInput(candidateId);
        handler.handleRequest(input, output, CONTEXT);
        var gatewayResponse = getGatewayResponse();
        assertEquals(HttpStatus.SC_OK, gatewayResponse.getStatusCode());
        var bodyObject = gatewayResponse.getBodyObject(CandidateResponse.class);
        var expectedResponse = getExpectedResponse(candidateWithIdentifier);

        assertEquals(bodyObject, expectedResponse);
    }

    private static CandidateResponse getExpectedResponse(CandidateWithIdentifier candidateWithIdentifier) {
        var candidate = candidateWithIdentifier.candidate();
        return new CandidateResponse(candidateWithIdentifier.identifier(),
                                          candidate.publicationId(),
                                          getApprovalStatuses(candidate),
                                          mapToInstitutionPoints(candidate),
                                          getNotes(candidate));
    }

    private static List<InstitutionPoints> mapToInstitutionPoints(Candidate candidate) {
        return candidate.points()
                   .stream()
                   .map(institutionPoint -> new InstitutionPoints(
                       institutionPoint.institutionId(), institutionPoint.points()))
                   .toList();
    }

    private static List<Note> getNotes(Candidate candidate) {
        return Objects.nonNull(candidate.notes())
                   ? candidate.notes().stream()
                         .map(note -> new Note(note.user(), note.text(), note.createdDate()))
                         .toList()
                   : Collections.emptyList();
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

    private static CandidateWithIdentifier getCandidate(UUID id, Candidate candidate) {
        return new CandidateWithIdentifier(candidate, id);
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