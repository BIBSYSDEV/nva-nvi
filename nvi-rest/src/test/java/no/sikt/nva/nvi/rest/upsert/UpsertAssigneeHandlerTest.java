package no.sikt.nva.nvi.rest.upsert;

import static java.util.UUID.randomUUID;
import static no.sikt.nva.nvi.test.TestUtils.randomApplicableCandidateBuilder;
import static no.sikt.nva.nvi.rest.upsert.UpsertAssigneeHandler.CANDIDATE_IDENTIFIER;
import static no.unit.nva.testutils.RandomDataGenerator.randomString;
import static no.unit.nva.testutils.RandomDataGenerator.randomUri;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.equalTo;
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
import java.nio.file.Path;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import no.sikt.nva.nvi.CandidateResponse;
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
        var candidate = nviService.upsertCandidate(randomApplicableCandidateBuilder()).orElseThrow();
        var approvalToUpdate = candidate.approvalStatuses().get(0);
        var assignee = randomString();
        var requestBody = new ApprovalDto(assignee, approvalToUpdate.institutionId());
        var request = createRequest(candidate.identifier(), requestBody, assignee);
        handler.handleRequest(request, output, context);
        var response = GatewayResponse.fromOutputStream(output, CandidateResponse.class);

        assertThat(response.getStatusCode(), is(equalTo(HttpURLConnection.HTTP_UNAUTHORIZED)));
    }

    @Test
    void shouldReturnNotFoundWhenCandidateDoesNotExist() throws IOException {
        mockUserApiResponse("userResponseBodyWithAccessRight.json");
        var candidate = nviService.upsertCandidate(randomApplicableCandidateBuilder()).orElseThrow();
        var approvalToUpdate = candidate.approvalStatuses().get(0);
        var assignee = randomString();
        var requestBody = new ApprovalDto(assignee, approvalToUpdate.institutionId());
        var request = createRequest(randomUUID(), requestBody, assignee);
        handler.handleRequest(request, output, context);
        var response = GatewayResponse.fromOutputStream(output, Problem.class);

        assertThat(response.getStatusCode(), is(equalTo(HttpURLConnection.HTTP_NOT_FOUND)));
    }

    @Test
    void shouldUpdateAssignee() throws IOException {
        mockUserApiResponse("userResponseBodyWithAccessRight.json");
        var candidate = nviService.upsertCandidate(randomApplicableCandidateBuilder()).orElseThrow();
        var approvalToUpdate = candidate.approvalStatuses().get(0);
        var assignee = randomString();
        var requestBody = new ApprovalDto(assignee, approvalToUpdate.institutionId());
        var request = createRequest(candidate.identifier(), requestBody, assignee);
        handler.handleRequest(request, output, context);
        var response = GatewayResponse.fromOutputStream(output, CandidateResponse.class);
        var candidateResponse = response.getBodyObject(CandidateResponse.class);
        var newAssignee = candidateResponse.approvalStatuses().get(0).assignee().value();

        assertThat(newAssignee, is(equalTo(assignee)));
        assertThat(response.getStatusCode(), is(equalTo(HttpURLConnection.HTTP_OK)));
    }

    private void mockUserApiResponse(String responseFile) {
        when(uriRetriever.getRawContent(any(), any()))
            .thenReturn(Optional.ofNullable(IoUtils.stringFromResources(Path.of(responseFile))));
    }

    private InputStream createRequest(UUID candidate, ApprovalDto assigneeRequest, String assignee)
        throws JsonProcessingException {
        var customerId = randomUri();
        return new HandlerRequestBuilder<ApprovalDto>(JsonUtils.dtoObjectMapper)
                   .withBody(randomAssigneeRequest())
                   .withCurrentCustomer(customerId)
                   .withTopLevelCristinOrgId(assigneeRequest.institutionId())
                   .withAccessRights(customerId, AccessRight.MANAGE_NVI_CANDIDATE.name())
                   .withUserName(assignee)
                   .withBody(assigneeRequest)
                   .withPathParameters(Map.of(CANDIDATE_IDENTIFIER, candidate.toString()))
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
