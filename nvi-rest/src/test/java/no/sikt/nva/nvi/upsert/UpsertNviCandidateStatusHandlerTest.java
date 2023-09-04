package no.sikt.nva.nvi.upsert;

import static java.util.Collections.emptyList;
import static java.util.Collections.emptyMap;
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
import java.util.List;
import java.util.UUID;
import java.util.stream.Stream;
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
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.zalando.problem.Problem;

class UpsertNviCandidateStatusHandlerTest {

    private Context context;
    private ByteArrayOutputStream output;
    private UpsertNviCandidateStatusHandler handler;
    private NviService nviService;

    static Stream<Status> statusEnumSource() {
        return Stream.of(Status.APPROVED, Status.REJECTED, Status.PENDING);
    }

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
        when(nviService.upsertApproval(any(), any())).thenThrow(IllegalArgumentException.class);
        handler.handleRequest(createRequest(randomStatusRequest()), output, context);
        var response = GatewayResponse.fromOutputStream(output, Problem.class);

        assertThat(response.getStatusCode(), is(Matchers.equalTo(HttpURLConnection.HTTP_BAD_REQUEST)));
    }

    @ParameterizedTest
    @EnumSource(NviApprovalStatus.class)
    void shouldReturnCandidateResponseWhenSuccessful(NviApprovalStatus status) throws IOException {
        var nviStatusRequest = new NviStatusRequest(UUID.randomUUID(), randomUri(), status);
        Status innerStatus = Status.parse(status.getValue());
        var request = createRequest(nviStatusRequest);
        var response = mockServiceResponse(nviStatusRequest, innerStatus);
        var approvalStatus = response.candidate()
                                 .approvalStatuses()
                                 .get(0);
        when(nviService.upsertApproval(any(), any())).thenReturn(response);

        handler.handleRequest(request, output, context);

        var gatewayResponse = GatewayResponse.fromOutputStream(output, CandidateResponse.class);
        var bodyAsInstance = gatewayResponse.getBodyObject(CandidateResponse.class);
        assertThat(bodyAsInstance,
                   is(equalTo(createResponse(nviStatusRequest, response, innerStatus, approvalStatus))));
    }

    private static CandidateResponse createResponse(
        NviStatusRequest nviStatusRequest,
        CandidateWithIdentifier response, Status status,
        no.sikt.nva.nvi.common.model.business.ApprovalStatus approvalStatus) {
        return CandidateResponse.builder()
                   .withId(nviStatusRequest.candidateId())
                   .withPublicationId(response.candidate().publicationId())
                   .withApprovalStatuses(
                       List.of(ApprovalStatus.builder()
                                   .withInstitutionId(nviStatusRequest.institutionId())
                                   .withStatus(status)
                                   .withFinalizedBy(new Username(approvalStatus.finalizedBy().value()))
                                   .withFinalizedDate(approvalStatus.finalizedDate())
                                   .build()))
                   .withPoints(emptyMap())
                   .withNotes(emptyList())
                   .build();
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
                .withPoints(emptyMap())
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