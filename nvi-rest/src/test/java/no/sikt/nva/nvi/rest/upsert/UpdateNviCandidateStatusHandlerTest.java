package no.sikt.nva.nvi.rest.upsert;

import static no.sikt.nva.nvi.common.db.PeriodRepositoryFixtures.setupClosedPeriod;
import static no.sikt.nva.nvi.common.db.PeriodRepositoryFixtures.setupFuturePeriod;
import static no.sikt.nva.nvi.common.dto.AllowedOperationFixtures.CURATOR_CAN_FINALIZE_APPROVAL;
import static no.sikt.nva.nvi.test.TestUtils.CURRENT_YEAR;
import static no.unit.nva.testutils.RandomDataGenerator.randomString;
import static no.unit.nva.testutils.RandomDataGenerator.randomUri;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.containsInAnyOrder;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.nullValue;
import static org.junit.jupiter.api.Assertions.assertEquals;

import com.fasterxml.jackson.core.JsonProcessingException;
import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URI;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Stream;
import no.sikt.nva.nvi.common.FakeEnvironment;
import no.sikt.nva.nvi.common.exceptions.TransactionException;
import no.sikt.nva.nvi.common.service.ApprovalServiceThrowingTransactionExceptions;
import no.sikt.nva.nvi.common.service.dto.ApprovalStatusDto;
import no.sikt.nva.nvi.common.service.dto.CandidateDto;
import no.sikt.nva.nvi.common.service.model.ApprovalStatus;
import no.sikt.nva.nvi.common.validator.FakeViewingScopeValidator;
import no.sikt.nva.nvi.rest.BaseCandidateRestHandlerTest;
import no.sikt.nva.nvi.rest.EnvironmentFixtures;
import no.unit.nva.commons.json.JsonUtils;
import no.unit.nva.testutils.HandlerRequestBuilder;
import nva.commons.apigateway.AccessRight;
import nva.commons.apigateway.ApiGatewayHandler;
import nva.commons.apigateway.GatewayResponse;
import org.assertj.core.api.Assertions;
import org.hamcrest.CoreMatchers;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.MethodSource;
import org.zalando.problem.Problem;

class UpdateNviCandidateStatusHandlerTest extends BaseCandidateRestHandlerTest {

  private static final String ERROR_MISSING_REJECTION_REASON =
      "Cannot reject approval status without reason";
  private static final String CANDIDATE_IDENTIFIER_PATH = "candidateIdentifier";
  private static final String STATUS_APPROVED = "APPROVED";
  private static final String STATUS_PENDING = "PENDING";
  private static final String STATUS_REJECTED = "REJECTED";

  public static Stream<Arguments> approvalStatusProvider() {
    return Stream.of(
        Arguments.of(ApprovalStatus.PENDING, ApprovalStatus.APPROVED),
        Arguments.of(ApprovalStatus.PENDING, ApprovalStatus.REJECTED),
        Arguments.of(ApprovalStatus.APPROVED, ApprovalStatus.PENDING),
        Arguments.of(ApprovalStatus.APPROVED, ApprovalStatus.REJECTED),
        Arguments.of(ApprovalStatus.REJECTED, ApprovalStatus.APPROVED),
        Arguments.of(ApprovalStatus.REJECTED, ApprovalStatus.PENDING));
  }

  @Override
  protected ApiGatewayHandler<NviStatusRequest, CandidateDto> createHandler() {
    return new UpdateNviCandidateStatusHandler(
        candidateService, approvalService, mockViewingScopeValidator, environment);
  }

  @Override
  protected FakeEnvironment getHandlerEnvironment() {
    return EnvironmentFixtures.UPDATE_NVI_CANDIDATE_STATUS_HANDLER;
  }

  @BeforeEach
  void setUp() {
    resourcePathParameter = "candidateIdentifier";
  }

  @Test
  void shouldReturnUnauthorizedWhenMissingAccessRights() throws IOException {
    handler.handleRequest(createRequestWithoutAccessRights(randomStatusRequest()), output, CONTEXT);
    var response = GatewayResponse.fromOutputStream(output, Problem.class);

    assertThat(
        response.getStatusCode(), is(CoreMatchers.equalTo(HttpURLConnection.HTTP_UNAUTHORIZED)));
  }

  private InputStream createRequestWithoutAccessRights(NviStatusRequest body)
      throws JsonProcessingException {
    return new HandlerRequestBuilder<NviStatusRequest>(JsonUtils.dtoObjectMapper)
        .withBody(body)
        .build();
  }

  private NviStatusRequest randomStatusRequest() {
    return new NviStatusRequest(randomUri(), ApprovalStatus.APPROVED, null);
  }

  @Test
  void shouldReturnUnauthorizedWhenCandidateIsNotInUsersViewingScope() throws IOException {
    var candidate = setupValidCandidate();
    var request =
        createRequest(candidate.identifier(), topLevelOrganizationId, ApprovalStatus.APPROVED);
    handler =
        new UpdateNviCandidateStatusHandler(
            scenario.getCandidateService(),
            approvalService,
            new FakeViewingScopeValidator(false),
            environment);
    handler.handleRequest(request, output, CONTEXT);
    var response = GatewayResponse.fromOutputStream(output, Problem.class);
    assertThat(
        response.getStatusCode(), is(CoreMatchers.equalTo(HttpURLConnection.HTTP_UNAUTHORIZED)));
  }

  private InputStream createRequest(
      UUID candidateIdentifier, URI institutionId, ApprovalStatus status)
      throws JsonProcessingException {
    var reason = status == ApprovalStatus.REJECTED ? randomString() : null;
    var requestBody = new NviStatusRequest(institutionId, status, reason);
    return createRequest(candidateIdentifier, institutionId, requestBody);
  }

  private static InputStream createRequest(
      UUID candidateIdentifier, URI organizationId, NviStatusRequest requestBody)
      throws JsonProcessingException {
    return new HandlerRequestBuilder<NviStatusRequest>(JsonUtils.dtoObjectMapper)
        .withPathParameters(Map.of(CANDIDATE_IDENTIFIER_PATH, candidateIdentifier.toString()))
        .withBody(requestBody)
        .withTopLevelCristinOrgId(organizationId)
        .withAccessRights(organizationId, AccessRight.MANAGE_NVI_CANDIDATES)
        .withUserName(randomString())
        .build();
  }

  @Test
  void shouldBeForbiddenToChangeStatusOfOtherInstitution() throws IOException {
    var otherInstitutionId = randomUri();
    var candidate = setupValidCandidate();
    var requestBody = new NviStatusRequest(topLevelOrganizationId, ApprovalStatus.PENDING, null);
    var request = createRequest(candidate.identifier(), otherInstitutionId, requestBody);
    handler.handleRequest(request, output, CONTEXT);
    var response = GatewayResponse.fromOutputStream(output, Problem.class);

    assertThat(response.getStatusCode(), is(equalTo(HttpURLConnection.HTTP_FORBIDDEN)));
  }

  @Test
  void shouldReturnConflictWhenUpdatingStatusAndReportingPeriodIsClosed() throws IOException {
    var candidate = setupValidCandidate();
    setupClosedPeriod(scenario, CURRENT_YEAR);
    var request =
        createRequest(candidate.identifier(), topLevelOrganizationId, ApprovalStatus.APPROVED);
    handler.handleRequest(request, output, CONTEXT);
    var response = GatewayResponse.fromOutputStream(output, Problem.class);

    assertThat(response.getStatusCode(), is(equalTo(HttpURLConnection.HTTP_CONFLICT)));
  }

  @Test
  void shouldReturnConflictWhenApprovingCandidateWithUnverifiedCreators() throws IOException {
    var candidate = setupCandidateWithUnverifiedCreator();
    var request =
        createRequest(candidate.identifier(), topLevelOrganizationId, ApprovalStatus.APPROVED);
    handler.handleRequest(request, output, CONTEXT);
    var response = GatewayResponse.fromOutputStream(output, Problem.class);

    assertThat(response.getStatusCode(), is(equalTo(HttpURLConnection.HTTP_CONFLICT)));
  }

  @Test
  void shouldAllowRejectingCandidateWithUnverifiedCreators() throws IOException {
    var candidate = setupCandidateWithUnverifiedCreator();
    var request =
        createRequest(candidate.identifier(), topLevelOrganizationId, ApprovalStatus.REJECTED);
    handler.handleRequest(request, output, CONTEXT);
    var response = GatewayResponse.fromOutputStream(output, CandidateDto.class);

    assertThat(response.getStatusCode(), is(equalTo(HttpURLConnection.HTTP_OK)));
  }

  @Test
  void shouldReturnConflictWhenUpdatingStatusAndNotOpenedPeriod() throws IOException {
    var candidate = setupValidCandidate();
    setupFuturePeriod(scenario, CURRENT_YEAR);
    var request =
        createRequest(candidate.identifier(), topLevelOrganizationId, ApprovalStatus.APPROVED);
    handler.handleRequest(request, output, CONTEXT);
    var response = GatewayResponse.fromOutputStream(output, Problem.class);

    assertThat(response.getStatusCode(), is(equalTo(HttpURLConnection.HTTP_CONFLICT)));
  }

  @Test
  void shouldReturnConflictErrorWhenTransactionFailsDueToConflict() throws IOException {
    var candidate = setupValidCandidate();
    var request =
        createRequest(candidate.identifier(), topLevelOrganizationId, ApprovalStatus.APPROVED);

    var failingHandler = setupHandlerThatFailsWithTransactionConflict();
    var response = handleRequestExpectingProblem(failingHandler, request);

    Assertions.assertThat(response.getStatus().getStatusCode())
        .isEqualTo(HttpURLConnection.HTTP_CONFLICT);
    Assertions.assertThat(response.getDetail()).isEqualTo(TransactionException.USER_MESSAGE);
  }

  private UpdateNviCandidateStatusHandler setupHandlerThatFailsWithTransactionConflict() {
    return new UpdateNviCandidateStatusHandler(
        candidateService,
        new ApprovalServiceThrowingTransactionExceptions(scenario.getCandidateRepository()),
        mockViewingScopeValidator,
        environment);
  }

  @Test
  void shouldReturnBadRequestIfRejectionDoesNotContainReason() throws IOException {
    var candidate = setupValidCandidate();
    var request = createRequestWithoutReason(candidate.identifier(), topLevelOrganizationId);
    handler.handleRequest(request, output, CONTEXT);
    var response = GatewayResponse.fromOutputStream(output, Problem.class);

    assertEquals(HttpURLConnection.HTTP_BAD_REQUEST, response.getStatusCode());
    assertThat(response.getBody(), containsString(ERROR_MISSING_REJECTION_REASON));
  }

  private InputStream createRequestWithoutReason(UUID candidateIdentifier, URI institutionId)
      throws JsonProcessingException {
    var requestBody = new NviStatusRequest(institutionId, ApprovalStatus.REJECTED, null);
    return createRequest(candidateIdentifier, institutionId, requestBody);
  }

  @ParameterizedTest(name = "Should update from old status {0} to new status {1}")
  @MethodSource("approvalStatusProvider")
  void shouldUpdateApprovalStatus(ApprovalStatus oldStatus, ApprovalStatus newStatus)
      throws IOException {
    var candidate = setupValidCandidate();
    scenario.updateApprovalStatus(candidate.identifier(), oldStatus, topLevelOrganizationId);
    var request = createRequest(candidate.identifier(), topLevelOrganizationId, newStatus);
    handler.handleRequest(request, output, CONTEXT);
    var response = GatewayResponse.fromOutputStream(output, CandidateDto.class);
    var candidateResponse = response.getBodyObject(CandidateDto.class);
    var actualApproval = candidateResponse.approvals().getFirst();
    var expectedStatus = getExpectedApprovalStatus(newStatus);
    assertThat(actualApproval.status(), is(equalTo(expectedStatus)));
  }

  private ApprovalStatusDto getExpectedApprovalStatus(ApprovalStatus status) {
    return switch (status) {
      case PENDING -> ApprovalStatusDto.PENDING;
      case APPROVED -> ApprovalStatusDto.APPROVED;
      case REJECTED -> ApprovalStatusDto.REJECTED;
      default -> throw new IllegalArgumentException("Unexpected value: " + status);
    };
  }

  @ParameterizedTest(
      name = "shouldResetFinalizedValuesWhenUpdatingStatusToPending from old status {0}")
  @EnumSource(
      value = ApprovalStatus.class,
      names = {STATUS_REJECTED, STATUS_APPROVED})
  void shouldResetFinalizedValuesWhenUpdatingStatusToPending(ApprovalStatus oldStatus)
      throws IOException {
    var candidate = setupValidCandidate();
    scenario.updateApprovalStatus(candidate.identifier(), oldStatus, topLevelOrganizationId);
    var newStatus = ApprovalStatus.PENDING;
    var request = createRequest(candidate.identifier(), topLevelOrganizationId, newStatus);
    handler.handleRequest(request, output, CONTEXT);
    var response = GatewayResponse.fromOutputStream(output, CandidateDto.class);
    var candidateResponse = response.getBodyObject(CandidateDto.class);
    var actualApproval = candidateResponse.approvals().getFirst();
    assertThat(actualApproval.finalizedBy(), is(nullValue()));
    assertThat(actualApproval.finalizedDate(), is(nullValue()));
    assertThat(actualApproval.status(), is(equalTo(ApprovalStatusDto.PENDING)));
  }

  @ParameterizedTest(name = "shouldUpdateApprovalStatusToRejectedWithReason from old status {0}")
  @EnumSource(
      value = ApprovalStatus.class,
      names = {STATUS_PENDING, STATUS_APPROVED})
  void shouldUpdateApprovalStatusToRejectedWithReason(ApprovalStatus oldStatus) throws IOException {
    var candidate = setupValidCandidate();
    scenario.updateApprovalStatus(candidate.identifier(), oldStatus, topLevelOrganizationId);
    var rejectionReason = randomString();
    var requestBody =
        new NviStatusRequest(topLevelOrganizationId, ApprovalStatus.REJECTED, rejectionReason);
    var request =
        createRequest(candidate.identifier(), topLevelOrganizationId, requestBody, randomString());
    handler.handleRequest(request, output, CONTEXT);
    var response = GatewayResponse.fromOutputStream(output, CandidateDto.class);
    var candidateResponse = response.getBodyObject(CandidateDto.class);
    var actualApprovalStatus = candidateResponse.approvals().getFirst();

    assertThat(actualApprovalStatus.status(), is(equalTo(ApprovalStatusDto.REJECTED)));
    assertThat(actualApprovalStatus.reason(), is(equalTo(rejectionReason)));
  }

  private static InputStream createRequest(
      UUID candidateIdentifier, URI organizationId, NviStatusRequest requestBody, String username)
      throws JsonProcessingException {
    return new HandlerRequestBuilder<NviStatusRequest>(JsonUtils.dtoObjectMapper)
        .withPathParameters(Map.of(CANDIDATE_IDENTIFIER_PATH, candidateIdentifier.toString()))
        .withBody(requestBody)
        .withTopLevelCristinOrgId(organizationId)
        .withAccessRights(organizationId, AccessRight.MANAGE_NVI_CANDIDATES)
        .withUserName(username)
        .build();
  }

  @ParameterizedTest(name = "shouldRemoveReasonWhenUpdatingStatusFromRejected to {0}")
  @EnumSource(
      value = ApprovalStatus.class,
      names = {STATUS_PENDING, STATUS_APPROVED})
  void shouldRemoveReasonWhenUpdatingStatusFromRejected(ApprovalStatus newStatus)
      throws IOException {
    var candidate = setupValidCandidate();
    scenario.updateApprovalStatus(
        candidate.identifier(), ApprovalStatus.REJECTED, topLevelOrganizationId);
    var request =
        createRequest(
            candidate.identifier(),
            topLevelOrganizationId,
            ApprovalStatus.parse(newStatus.getValue()));
    handler.handleRequest(request, output, CONTEXT);
    var response = GatewayResponse.fromOutputStream(output, CandidateDto.class);
    var candidateResponse = response.getBodyObject(CandidateDto.class);
    var actualApproval = candidateResponse.approvals().getFirst();
    var expectedStatus = getExpectedApprovalStatus(newStatus);
    assertThat(actualApproval.status(), is(equalTo(expectedStatus)));
    assertThat(actualApproval.reason(), is(nullValue()));
  }

  @Test
  void shouldUpdateAssigneeWhenFinalizingApprovalWithoutAssignee() throws IOException {
    var candidate = setupValidCandidate();
    var assignee = randomString();
    var requestBody = new NviStatusRequest(topLevelOrganizationId, ApprovalStatus.APPROVED, null);
    var request =
        createRequest(candidate.identifier(), topLevelOrganizationId, requestBody, assignee);
    handler.handleRequest(request, output, CONTEXT);
    var response = GatewayResponse.fromOutputStream(output, CandidateDto.class);
    var candidateResponse = response.getBodyObject(CandidateDto.class);

    assertThat(candidateResponse.approvals().getFirst().assignee(), is(equalTo(assignee)));
  }

  @Test
  void shouldIncludeAllowedOperations() throws IOException {
    var candidate = setupValidCandidate();
    var assignee = randomString();
    var requestBody = new NviStatusRequest(topLevelOrganizationId, ApprovalStatus.PENDING, null);
    var request =
        createRequest(candidate.identifier(), topLevelOrganizationId, requestBody, assignee);

    var candidateDto = handleRequest(request);

    var actualAllowedOperations = candidateDto.allowedOperations();
    assertThat(
        actualAllowedOperations, containsInAnyOrder(CURATOR_CAN_FINALIZE_APPROVAL.toArray()));
  }
}
