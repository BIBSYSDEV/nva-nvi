package no.sikt.nva.nvi.upsert;

import static no.unit.nva.testutils.RandomDataGenerator.randomString;
import static no.unit.nva.testutils.RandomDataGenerator.randomUri;
import static org.hamcrest.CoreMatchers.equalTo;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import com.amazonaws.services.lambda.runtime.Context;
import com.fasterxml.jackson.core.JsonProcessingException;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URI;
import java.time.Instant;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.UUID;
import no.sikt.nva.nvi.CandidateResponse;
import no.sikt.nva.nvi.common.model.CandidateWithIdentifier;
import no.sikt.nva.nvi.common.model.business.Candidate;
import no.sikt.nva.nvi.common.model.business.Status;
import no.sikt.nva.nvi.common.model.business.Username;
import no.sikt.nva.nvi.common.service.NviService;
import no.sikt.nva.nvi.fetch.ApprovalStatus;
import no.sikt.nva.nvi.rest.NviApprovalStatus;
import no.sikt.nva.nvi.rest.NviStatusRequest;
import no.unit.nva.commons.json.JsonUtils;
import no.unit.nva.testutils.HandlerRequestBuilder;
import nva.commons.apigateway.AccessRight;
import nva.commons.apigateway.GatewayResponse;
import nva.commons.apigateway.exceptions.BadRequestException;
import org.hamcrest.Matchers;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.zalando.problem.Problem;

class UpsertNviCandidateStatusHandlerTest {

    private Context context;
    private ByteArrayOutputStream output;
    private UpsertNviCandidateStatusHandler handler;
    private NviService nviService;

    @BeforeEach
    void init() {
        output = new ByteArrayOutputStream();
        context = mock(Context.class);
        nviService = mock(NviService.class);
        handler = new UpsertNviCandidateStatusHandler(nviService);
    }

    @Test
    void shouldReturnUnauthorizedWhenMissingAccessRights() throws IOException {
        handler.handleRequest(createRequestWithoutAccessRights(randomStatusRequest()), output, context);
        var response = GatewayResponse.fromOutputStream(output, Problem.class);

        assertThat(response.getStatusCode(), is(equalTo(HttpURLConnection.HTTP_UNAUTHORIZED)));
    }

    @Test
    void shouldReturnBadRequestWhenMissingAccessRights() throws IOException, BadRequestException {
        when(nviService.upsertApproval(UUID.randomUUID(), any())).thenThrow(IllegalArgumentException.class);
        handler.handleRequest(createRequest(randomStatusRequest()), output, context);
        var response = GatewayResponse.fromOutputStream(output, Problem.class);

        assertThat(response.getStatusCode(), is(Matchers.equalTo(HttpURLConnection.HTTP_BAD_REQUEST)));
    }

    @Test
    void shouldReturnCandidateResponseWhenSuccessful() throws IOException {
        var nviStatusRequest = new NviStatusRequest(UUID.randomUUID(), randomUri(), NviApprovalStatus.APPROVED);
        var request = createRequest(nviStatusRequest);
        var status = Status.APPROVED;
        var response = mockServiceResponse(nviStatusRequest, status);
        var approvalStatus = response.candidate()
                                 .approvalStatuses()
                                 .get(0);
        when(nviService.upsertApproval(any(), any())).thenReturn(response);

        handler.handleRequest(request, output, context);

        var gatewayResponse = GatewayResponse.fromOutputStream(output, CandidateResponse.class);
        var candidateResponse = new CandidateResponse(
            nviStatusRequest.candidateId(),
            response.candidate().publicationId(),
            List.of(new ApprovalStatus(nviStatusRequest.institutionId(),
                                       status,
                                       new Username(
                                           approvalStatus.finalizedBy().value()),
                                       approvalStatus.finalizedDate())),
            new HashMap<>(),
            Collections.emptyList());
        CandidateResponse bodyAsInstance = gatewayResponse.getBodyObject(CandidateResponse.class);
        assertThat(bodyAsInstance, is(equalTo(candidateResponse)));
    }

    private static CandidateWithIdentifier mockServiceResponse(NviStatusRequest nviStatusRequest,
                                                               Status status) {
        return new CandidateWithIdentifier(
            Candidate.builder()
                .withPublicationId(nviStatusRequest.institutionId())
                .withApprovalStatuses(
                    List.of(new no.sikt.nva.nvi.common.model.business.ApprovalStatus(nviStatusRequest.institutionId(),
                                                                                     status,
                                                                                     new Username(randomString()),
                                                                                     Instant.now())))
                .withPoints(new HashMap<>())
                .withNotes(List.of())
                .build(),
            nviStatusRequest.candidateId());
    }

    private InputStream createRequest(NviStatusRequest body) throws JsonProcessingException {
        URI customerId = randomUri();
        return new HandlerRequestBuilder<NviStatusRequest>(JsonUtils.dtoObjectMapper)
                   .withBody(body)
                   .withCurrentCustomer(customerId)
                   //TODO CHANGE TO CORRECT ACCESS RIGHT
                   .withAccessRights(customerId, AccessRight.MANAGE_NVI_CANDIDATE.name())
                   .withUserName(randomString())
                   .build();
    }

    private InputStream createRequestWithoutAccessRights(NviStatusRequest body) throws JsonProcessingException {
        return new HandlerRequestBuilder<NviStatusRequest>(JsonUtils.dtoObjectMapper)
                   .withBody(body)
                   .build();
    }

    private NviStatusRequest randomStatusRequest() {
        return new NviStatusRequest(UUID.randomUUID(),
                                    randomUri(),
                                    NviApprovalStatus.APPROVED);
    }
}