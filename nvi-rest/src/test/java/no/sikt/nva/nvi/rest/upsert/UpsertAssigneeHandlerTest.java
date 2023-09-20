package no.sikt.nva.nvi.rest.upsert;

import static java.util.UUID.randomUUID;
import static no.sikt.nva.nvi.rest.upsert.UpsertAssigneeHandler.CANDIDATE_IDENTIFIER;
import static no.sikt.nva.nvi.test.TestUtils.randomApplicableCandidate;
import static no.sikt.nva.nvi.test.TestUtils.randomApprovalStatus;
import static no.sikt.nva.nvi.test.TestUtils.randomCandidate;
import static no.sikt.nva.nvi.test.TestUtils.randomUsername;
import static no.unit.nva.testutils.RandomDataGenerator.randomString;
import static no.unit.nva.testutils.RandomDataGenerator.randomUri;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.nullValue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import com.amazonaws.services.lambda.runtime.Context;
import com.fasterxml.jackson.core.JsonProcessingException;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.nio.file.Path;
import java.time.Instant;
import java.util.Calendar;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import no.sikt.nva.nvi.CandidateResponse;
import no.sikt.nva.nvi.common.db.Candidate;
import no.sikt.nva.nvi.common.db.model.DbCandidate;
import no.sikt.nva.nvi.common.db.model.DbNviPeriod;
import no.sikt.nva.nvi.common.db.model.DbPublicationDate;
import no.sikt.nva.nvi.common.db.model.DbUsername;
import no.sikt.nva.nvi.common.model.PeriodStatus;
import no.sikt.nva.nvi.common.model.PeriodStatus.Status;
import no.sikt.nva.nvi.common.service.NviService;
import no.sikt.nva.nvi.rest.model.ApprovalDto;
import no.sikt.nva.nvi.test.LocalDynamoTest;
import no.unit.nva.auth.uriretriever.AuthorizedBackendUriRetriever;
import no.unit.nva.commons.json.JsonUtils;
import no.unit.nva.testutils.HandlerRequestBuilder;
import nva.commons.apigateway.AccessRight;
import nva.commons.apigateway.GatewayResponse;
import nva.commons.core.ioutils.IoUtils;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.zalando.problem.Problem;

public class UpsertAssigneeHandlerTest extends LocalDynamoTest {

    private Context context;
    private ByteArrayOutputStream output;
    private UpsertAssigneeHandler handler;
    private NviService nviService;
    private AuthorizedBackendUriRetriever uriRetriever;

    @BeforeEach
    void init() {
        output = new ByteArrayOutputStream();
        context = mock(Context.class);
        nviService = new NviService((initializeTestDatabase()));
        uriRetriever = mock(AuthorizedBackendUriRetriever.class);
        handler = new UpsertAssigneeHandler(nviService, uriRetriever);
    }

    @Test
    void shouldReturnUnauthorizedWhenMissingAccessRights() throws IOException {
        handler.handleRequest(createRequestWithoutAccessRights(), output, context);
        var response = GatewayResponse.fromOutputStream(output, Problem.class);

        assertThat(response.getStatusCode(), is(equalTo(HttpURLConnection.HTTP_UNAUTHORIZED)));
    }

    @Test
    void shouldReturnUnauthorizedWhenAssigneeIsNotFromTheSameInstitution() throws IOException {
        handler.handleRequest(createRequestWithDifferentInstitution(), output, context);
        var response = GatewayResponse.fromOutputStream(output, Problem.class);

        assertThat(response.getStatusCode(), is(equalTo(HttpURLConnection.HTTP_UNAUTHORIZED)));
    }

    @Test
    void shouldReturnUnauthorizedWhenAssigningToUserWithoutAccessRight() throws IOException {
        mockUserApiResponse("userResponseBodyWithoutAccessRight.json");
        var candidate = nviService.upsertCandidate(randomApplicableCandidate()).orElseThrow();
        var assignee = randomString();
        handler.handleRequest(createRequest(candidate, assignee), output, context);
        var response = GatewayResponse.fromOutputStream(output, CandidateResponse.class);

        assertThat(response.getStatusCode(), is(equalTo(HttpURLConnection.HTTP_UNAUTHORIZED)));
    }

    @Test
    void shouldReturnNotFoundWhenCandidateDoesNotExist() throws IOException {
        mockUserApiResponse("userResponseBodyWithAccessRight.json");
        var candidate = nonExistingCandidate();
        var assignee = randomString();
        handler.handleRequest(createRequest(candidate, assignee), output, context);
        var response = GatewayResponse.fromOutputStream(output, Problem.class);

        assertThat(response.getStatusCode(), is(equalTo(HttpURLConnection.HTTP_NOT_FOUND)));
    }

    @Test
    void shouldUpdateAssigneeWhenAssigneeIsNotPresent() throws IOException {
        mockUserApiResponse("userResponseBodyWithAccessRight.json");
        var reportingYear = "2100";
        DbCandidate dbCandidate = randomApplicableCandidate().copy()
                                      .publicationDate(DbPublicationDate.builder().year(reportingYear).build()).build();
        createPeriod(reportingYear);
        var candidate = nviService.upsertCandidate(dbCandidate).orElseThrow();
        var assignee = randomString();
        handler.handleRequest(createRequest(candidate, assignee), output, context);
        var response = GatewayResponse.fromOutputStream(output, CandidateResponse.class);

        assertThat(response.getBodyObject(CandidateResponse.class).approvalStatuses().get(0).assignee().value(),
                   is(equalTo(assignee)));
        assertThat(response.getStatusCode(), is(equalTo(HttpURLConnection.HTTP_OK)));
    }

    private DbNviPeriod createPeriod(String reportingYear) {

        var calendar = Calendar.getInstance();
        calendar.add(Calendar.MINUTE, 10);
        return nviService.createPeriod(
            new DbNviPeriod(reportingYear, calendar.toInstant(), randomUsername(),
                            randomUsername()));
    }

    @Test
    void shouldRemoveAssigneeWhenAssigneeIsPresent() throws IOException {
        mockUserApiResponse("userResponseBodyWithAccessRight.json");
        var candidate = nviService.upsertCandidate(randomApplicableCandidate()).orElseThrow();
        updateAssignee(candidate);
        handler.handleRequest(createRequest(candidate, null), output, context);
        var response = GatewayResponse.fromOutputStream(output, CandidateResponse.class);

        assertThat(response.getBodyObject(CandidateResponse.class).approvalStatuses().get(0).assignee(),
                   is(nullValue()));
        assertThat(response.getStatusCode(), is(equalTo(HttpURLConnection.HTTP_OK)));
    }

    @Test
    void shouldReturnBadRequestWhenUpdatingAssigneeAfterReportingPeriodHasBeenClosed()
        throws IOException {
        var reportingYear = "2100";
        var candidate = new Candidate(randomUUID(), randomCandidate(), List.of(randomApprovalStatus()), List.of(),
                                      new PeriodStatus(null, Status.NO_PERIOD));
        mockPeriod(reportingYear, candidate);
        var request = createRequest(candidate, null);
        handler.handleRequest(request, output, context);
        var response = GatewayResponse.fromOutputStream(output, Problem.class);

        assertThat(response.getStatusCode(), is(equalTo(HttpURLConnection.HTTP_BAD_REQUEST)));
    }

    private void mockPeriod(String reportingYear, Candidate candidate) {
        nviService = mock(NviService.class);
        when(nviService.getPeriod(any()))
            .thenReturn(new DbNviPeriod(reportingYear, Instant.now(), randomUsername(), randomUsername()));
        when(nviService.findCandidateById(any()))
            .thenReturn(Optional.of(candidate));
        handler = new UpsertAssigneeHandler(nviService, uriRetriever);
    }

    private Candidate nonExistingCandidate() {
        var candidate = nviService.upsertCandidate(randomApplicableCandidate()).orElseThrow();
        return new Candidate(randomUUID(), candidate.candidate(), candidate.approvalStatuses(),
                             Collections.emptyList(), new PeriodStatus(null, Status.NO_PERIOD));
    }

    private void updateAssignee(Candidate candidate) {
        nviService.updateApprovalStatus(candidate.identifier(),
                                        candidate.approvalStatuses().get(0).copy()
                                            .assignee(new DbUsername(randomString()))
                                            .build());
    }

    private void mockUserApiResponse(String responseFile) {
        when(uriRetriever.getRawContent(any(), any()))
            .thenReturn(Optional.ofNullable(IoUtils.stringFromResources(Path.of(responseFile))));
    }

    private InputStream createRequest(Candidate candidate, String newAssignee)
        throws JsonProcessingException {
        var approvalToUpdate = candidate.approvalStatuses().get(0);
        var requestBody = new ApprovalDto(newAssignee, approvalToUpdate.institutionId());
        var customerId = randomUri();
        return new HandlerRequestBuilder<ApprovalDto>(JsonUtils.dtoObjectMapper)
                   .withBody(randomAssigneeRequest())
                   .withCurrentCustomer(customerId)
                   .withTopLevelCristinOrgId(requestBody.institutionId())
                   .withAccessRights(customerId, AccessRight.MANAGE_NVI_CANDIDATE.name())
                   .withUserName(randomString())
                   .withBody(requestBody)
                   .withPathParameters(Map.of(CANDIDATE_IDENTIFIER, candidate.identifier().toString()))
                   .build();
    }

    private InputStream createRequestWithoutAccessRights() throws JsonProcessingException {
        return new HandlerRequestBuilder<ApprovalDto>(JsonUtils.dtoObjectMapper)
                   .withBody(randomAssigneeRequest())
                   .build();
    }

    private InputStream createRequestWithDifferentInstitution() throws JsonProcessingException {
        return new HandlerRequestBuilder<ApprovalDto>(JsonUtils.dtoObjectMapper)
                   .withBody(randomAssigneeRequest())
                   .withCurrentCustomer(randomUri())
                   .build();
    }

    private ApprovalDto randomAssigneeRequest() {
        return new ApprovalDto(randomString(), randomUri());
    }
}
