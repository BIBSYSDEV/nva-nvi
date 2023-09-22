package no.sikt.nva.nvi.rest.fetch;

import static no.sikt.nva.nvi.rest.fetch.FetchNviCandidateHandler.CANDIDATE_IDENTIFIER;
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
import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import no.sikt.nva.nvi.CandidateResponse;
import no.sikt.nva.nvi.common.db.Candidate;
import no.sikt.nva.nvi.common.db.model.DbApprovalStatus;
import no.sikt.nva.nvi.common.db.model.DbCandidate;
import no.sikt.nva.nvi.common.db.model.DbInstitutionPoints;
import no.sikt.nva.nvi.common.service.NviService;
import no.sikt.nva.nvi.rest.upsert.NviApprovalStatus;
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
        when(service.findCandidateById(candidateIdentifier)).thenReturn(Optional.empty());
        handler.handleRequest(input, output, CONTEXT);
        var gatewayResponse = getGatewayResponse();

        assertEquals(HttpStatus.SC_NOT_FOUND, gatewayResponse.getStatusCode());
    }

    @Test
    void shouldReturnValidCandidateIfExists() throws IOException {
        var candidateId = UUID.randomUUID();
        var candidateWithIdentifier = getCandidate(candidateId, randomCandidate(), List.of());
        when(service.findCandidateById(candidateId)).thenReturn(Optional.of(candidateWithIdentifier));
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
        var candidate = getCandidate(candidateId, randomCandidateBuilder(true).build(), List.of());
        when(service.findCandidateById(candidateId)).thenReturn(Optional.of(candidate));
        var input = getInput(candidateId);
        handler.handleRequest(input, output, CONTEXT);
        var gatewayResponse = getGatewayResponse();
        assertEquals(HttpStatus.SC_OK, gatewayResponse.getStatusCode());
        var bodyObject = gatewayResponse.getBodyObject(CandidateResponse.class);
        var expectedResponse = getExpectedResponse(candidate);

        assertEquals(bodyObject, expectedResponse);
    }

    private static CandidateResponse getExpectedResponse(Candidate candidate) {
        return new CandidateResponse(candidate.identifier(),
                                     candidate.candidate().publicationId(),
                                     getApprovalStatuses(candidate.approvalStatuses(), candidate.candidate().points()),
                                     List.of()
        );
    }

    private static List<ApprovalStatus> getApprovalStatuses(List<DbApprovalStatus> approvalStatuses,
                                                            List<DbInstitutionPoints> points) {
        return approvalStatuses.stream()
                   .map(approvalStatus -> new ApprovalStatus(
                       approvalStatus.institutionId(),
                       NviApprovalStatus.parse(approvalStatus.status().getValue()),
                       getPointsForApprovalStatus(points, approvalStatus),
                       approvalStatus.assignee(),
                       approvalStatus.finalizedBy(),
                       approvalStatus.finalizedDate()))
                   .toList();
    }

    private static BigDecimal getPointsForApprovalStatus(List<DbInstitutionPoints> points,
                                                         DbApprovalStatus approvalStatus) {
        return points.stream()
                   .filter(institutionPoints -> isForSameInstitution(approvalStatus, institutionPoints))
                   .map(DbInstitutionPoints::points)
                   .findFirst()
                   .orElseThrow();
    }

    private static boolean isForSameInstitution(DbApprovalStatus approvalStatus,
                                                DbInstitutionPoints institutionPoints) {
        return institutionPoints.institutionId().equals(approvalStatus.institutionId());
    }

    private static InputStream getInput(UUID publicationId) throws JsonProcessingException {
        return new HandlerRequestBuilder<InputStream>(dtoObjectMapper)
                   .withHeaders(Map.of(HttpHeader.ACCEPT.asString(), ContentType.APPLICATION_JSON.getMimeType()))
                   .withPathParameters(Map.of(CANDIDATE_IDENTIFIER, publicationId.toString()))
                   .build();
    }

    private static Candidate getCandidate(UUID id, DbCandidate candidate, List<DbApprovalStatus> approvalStatusList) {
        return new Candidate(id, candidate, approvalStatusList, List.of());
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