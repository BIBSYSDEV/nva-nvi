package no.sikt.nva.nvi.rest.upsert;

import static java.util.Collections.emptyList;
import static java.util.UUID.randomUUID;
import static no.sikt.nva.nvi.common.db.PeriodRepositoryFixtures.setupClosedPeriod;
import static no.sikt.nva.nvi.common.db.PeriodRepositoryFixtures.setupFuturePeriod;
import static no.sikt.nva.nvi.common.dto.AllowedOperationFixtures.CURATOR_CAN_FINALIZE_APPROVAL;
import static no.sikt.nva.nvi.test.TestUtils.CURRENT_YEAR;
import static no.unit.nva.testutils.RandomDataGenerator.randomString;
import static no.unit.nva.testutils.RandomDataGenerator.randomUri;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.containsInAnyOrder;
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
import java.util.UUID;
import no.sikt.nva.nvi.common.FakeEnvironment;
import no.sikt.nva.nvi.common.model.UpdateAssigneeRequest;
import no.sikt.nva.nvi.common.service.dto.CandidateDto;
import no.sikt.nva.nvi.common.service.model.ApprovalStatus;
import no.sikt.nva.nvi.common.service.model.Candidate;
import no.sikt.nva.nvi.common.validator.FakeViewingScopeValidator;
import no.sikt.nva.nvi.rest.BaseCandidateRestHandlerTest;
import no.sikt.nva.nvi.rest.EnvironmentFixtures;
import no.sikt.nva.nvi.rest.model.UpsertAssigneeRequest;
import no.unit.nva.clients.IdentityServiceClient;
import no.unit.nva.clients.UserDto;
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
  private UUID candidateIdentifier;

  private static final IdentityServiceClient mockIdentityServiceClient =
      mock(IdentityServiceClient.class);

  @Override
  protected ApiGatewayHandler<UpsertAssigneeRequest, CandidateDto> createHandler() {
    return new UpsertAssigneeHandler(
        candidateService,
        approvalService,
        mockIdentityServiceClient,
        mockViewingScopeValidator,
        environment);
  }

  @Override
  protected FakeEnvironment getHandlerEnvironment() {
    return EnvironmentFixtures.UPSERT_ASSIGNEE_HANDLER;
  }

  @BeforeEach
  void setUp() {
    resourcePathParameter = "candidateIdentifier";
    var candidate = setupValidCandidate();
    candidateIdentifier = candidate.identifier();
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
    var assignee = randomString();
    mockNoAccessForUser(assignee);
    handler.handleRequest(createRequest(candidateIdentifier, assignee), output, CONTEXT);
    var response = GatewayResponse.fromOutputStream(output, CandidateDto.class);

    assertThat(response.getStatusCode(), is(equalTo(HttpURLConnection.HTTP_UNAUTHORIZED)));
  }

  @Test
  void shouldReturnUnauthorizedWhenCandidateIsNotInUsersViewingScope() throws IOException {
    var assignee = randomString();
    var viewingScopeValidatorReturningFalse = new FakeViewingScopeValidator(false);
    handler =
        new UpsertAssigneeHandler(
            candidateService,
            approvalService,
            mockIdentityServiceClient,
            viewingScopeValidatorReturningFalse,
            environment);
    handler.handleRequest(createRequest(candidateIdentifier, assignee), output, CONTEXT);
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
    var assignee = randomString();
    mockNviCuratorAccessForUser(assignee);
    setupClosedPeriod(scenario, CURRENT_YEAR);
    handler.handleRequest(createRequest(candidateIdentifier, assignee), output, CONTEXT);
    var response = GatewayResponse.fromOutputStream(output, Problem.class);

    assertThat(response.getStatusCode(), is(equalTo(HttpURLConnection.HTTP_CONFLICT)));
  }

  @Test
  void shouldReturnConflictWhenUpdatingAssigneeAndReportingPeriodIsNotOpenedYet()
      throws IOException {
    var assignee = randomString();
    mockNviCuratorAccessForUser(assignee);
    setupFuturePeriod(scenario, CURRENT_YEAR);
    handler.handleRequest(createRequest(candidateIdentifier, assignee), output, CONTEXT);
    var response = GatewayResponse.fromOutputStream(output, Problem.class);

    assertThat(response.getStatusCode(), is(equalTo(HttpURLConnection.HTTP_CONFLICT)));
  }

  @Test
  void shouldUpdateAssigneeWhenAssigneeIsNotPresent() throws IOException {
    var assignee = randomString();
    mockNviCuratorAccessForUser(assignee);
    handler.handleRequest(createRequest(candidateIdentifier, assignee), output, CONTEXT);
    var response = GatewayResponse.fromOutputStream(output, CandidateDto.class);

    assertThat(
        response.getBodyObject(CandidateDto.class).approvals().getFirst().assignee(),
        is(equalTo(assignee)));
    assertThat(response.getStatusCode(), is(equalTo(HttpURLConnection.HTTP_OK)));
  }

  @Test
  void shouldRemoveAssigneeWhenAssigneeIsPresent() throws IOException {
    handler.handleRequest(createRequest(candidateIdentifier, null), output, CONTEXT);
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
    var newCandidate = candidateWithFinalizedApproval(newAssignee);
    handler.handleRequest(createRequest(newCandidate.identifier(), newAssignee), output, CONTEXT);
    var response = GatewayResponse.fromOutputStream(output, CandidateDto.class);

    assertThat(
        response.getBodyObject(CandidateDto.class).approvals().getFirst().assignee(),
        is(equalTo(newAssignee)));
  }

  @Test
  void shouldIncludeAllowedOperations() throws IOException {
    var assignee = randomString();
    mockNviCuratorAccessForUser(assignee);
    var request = createRequest(candidateIdentifier, assignee);
    var candidateDto = handleRequest(request);

    var actualAllowedOperations = candidateDto.allowedOperations();
    assertThat(
        actualAllowedOperations, containsInAnyOrder(CURATOR_CAN_FINALIZE_APPROVAL.toArray()));
  }

  private Candidate candidateWithFinalizedApproval(String newAssignee) {
    var updateRequest = new UpdateAssigneeRequest(topLevelOrganizationId, newAssignee);
    var candidate = candidateService.getCandidateByIdentifier(candidateIdentifier);
    approvalService.updateApproval(candidate, updateRequest, curatorUser);
    return scenario.updateApprovalStatus(
        candidateIdentifier, ApprovalStatus.APPROVED, topLevelOrganizationId);
  }

  private InputStream createRequest(UUID candidateIdentifier, String newAssignee)
      throws JsonProcessingException {
    var requestBody = new UpsertAssigneeRequest(newAssignee, topLevelOrganizationId);
    return new HandlerRequestBuilder<UpsertAssigneeRequest>(JsonUtils.dtoObjectMapper)
        .withBody(randomAssigneeRequest())
        .withTopLevelCristinOrgId(requestBody.institutionId())
        .withAccessRights(requestBody.institutionId(), AccessRight.MANAGE_NVI_CANDIDATES)
        .withUserName(randomString())
        .withBody(requestBody)
        .withPathParameters(Map.of(resourcePathParameter, candidateIdentifier.toString()))
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
    var user = UserDto.builder().withUsername(userName).withAccessRights(accessRights).build();
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
