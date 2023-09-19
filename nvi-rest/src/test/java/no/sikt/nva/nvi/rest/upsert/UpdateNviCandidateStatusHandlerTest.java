package no.sikt.nva.nvi.rest.upsert;

import static java.util.Collections.emptyList;
import static no.sikt.nva.nvi.test.TestUtils.randomCandidate;
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
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import no.sikt.nva.nvi.CandidateResponse;
import no.sikt.nva.nvi.common.db.Candidate;
import no.sikt.nva.nvi.common.db.model.DbApprovalStatus;
import no.sikt.nva.nvi.common.db.model.DbCandidate;
import no.sikt.nva.nvi.common.db.model.DbNviPeriod;
import no.sikt.nva.nvi.common.db.model.DbPublicationDate;
import no.sikt.nva.nvi.common.db.model.DbStatus;
import no.sikt.nva.nvi.common.db.model.DbUsername;
import no.sikt.nva.nvi.common.model.ReportStatus;
import no.sikt.nva.nvi.common.service.NviService;
import no.sikt.nva.nvi.rest.fetch.ApprovalStatus;
import no.unit.nva.commons.json.JsonUtils;
import no.unit.nva.testutils.HandlerRequestBuilder;
import nva.commons.apigateway.AccessRight;
import nva.commons.apigateway.GatewayResponse;
import org.hamcrest.Matchers;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.zalando.problem.Problem;

class UpdateNviCandidateStatusHandlerTest {

    private Context context;
    private ByteArrayOutputStream output;
    private UpdateNviCandidateStatusHandler handler;
    private NviService nviService;

    @BeforeEach
    void init() {
        output = new ByteArrayOutputStream();
        context = mock(Context.class);
        nviService = mock(NviService.class);
        handler = new UpdateNviCandidateStatusHandler(nviService);
    }

    @Test
    void shouldReturnUnauthorizedWhenMissingAccessRights() throws IOException {
        when(nviService.findById(any())).thenReturn(candidateWithPublicationDate(randomString()));
        handler.handleRequest(createRequestWithoutAccessRights(randomStatusRequest(randomUri())), output, context);
        var response = GatewayResponse.fromOutputStream(output, Problem.class);

        assertThat(response.getStatusCode(), is(equalTo(HttpURLConnection.HTTP_UNAUTHORIZED)));
    }

    @ParameterizedTest
    @EnumSource(NviApprovalStatus.class)
    void shouldReturnCandidateResponseWhenSuccessful(NviApprovalStatus status) throws IOException {
        URI institutionId = randomUri();
        var nviStatusRequest = new NviStatusRequest(UUID.randomUUID(), institutionId, status);
        var innerStatus = DbStatus.parse(status.getValue());
        var request = createRequest(nviStatusRequest, institutionId);
        var response = mockServiceResponse(nviStatusRequest, innerStatus);
        when(nviService.updateApprovalStatus(any(), any())).thenReturn(response);
        when(nviService.findById(any())).thenReturn(candidateWithPublicationDate(randomString()));
        handler.handleRequest(request, output, context);

        var gatewayResponse = GatewayResponse.fromOutputStream(output, CandidateResponse.class);
        var bodyAsInstance = gatewayResponse.getBodyObject(CandidateResponse.class);
        var approvalStatus = response.approvalStatuses().get(0);
        assertThat(bodyAsInstance,
                   is(equalTo(createResponse(nviStatusRequest, response, innerStatus, approvalStatus))));
    }

    @Test
    void shouldBeForbiddenToChangeStatusOfOtherInstitution() throws IOException {
        when(nviService.findById(any())).thenReturn(candidateWithPublicationDate(randomString()));
        var body = randomStatusRequest(randomUri());
        var request = createRequest(body, randomUri());
        handler.handleRequest(request, output, context);
        var response = GatewayResponse.fromOutputStream(output, Problem.class);

        assertThat(response.getStatusCode(), is(equalTo(HttpURLConnection.HTTP_FORBIDDEN)));
    }

    @Test
    void shouldReturnBadRequestWhenUpdatingStatusAfterReportingPeriodHasBeenClosed()
        throws IOException {
        var reportingYear = "2100";
        var institutionId = randomUri();
        when(nviService.findById(any())).thenReturn(candidateWithPublicationDate(reportingYear));
        when(nviService.getPeriod(any())).thenReturn(getDbNviPeriod(reportingYear));
        handler.handleRequest(createRequest(randomStatusRequest(institutionId), institutionId), output, context);
        var response = GatewayResponse.fromOutputStream(output, Problem.class);

        assertThat(response.getStatusCode(), is(Matchers.equalTo(HttpURLConnection.HTTP_BAD_REQUEST)));
    }

    private static DbNviPeriod getDbNviPeriod(String reportingYear) {
        return new DbNviPeriod(reportingYear, Instant.now(), new DbUsername(randomString()),
                               new DbUsername(randomString()));
    }

    private static Optional<Candidate> candidateWithPublicationDate(String reportingYear) {
        var candidate = randomCandidate().copy()
                                .publicationDate(DbPublicationDate.builder().year(reportingYear).build())
                                .build();
        return Optional.of(new Candidate(UUID.randomUUID(), candidate, List.of(), List.of(),
                                         ReportStatus.NOT_REPORTABLE));
    }

    private static CandidateResponse createResponse(
        NviStatusRequest nviStatusRequest,
        Candidate response, DbStatus status,
        DbApprovalStatus approvalStatus) {
        return CandidateResponse.builder()
                   .withId(nviStatusRequest.candidateId())
                   .withPublicationId(response.candidate().publicationId())
                   .withApprovalStatuses(
                       List.of(createApprovalStatus(nviStatusRequest, status, approvalStatus)))
                   .withPoints(emptyList())
                   .withNotes(emptyList())
                   .withReportStatus(ReportStatus.NOT_REPORTABLE.getValue())
                   .build();
    }

    private static ApprovalStatus createApprovalStatus(NviStatusRequest nviStatusRequest, DbStatus status,
                                                       DbApprovalStatus approvalStatus) {
        return ApprovalStatus.builder()
                   .withInstitutionId(nviStatusRequest.institutionId())
                   .withStatus(status)
                   .withAssignee(new DbUsername(approvalStatus.assignee().value()))
                   .withFinalizedBy(new DbUsername(approvalStatus.finalizedBy().value()))
                   .withFinalizedDate(approvalStatus.finalizedDate())
                   .build();
    }

    private static Candidate mockServiceResponse(NviStatusRequest nviStatusRequest,
                                                 DbStatus status) {
        var username = new DbUsername(randomString());
        return new Candidate(
            nviStatusRequest.candidateId(),
            DbCandidate.builder()
                .publicationId(nviStatusRequest.institutionId())
                .points(emptyList())
                .build(),
            List.of(new DbApprovalStatus(nviStatusRequest.institutionId(),
                                         status,
                                         username,
                                         username,
                                         Instant.now())),
            emptyList(),
            no.sikt.nva.nvi.common.model.ReportStatus.NOT_REPORTABLE);
    }

    private static InputStream createRequest(NviStatusRequest body, URI customerId) throws JsonProcessingException {
        return new HandlerRequestBuilder<NviStatusRequest>(JsonUtils.dtoObjectMapper)
                   .withBody(body)
                   .withCurrentCustomer(customerId)
                   .withPathParameters(Map.of("candidateIdentifier", body.candidateId().toString()))
                   .withTopLevelCristinOrgId(customerId)
                   //TODO CHANGE TO CORRECT ACCESS RIGHT
                   .withAccessRights(customerId, AccessRight.MANAGE_NVI_CANDIDATE.name())
                   .withUserName(randomString())
                   .build();
    }

    private InputStream createRequest(NviStatusRequest body) throws JsonProcessingException {
        return createRequest(body, randomUri());
    }

    private InputStream createRequestWithoutAccessRights(NviStatusRequest body) throws JsonProcessingException {
        return new HandlerRequestBuilder<NviStatusRequest>(JsonUtils.dtoObjectMapper)
                   .withBody(body)
                   .withPathParameters(Map.of("candidateIdentifier", UUID.randomUUID().toString()))
                   .build();
    }

    private NviStatusRequest randomStatusRequest(URI institutionId) {
        return new NviStatusRequest(UUID.randomUUID(),
                                    institutionId,
                                    NviApprovalStatus.APPROVED);
    }
}