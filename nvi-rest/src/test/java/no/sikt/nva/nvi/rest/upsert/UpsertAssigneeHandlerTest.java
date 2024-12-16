package no.sikt.nva.nvi.rest.upsert;

import static java.util.UUID.randomUUID;
import static no.sikt.nva.nvi.rest.upsert.UpsertAssigneeHandler.CANDIDATE_IDENTIFIER;
import static no.sikt.nva.nvi.test.TestUtils.createUpsertCandidateRequest;
import static no.sikt.nva.nvi.test.TestUtils.periodRepositoryReturningClosedPeriod;
import static no.sikt.nva.nvi.test.TestUtils.periodRepositoryReturningNotOpenedPeriod;
import static no.sikt.nva.nvi.test.TestUtils.periodRepositoryReturningOpenedPeriod;
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
import java.util.Calendar;
import java.util.Map;
import java.util.Optional;
import no.sikt.nva.nvi.common.db.CandidateRepository;
import no.sikt.nva.nvi.common.db.PeriodRepository;
import no.sikt.nva.nvi.common.model.UpdateAssigneeRequest;
import no.sikt.nva.nvi.common.model.UpdateStatusRequest;
import no.sikt.nva.nvi.common.service.dto.CandidateDto;
import no.sikt.nva.nvi.common.service.model.ApprovalStatus;
import no.sikt.nva.nvi.common.service.model.Candidate;
import no.sikt.nva.nvi.rest.model.UpsertAssigneeRequest;
import no.sikt.nva.nvi.test.FakeViewingScopeValidator;
import no.sikt.nva.nvi.test.LocalDynamoTest;
import no.sikt.nva.nvi.test.TestUtils;
import no.unit.nva.auth.uriretriever.AuthorizedBackendUriRetriever;
import no.unit.nva.commons.json.JsonUtils;
import no.unit.nva.testutils.HandlerRequestBuilder;
import nva.commons.apigateway.AccessRight;
import nva.commons.apigateway.GatewayResponse;
import nva.commons.core.ioutils.IoUtils;
import org.hamcrest.CoreMatchers;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.zalando.problem.Problem;

class UpsertAssigneeHandlerTest extends LocalDynamoTest {

    private static final int YEAR = Calendar.getInstance().getWeekYear();
    private static final String USER_RESPONSE_BODY_WITHOUT_ACCESS_RIGHT_JSON = "userResponseBodyWithoutAccessRight"
                                                                               + ".json";
    private static final String USER_RESPONSE_BODY_WITH_ACCESS_RIGHT_JSON = "userResponseBodyWithAccessRight.json";
    private Context context;
    private ByteArrayOutputStream output;
    private UpsertAssigneeHandler handler;
    private CandidateRepository candidateRepository;
    private PeriodRepository periodRepository;
    private AuthorizedBackendUriRetriever uriRetriever;
    private FakeViewingScopeValidator viewingScopeValidator;

    @BeforeEach
    void init() {
        output = new ByteArrayOutputStream();
        context = mock(Context.class);
        candidateRepository = new CandidateRepository(initializeTestDatabase());
        periodRepository = periodRepositoryReturningOpenedPeriod(YEAR);
        uriRetriever = mock(AuthorizedBackendUriRetriever.class);
        viewingScopeValidator = new FakeViewingScopeValidator(true);
        handler = new UpsertAssigneeHandler(candidateRepository, periodRepository, uriRetriever, viewingScopeValidator);
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
        mockUserApiResponse(USER_RESPONSE_BODY_WITHOUT_ACCESS_RIGHT_JSON);
        var candidate = createCandidate();
        var assignee = randomString();
        handler.handleRequest(createRequest(candidate, assignee), output, context);
        var response = GatewayResponse.fromOutputStream(output, CandidateDto.class);

        assertThat(response.getStatusCode(), is(equalTo(HttpURLConnection.HTTP_UNAUTHORIZED)));
    }

    @Test
    void shouldReturnUnauthorizedWhenCandidateIsNotInUsersViewingScope() throws IOException {
        mockUserApiResponse(USER_RESPONSE_BODY_WITHOUT_ACCESS_RIGHT_JSON);
        var candidate = createCandidate();
        var assignee = randomString();
        viewingScopeValidator = new FakeViewingScopeValidator(false);
        handler = new UpsertAssigneeHandler(candidateRepository, periodRepository, uriRetriever, viewingScopeValidator);
        handler.handleRequest(createRequest(candidate, assignee), output, context);
        var response = GatewayResponse.fromOutputStream(output, Problem.class);
        assertThat(response.getStatusCode(), is(CoreMatchers.equalTo(HttpURLConnection.HTTP_UNAUTHORIZED)));
    }

    @Test
    void shouldReturnNotFoundWhenCandidateDoesNotExist() throws IOException {
        mockUserApiResponse(USER_RESPONSE_BODY_WITH_ACCESS_RIGHT_JSON);
        handler.handleRequest(createRequestWithNonExistingCandidate(), output, context);
        var response = GatewayResponse.fromOutputStream(output, Problem.class);

        assertThat(response.getStatusCode(), is(equalTo(HttpURLConnection.HTTP_NOT_FOUND)));
    }

    @Test
    void shouldReturnConflictWhenUpdatingAssigneeAndReportingPeriodIsClosed() throws IOException {
        mockUserApiResponse(USER_RESPONSE_BODY_WITH_ACCESS_RIGHT_JSON);
        var candidate = createCandidate();
        var assignee = randomString();
        var periodRepository = periodRepositoryReturningClosedPeriod(YEAR);
        var handler = new UpsertAssigneeHandler(candidateRepository, periodRepository, uriRetriever,
                                                viewingScopeValidator);
        handler.handleRequest(createRequest(candidate, assignee), output, context);
        var response = GatewayResponse.fromOutputStream(output, Problem.class);

        assertThat(response.getStatusCode(), is(equalTo(HttpURLConnection.HTTP_CONFLICT)));
    }

    @Test
    void shouldReturnConflictWhenUpdatingAssigneeAndReportingPeriodIsNotOpenedYet() throws IOException {
        mockUserApiResponse(USER_RESPONSE_BODY_WITH_ACCESS_RIGHT_JSON);
        var candidate = createCandidate();
        var assignee = randomString();
        var periodRepository = periodRepositoryReturningNotOpenedPeriod(YEAR);
        var handler = new UpsertAssigneeHandler(candidateRepository, periodRepository, uriRetriever,
                                                viewingScopeValidator);
        handler.handleRequest(createRequest(candidate, assignee), output, context);
        var response = GatewayResponse.fromOutputStream(output, Problem.class);

        assertThat(response.getStatusCode(), is(equalTo(HttpURLConnection.HTTP_CONFLICT)));
    }

    @Test
    void shouldUpdateAssigneeWhenAssigneeIsNotPresent() throws IOException {
        mockUserApiResponse(USER_RESPONSE_BODY_WITH_ACCESS_RIGHT_JSON);
        var candidate = createCandidate();
        var assignee = randomString();
        handler.handleRequest(createRequest(candidate, assignee), output, context);
        var response = GatewayResponse.fromOutputStream(output, CandidateDto.class);

        assertThat(response.getBodyObject(CandidateDto.class).approvals().get(0).assignee(),
                   is(equalTo(assignee)));
        assertThat(response.getStatusCode(), is(equalTo(HttpURLConnection.HTTP_OK)));
    }

    @Test
    void shouldRemoveAssigneeWhenAssigneeIsPresent() throws IOException {
        mockUserApiResponse(USER_RESPONSE_BODY_WITH_ACCESS_RIGHT_JSON);
        var candidate = createCandidate();
        handler.handleRequest(createRequest(candidate, null), output, context);
        var response = GatewayResponse.fromOutputStream(output, CandidateDto.class);

        assertThat(response.getBodyObject(CandidateDto.class).approvals().get(0).assignee(),
                   is(nullValue()));
        assertThat(response.getStatusCode(), is(equalTo(HttpURLConnection.HTTP_OK)));
    }

    @Test
    void shouldUpdateAssigneeWhenExistingApprovalIsFinalized() throws IOException {
        mockUserApiResponse(USER_RESPONSE_BODY_WITH_ACCESS_RIGHT_JSON);
        var newAssignee = randomString();
        var candidate = candidateWithFinalizedApproval(newAssignee);
        handler.handleRequest(createRequest(candidate, newAssignee), output, context);
        var response =
            GatewayResponse.fromOutputStream(output, CandidateDto.class);

        assertThat(response.getBodyObject(CandidateDto.class).approvals().get(0).assignee(),
                   is(equalTo(newAssignee)));
    }

    private Candidate createCandidate() {
        return TestUtils.randomApplicableCandidate(candidateRepository, periodRepository);
    }

    private Candidate candidateWithFinalizedApproval(String newAssignee) {
        var institutionId = randomUri();
        var request = createUpsertCandidateRequest(institutionId).build();
        Candidate.upsert(request, candidateRepository, periodRepository);
        var candidate = Candidate.fetchByPublicationId(request::publicationId, candidateRepository, periodRepository);
        candidate.updateApproval(new UpdateAssigneeRequest(institutionId, newAssignee));
        candidate.updateApproval(
            new UpdateStatusRequest(institutionId, ApprovalStatus.APPROVED, randomString(), randomString()));
        return candidate;
    }

    private void mockUserApiResponse(String responseFile) {
        when(uriRetriever.getRawContent(any(), any())).thenReturn(
            Optional.ofNullable(IoUtils.stringFromResources(Path.of(responseFile))));
    }

    private InputStream createRequest(Candidate candidate, String newAssignee) throws JsonProcessingException {
        var approvalToUpdate = candidate.toDto().approvals().getFirst();
        var requestBody = new UpsertAssigneeRequest(newAssignee, approvalToUpdate.institutionId());
        var customerId = randomUri();
        return new HandlerRequestBuilder<UpsertAssigneeRequest>(JsonUtils.dtoObjectMapper).withBody(
                randomAssigneeRequest())
                   .withCurrentCustomer(customerId)
                   .withTopLevelCristinOrgId(requestBody.institutionId())
                   .withAccessRights(customerId, AccessRight.MANAGE_NVI_CANDIDATES)
                   .withUserName(randomString())
                   .withBody(requestBody)
                   .withPathParameters(Map.of(CANDIDATE_IDENTIFIER, candidate.getIdentifier().toString()))
                   .build();
    }

    private InputStream createRequestWithNonExistingCandidate() throws JsonProcessingException {
        var approvalToUpdate = createCandidate().toDto().approvals().get(0);
        var requestBody = new UpsertAssigneeRequest(randomString(), approvalToUpdate.institutionId());
        var customerId = randomUri();
        return new HandlerRequestBuilder<UpsertAssigneeRequest>(JsonUtils.dtoObjectMapper).withBody(
                randomAssigneeRequest())
                   .withCurrentCustomer(customerId)
                   .withTopLevelCristinOrgId(requestBody.institutionId())
                   .withAccessRights(customerId, AccessRight.MANAGE_NVI_CANDIDATES)
                   .withUserName(randomString())
                   .withBody(requestBody)
                   .withPathParameters(Map.of(CANDIDATE_IDENTIFIER, randomUUID().toString()))
                   .build();
    }

    private InputStream createRequestWithoutAccessRights() throws JsonProcessingException {
        return new HandlerRequestBuilder<UpsertAssigneeRequest>(JsonUtils.dtoObjectMapper).withBody(
                randomAssigneeRequest())
                   .build();
    }

    private InputStream createRequestWithDifferentInstitution() throws JsonProcessingException {
        return new HandlerRequestBuilder<UpsertAssigneeRequest>(JsonUtils.dtoObjectMapper).withBody(
                randomAssigneeRequest())
                   .withCurrentCustomer(randomUri())
                   .build();
    }

    private UpsertAssigneeRequest randomAssigneeRequest() {
        return new UpsertAssigneeRequest(randomString(), randomUri());
    }
}
