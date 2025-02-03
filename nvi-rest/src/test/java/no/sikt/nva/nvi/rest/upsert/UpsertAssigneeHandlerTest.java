package no.sikt.nva.nvi.rest.upsert;

import static java.util.UUID.randomUUID;
import static no.sikt.nva.nvi.test.TestUtils.CURRENT_YEAR;
import static no.sikt.nva.nvi.test.TestUtils.createUpsertCandidateRequest;
import static no.sikt.nva.nvi.test.TestUtils.mockOrganizationResponseForAffiliation;
import static no.sikt.nva.nvi.test.TestUtils.periodRepositoryReturningClosedPeriod;
import static no.sikt.nva.nvi.test.TestUtils.periodRepositoryReturningNotOpenedPeriod;
import static no.unit.nva.testutils.RandomDataGenerator.randomString;
import static no.unit.nva.testutils.RandomDataGenerator.randomUri;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.nullValue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.core.JsonProcessingException;
import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.nio.file.Path;
import java.util.Map;
import java.util.Optional;
import no.sikt.nva.nvi.common.client.OrganizationRetriever;
import no.sikt.nva.nvi.common.model.UpdateAssigneeRequest;
import no.sikt.nva.nvi.common.model.UpdateStatusRequest;
import no.sikt.nva.nvi.common.service.dto.CandidateDto;
import no.sikt.nva.nvi.common.service.model.ApprovalStatus;
import no.sikt.nva.nvi.common.service.model.Candidate;
import no.sikt.nva.nvi.rest.BaseCandidateRestHandlerTest;
import no.sikt.nva.nvi.rest.model.UpsertAssigneeRequest;
import no.sikt.nva.nvi.test.FakeViewingScopeValidator;
import no.sikt.nva.nvi.test.TestUtils;
import no.unit.nva.auth.uriretriever.UriRetriever;
import no.unit.nva.commons.json.JsonUtils;
import no.unit.nva.testutils.HandlerRequestBuilder;
import nva.commons.apigateway.AccessRight;
import nva.commons.apigateway.ApiGatewayHandler;
import nva.commons.apigateway.GatewayResponse;
import nva.commons.core.ioutils.IoUtils;
import org.hamcrest.CoreMatchers;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.zalando.problem.Problem;

class UpsertAssigneeHandlerTest extends BaseCandidateRestHandlerTest {

  private static final String USER_RESPONSE_BODY_WITHOUT_ACCESS_RIGHT_JSON =
      "userResponseBodyWithoutAccessRight" + ".json";
  private static final String USER_RESPONSE_BODY_WITH_ACCESS_RIGHT_JSON =
      "userResponseBodyWithAccessRight.json";

  @Override
  protected ApiGatewayHandler<UpsertAssigneeRequest, CandidateDto> createHandler() {
    return new UpsertAssigneeHandler(
        candidateRepository, periodRepository, mockUriRetriever, mockViewingScopeValidator);
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
    mockUserApiResponse(USER_RESPONSE_BODY_WITHOUT_ACCESS_RIGHT_JSON);
    var candidate = createCandidate();
    var assignee = randomString();
    handler.handleRequest(createRequest(candidate, assignee), output, CONTEXT);
    var response = GatewayResponse.fromOutputStream(output, CandidateDto.class);

    assertThat(response.getStatusCode(), is(equalTo(HttpURLConnection.HTTP_UNAUTHORIZED)));
  }

  @Test
  void shouldReturnUnauthorizedWhenCandidateIsNotInUsersViewingScope() throws IOException {
    mockUserApiResponse(USER_RESPONSE_BODY_WITHOUT_ACCESS_RIGHT_JSON);
    var candidate = createCandidate();
    var assignee = randomString();
    var viewingScopeValidatorReturningFalse = new FakeViewingScopeValidator(false);
    handler =
        new UpsertAssigneeHandler(
            candidateRepository,
            periodRepository,
            mockUriRetriever,
            viewingScopeValidatorReturningFalse);
    handler.handleRequest(createRequest(candidate, assignee), output, CONTEXT);
    var response = GatewayResponse.fromOutputStream(output, Problem.class);
    assertThat(
        response.getStatusCode(), is(CoreMatchers.equalTo(HttpURLConnection.HTTP_UNAUTHORIZED)));
  }

  @Test
  void shouldReturnNotFoundWhenCandidateDoesNotExist() throws IOException {
    mockUserApiResponse(USER_RESPONSE_BODY_WITH_ACCESS_RIGHT_JSON);
    handler.handleRequest(createRequestWithNonExistingCandidate(), output, CONTEXT);
    var response = GatewayResponse.fromOutputStream(output, Problem.class);

    assertThat(response.getStatusCode(), is(equalTo(HttpURLConnection.HTTP_NOT_FOUND)));
  }

  @Test
  void shouldReturnConflictWhenUpdatingAssigneeAndReportingPeriodIsClosed() throws IOException {
    mockUserApiResponse(USER_RESPONSE_BODY_WITH_ACCESS_RIGHT_JSON);
    var candidate = createCandidate();
    var assignee = randomString();
    var periodRepositoryForClosedPeriod = periodRepositoryReturningClosedPeriod(CURRENT_YEAR);
    var customHandler =
        new UpsertAssigneeHandler(
            candidateRepository,
            periodRepositoryForClosedPeriod,
            mockUriRetriever,
            mockViewingScopeValidator);
    customHandler.handleRequest(createRequest(candidate, assignee), output, CONTEXT);
    var response = GatewayResponse.fromOutputStream(output, Problem.class);

    assertThat(response.getStatusCode(), is(equalTo(HttpURLConnection.HTTP_CONFLICT)));
  }

  @Test
  void shouldReturnConflictWhenUpdatingAssigneeAndReportingPeriodIsNotOpenedYet()
      throws IOException {
    mockUserApiResponse(USER_RESPONSE_BODY_WITH_ACCESS_RIGHT_JSON);
    var candidate = createCandidate();
    var assignee = randomString();
    var periodRepositoryForNotYetOpenPeriod =
        periodRepositoryReturningNotOpenedPeriod(CURRENT_YEAR);
    var customHandler =
        new UpsertAssigneeHandler(
            candidateRepository,
            periodRepositoryForNotYetOpenPeriod,
            mockUriRetriever,
            mockViewingScopeValidator);
    customHandler.handleRequest(createRequest(candidate, assignee), output, CONTEXT);
    var response = GatewayResponse.fromOutputStream(output, Problem.class);

    assertThat(response.getStatusCode(), is(equalTo(HttpURLConnection.HTTP_CONFLICT)));
  }

  @Test
  void shouldUpdateAssigneeWhenAssigneeIsNotPresent() throws IOException {
    mockUserApiResponse(USER_RESPONSE_BODY_WITH_ACCESS_RIGHT_JSON);
    var candidate = createCandidate();
    var assignee = randomString();
    handler.handleRequest(createRequest(candidate, assignee), output, CONTEXT);
    var response = GatewayResponse.fromOutputStream(output, CandidateDto.class);

    assertThat(
        response.getBodyObject(CandidateDto.class).approvals().getFirst().assignee(),
        is(equalTo(assignee)));
    assertThat(response.getStatusCode(), is(equalTo(HttpURLConnection.HTTP_OK)));
  }

  @Test
  void shouldRemoveAssigneeWhenAssigneeIsPresent() throws IOException {
    mockUserApiResponse(USER_RESPONSE_BODY_WITH_ACCESS_RIGHT_JSON);
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
    mockUserApiResponse(USER_RESPONSE_BODY_WITH_ACCESS_RIGHT_JSON);
    var newAssignee = randomString();
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
    var institutionId = randomUri();
    var mockUriRetriever = mock(UriRetriever.class);
    var mockOrganizationRetriever = new OrganizationRetriever(mockUriRetriever);
    mockOrganizationResponseForAffiliation(institutionId, null, mockUriRetriever);
    var request = createUpsertCandidateRequest(institutionId).build();
    Candidate.upsert(request, candidateRepository, periodRepository);
    var candidate =
        Candidate.fetchByPublicationId(
            request::publicationId, candidateRepository, periodRepository);
    candidate.updateApprovalAssignee(new UpdateAssigneeRequest(institutionId, newAssignee));
    candidate.updateApprovalStatus(
        new UpdateStatusRequest(
            institutionId, ApprovalStatus.APPROVED, randomString(), randomString()),
        mockOrganizationRetriever);
    return candidate;
  }

  private void mockUserApiResponse(String responseFile) {
    when(mockUriRetriever.getRawContent(any(), any()))
        .thenReturn(Optional.ofNullable(IoUtils.stringFromResources(Path.of(responseFile))));
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

  private InputStream createRequestWithNonExistingCandidate() throws JsonProcessingException {
    var organizationId = randomUri();
    var requestBody = new UpsertAssigneeRequest(randomString(), organizationId);
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
}
