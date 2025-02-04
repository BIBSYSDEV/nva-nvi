package no.sikt.nva.nvi.rest.upsert;

import static java.util.Collections.emptyList;
import static java.util.UUID.randomUUID;
import static no.sikt.nva.nvi.test.TestUtils.CURRENT_YEAR;
import static no.sikt.nva.nvi.test.TestUtils.periodRepositoryReturningClosedPeriod;
import static no.sikt.nva.nvi.test.TestUtils.periodRepositoryReturningNotOpenedPeriod;
import static no.unit.nva.testutils.RandomDataGenerator.randomString;
import static no.unit.nva.testutils.RandomDataGenerator.randomUri;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.nullValue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.core.JsonProcessingException;
import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.util.List;
import java.util.Map;
import no.sikt.nva.nvi.common.model.UpdateAssigneeRequest;
import no.sikt.nva.nvi.common.model.UpdateStatusRequest;
import no.sikt.nva.nvi.common.service.dto.CandidateDto;
import no.sikt.nva.nvi.common.service.model.ApprovalStatus;
import no.sikt.nva.nvi.common.service.model.Candidate;
import no.sikt.nva.nvi.rest.BaseCandidateRestHandlerTest;
import no.sikt.nva.nvi.rest.model.UpsertAssigneeRequest;
import no.sikt.nva.nvi.test.FakeViewingScopeValidator;
import no.sikt.nva.nvi.test.TestUtils;
import no.unit.nva.clients.GetUserResponse;
import no.unit.nva.clients.IdentityServiceClient;
import no.unit.nva.commons.json.JsonUtils;
import no.unit.nva.testutils.HandlerRequestBuilder;
import nva.commons.apigateway.AccessRight;
import nva.commons.apigateway.ApiGatewayHandler;
import nva.commons.apigateway.GatewayResponse;
import nva.commons.apigateway.exceptions.NotFoundException;
import org.hamcrest.CoreMatchers;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.zalando.problem.Problem;

class UpsertAssigneeHandlerTest extends BaseCandidateRestHandlerTest {

  private static final IdentityServiceClient mockIdentityServiceClient =
      mock(IdentityServiceClient.class);

  @Override
  protected ApiGatewayHandler<UpsertAssigneeRequest, CandidateDto> createHandler() {
    return new UpsertAssigneeHandler(
        candidateRepository,
        periodRepository,
        mockIdentityServiceClient,
        mockViewingScopeValidator);
  }

  @BeforeEach
  void setUp() {
    resourcePathParameter = "candidateIdentifier";
  }

  @Test
  void shouldReturnUnauthorizedWhenMissingAccessRights() throws IOException {
    handler.handleRequest(createRequestWithoutAccessRights(), output, CONTEXT);
    var response = GatewayResponse.fromOutputStream(output, Problem.class);

    assertThat(response.getStatusCode(), is(equalTo(HttpURLConnection.HTTP_UNAUTHORIZED)));
  }

  @Test
  void shouldReturnUnauthorizedWhenAssigneeIsNotFromTheSameInstitution() throws IOException {
    handler.handleRequest(createRequestWithDifferentInstitution(), output, CONTEXT);
    var response = GatewayResponse.fromOutputStream(output, Problem.class);

    assertThat(response.getStatusCode(), is(equalTo(HttpURLConnection.HTTP_UNAUTHORIZED)));
  }

  @Test
  void shouldReturnUnauthorizedWhenAssigningToUserWithoutAccessRight() throws IOException {
    var candidate = createCandidate();
    var assignee = randomString();
    mockNoAccessForUser(assignee);
    handler.handleRequest(createRequest(candidate, assignee), output, CONTEXT);
    var response = GatewayResponse.fromOutputStream(output, CandidateDto.class);

    assertThat(response.getStatusCode(), is(equalTo(HttpURLConnection.HTTP_UNAUTHORIZED)));
  }

  @Test
  void shouldReturnUnauthorizedWhenCandidateIsNotInUsersViewingScope() throws IOException {
    var candidate = createCandidate();
    var assignee = randomString();
    var viewingScopeValidatorReturningFalse = new FakeViewingScopeValidator(false);
    handler =
        new UpsertAssigneeHandler(
            candidateRepository,
            periodRepository,
            mockIdentityServiceClient,
            viewingScopeValidatorReturningFalse);
    handler.handleRequest(createRequest(candidate, assignee), output, CONTEXT);
    var response = GatewayResponse.fromOutputStream(output, Problem.class);
    assertThat(
        response.getStatusCode(), is(CoreMatchers.equalTo(HttpURLConnection.HTTP_UNAUTHORIZED)));
  }

  @Test
  void shouldReturnNotFoundWhenCandidateDoesNotExist() throws IOException {
    var assignee = randomString();
    mockNviCuratorAccessForUser(assignee);
    handler.handleRequest(createRequestWithNonExistingCandidate(assignee), output, CONTEXT);
    var response = GatewayResponse.fromOutputStream(output, Problem.class);

    assertThat(response.getStatusCode(), is(equalTo(HttpURLConnection.HTTP_NOT_FOUND)));
  }

  @Test
  void shouldReturnConflictWhenUpdatingAssigneeAndReportingPeriodIsClosed() throws IOException {
    var candidate = createCandidate();
    var assignee = randomString();
    mockNviCuratorAccessForUser(assignee);
    var periodRepositoryForClosedPeriod = periodRepositoryReturningClosedPeriod(CURRENT_YEAR);
    var customHandler =
        new UpsertAssigneeHandler(
            candidateRepository,
            periodRepositoryForClosedPeriod,
            mockIdentityServiceClient,
            mockViewingScopeValidator);
    customHandler.handleRequest(createRequest(candidate, assignee), output, CONTEXT);
    var response = GatewayResponse.fromOutputStream(output, Problem.class);

    assertThat(response.getStatusCode(), is(equalTo(HttpURLConnection.HTTP_CONFLICT)));
  }

  @Test
  void shouldReturnConflictWhenUpdatingAssigneeAndReportingPeriodIsNotOpenedYet()
      throws IOException {
    var candidate = createCandidate();
    var assignee = randomString();
    mockNviCuratorAccessForUser(assignee);
    var periodRepositoryForNotYetOpenPeriod =
        periodRepositoryReturningNotOpenedPeriod(CURRENT_YEAR);
    var customHandler =
        new UpsertAssigneeHandler(
            candidateRepository,
            periodRepositoryForNotYetOpenPeriod,
            mockIdentityServiceClient,
            mockViewingScopeValidator);
    customHandler.handleRequest(createRequest(candidate, assignee), output, CONTEXT);
    var response = GatewayResponse.fromOutputStream(output, Problem.class);

    assertThat(response.getStatusCode(), is(equalTo(HttpURLConnection.HTTP_CONFLICT)));
  }

  @Test
  void shouldUpdateAssigneeWhenAssigneeIsNotPresent() throws IOException {
    var candidate = createCandidate();
    var assignee = randomString();
    mockNviCuratorAccessForUser(assignee);
    handler.handleRequest(createRequest(candidate, assignee), output, CONTEXT);
    var response = GatewayResponse.fromOutputStream(output, CandidateDto.class);

    assertThat(
        response.getBodyObject(CandidateDto.class).approvals().getFirst().assignee(),
        is(equalTo(assignee)));
    assertThat(response.getStatusCode(), is(equalTo(HttpURLConnection.HTTP_OK)));
  }

  @Test
  void shouldRemoveAssigneeWhenAssigneeIsPresent() throws IOException {
    var candidate = createCandidate();
    handler.handleRequest(createRequest(candidate, null), output, CONTEXT);
    var response = GatewayResponse.fromOutputStream(output, CandidateDto.class);

    assertThat(
        response.getBodyObject(CandidateDto.class).approvals().getFirst().assignee(),
        is(nullValue()));
    assertThat(response.getStatusCode(), is(equalTo(HttpURLConnection.HTTP_OK)));
  }

  @Test
  void shouldUpdateAssigneeWhenExistingApprovalIsFinalized() throws IOException {
    var newAssignee = randomString();
    mockNviCuratorAccessForUser(newAssignee);
    var candidate = candidateWithFinalizedApproval(newAssignee);
    handler.handleRequest(createRequest(candidate, newAssignee), output, CONTEXT);
    var response = GatewayResponse.fromOutputStream(output, CandidateDto.class);

    assertThat(
        response.getBodyObject(CandidateDto.class).approvals().getFirst().assignee(),
        is(equalTo(newAssignee)));
  }

  private Candidate createCandidate() {
    return TestUtils.randomApplicableCandidate(candidateRepository, periodRepository);
  }

  private Candidate candidateWithFinalizedApproval(String newAssignee) {
    var candidate = setupValidCandidate(topLevelOrganizationId);
    candidate.updateApprovalAssignee(
        new UpdateAssigneeRequest(topLevelOrganizationId, newAssignee));
    candidate.updateApprovalStatus(
        new UpdateStatusRequest(
            topLevelOrganizationId, ApprovalStatus.APPROVED, randomString(), randomString()),
        mockOrganizationRetriever);
    return candidate;
  }

  private InputStream createRequest(Candidate candidate, String newAssignee)
      throws JsonProcessingException {
    var approvalToUpdate = candidate.toDto().approvals().getFirst();
    var requestBody = new UpsertAssigneeRequest(newAssignee, approvalToUpdate.institutionId());
    return new HandlerRequestBuilder<UpsertAssigneeRequest>(JsonUtils.dtoObjectMapper)
        .withBody(randomAssigneeRequest())
        .withTopLevelCristinOrgId(requestBody.institutionId())
        .withAccessRights(requestBody.institutionId(), AccessRight.MANAGE_NVI_CANDIDATES)
        .withUserName(randomString())
        .withBody(requestBody)
        .withPathParameters(Map.of(resourcePathParameter, candidate.getIdentifier().toString()))
        .build();
  }

  private InputStream createRequestWithNonExistingCandidate(String assignee)
      throws JsonProcessingException {
    var organizationId = randomUri();
    var requestBody = new UpsertAssigneeRequest(assignee, organizationId);
    return new HandlerRequestBuilder<UpsertAssigneeRequest>(JsonUtils.dtoObjectMapper)
        .withBody(randomAssigneeRequest())
        .withTopLevelCristinOrgId(organizationId)
        .withAccessRights(organizationId, AccessRight.MANAGE_NVI_CANDIDATES)
        .withUserName(randomString())
        .withBody(requestBody)
        .withPathParameters(Map.of(resourcePathParameter, randomUUID().toString()))
        .build();
  }

  private InputStream createRequestWithoutAccessRights() throws JsonProcessingException {
    return new HandlerRequestBuilder<UpsertAssigneeRequest>(JsonUtils.dtoObjectMapper)
        .withBody(randomAssigneeRequest())
        .build();
  }

  private InputStream createRequestWithDifferentInstitution() throws JsonProcessingException {
    return new HandlerRequestBuilder<UpsertAssigneeRequest>(JsonUtils.dtoObjectMapper)
        .withBody(randomAssigneeRequest())
        .withTopLevelCristinOrgId(randomUri())
        .build();
  }

  private UpsertAssigneeRequest randomAssigneeRequest() {
    return new UpsertAssigneeRequest(randomString(), randomUri());
  }

  private void mockUserIdentity(String userName, List<String> accessRights) {
    var user =
        GetUserResponse.builder().withUsername(userName).withAccessRights(accessRights).build();
    try {
      when(mockIdentityServiceClient.getUser(userName)).thenReturn(user);
    } catch (NotFoundException e) {
      throw new RuntimeException(e);
    }
  }

  private void mockNviCuratorAccessForUser(String userName) {
    mockUserIdentity(userName, List.of(AccessRight.MANAGE_NVI_CANDIDATES.toString()));
  }

  private void mockNoAccessForUser(String userName) {
    mockUserIdentity(userName, emptyList());
  }
}
