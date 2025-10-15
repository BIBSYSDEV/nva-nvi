package no.sikt.nva.nvi.rest.create;

import static no.sikt.nva.nvi.common.db.PeriodRepositoryFixtures.setupClosedPeriod;
import static no.sikt.nva.nvi.common.dto.AllowedOperationFixtures.CURATOR_CAN_FINALIZE_APPROVAL;
import static no.sikt.nva.nvi.test.TestUtils.CURRENT_YEAR;
import static no.unit.nva.testutils.RandomDataGenerator.randomString;
import static no.unit.nva.testutils.RandomDataGenerator.randomUri;
import static org.hamcrest.CoreMatchers.equalTo;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.containsInAnyOrder;
import static org.hamcrest.Matchers.is;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;

import com.fasterxml.jackson.core.JsonProcessingException;
import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URI;
import java.util.Map;
import java.util.UUID;
import no.sikt.nva.nvi.common.FakeEnvironment;
import no.sikt.nva.nvi.common.model.UpdateAssigneeRequest;
import no.sikt.nva.nvi.common.service.dto.ApprovalDto;
import no.sikt.nva.nvi.common.service.dto.CandidateDto;
import no.sikt.nva.nvi.common.validator.FakeViewingScopeValidator;
import no.sikt.nva.nvi.rest.BaseCandidateRestHandlerTest;
import no.sikt.nva.nvi.rest.EnvironmentFixtures;
import no.unit.nva.commons.json.JsonUtils;
import no.unit.nva.testutils.HandlerRequestBuilder;
import nva.commons.apigateway.AccessRight;
import nva.commons.apigateway.ApiGatewayHandler;
import nva.commons.apigateway.GatewayResponse;
import org.hamcrest.Matchers;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.zalando.problem.Problem;

class CreateNoteHandlerTest extends BaseCandidateRestHandlerTest {

  @Override
  protected ApiGatewayHandler<NviNoteRequest, CandidateDto> createHandler() {
    return new CreateNoteHandler(
        candidateService, noteService, mockViewingScopeValidator, environment);
  }

  @Override
  protected FakeEnvironment getHandlerEnvironment() {
    return EnvironmentFixtures.CREATE_NOTE_HANDLER;
  }

  @BeforeEach
  void setUp() {
    resourcePathParameter = "candidateIdentifier";
  }

  @Test
  void shouldReturnUnauthorizedWhenMissingAccessRights() throws IOException {
    handler.handleRequest(
        requestWithoutAccessRights(UUID.randomUUID(), randomNote()), output, CONTEXT);
    var response = GatewayResponse.fromOutputStream(output, Problem.class);

    assertThat(response.getStatusCode(), is(equalTo(HttpURLConnection.HTTP_UNAUTHORIZED)));
  }

  @Test
  void shouldReturnUnauthorizedWhenCandidateIsNotInUsersViewingScope() throws IOException {
    var candidate = setupValidCandidate();
    var request =
        createRequest(candidate.identifier(), new NviNoteRequest("The note"), randomString());
    var viewingScopeValidatorReturningFalse = new FakeViewingScopeValidator(false);
    handler =
        new CreateNoteHandler(
            candidateService, noteService, viewingScopeValidatorReturningFalse, environment);
    handler.handleRequest(request, output, CONTEXT);
    var response = GatewayResponse.fromOutputStream(output, Problem.class);
    assertThat(response.getStatusCode(), is(equalTo(HttpURLConnection.HTTP_UNAUTHORIZED)));
  }

  @Test
  void shouldReturnBadRequestIfCreateNoteRequestIsInvalid() throws IOException {
    var invalidRequestBody =
        JsonUtils.dtoObjectMapper.writeValueAsString(
            Map.of("someInvalidInputField", randomString()));
    var request = createRequest(UUID.randomUUID(), invalidRequestBody, randomString());

    handler.handleRequest(request, output, CONTEXT);
    var response = GatewayResponse.fromOutputStream(output, Problem.class);

    assertThat(response.getStatusCode(), is(equalTo(HttpURLConnection.HTTP_BAD_REQUEST)));
  }

  @Test
  void shouldAddNoteToCandidateWhenNoteIsValid() throws IOException {
    var candidate = setupValidCandidate();
    var theNote = "The note";
    var userName = randomString();

    var request = createRequest(candidate.identifier(), new NviNoteRequest(theNote), userName);
    handler.handleRequest(request, output, CONTEXT);
    var gatewayResponse = GatewayResponse.fromOutputStream(output, CandidateDto.class);
    var actualNote = gatewayResponse.getBodyObject(CandidateDto.class).notes().getFirst();

    assertThat(actualNote.text(), is(equalTo(theNote)));
    assertThat(actualNote.user(), is(equalTo(userName)));
  }

  @Test
  void shouldReturnConflictWhenCreatingNoteAndReportingPeriodIsClosed() throws IOException {
    var candidate = setupValidCandidate();
    var request =
        createRequest(candidate.identifier(), new NviNoteRequest(randomString()), randomString());
    setupClosedPeriod(scenario, CURRENT_YEAR);
    handler.handleRequest(request, output, CONTEXT);
    var response = GatewayResponse.fromOutputStream(output, Problem.class);

    assertThat(response.getStatusCode(), is(Matchers.equalTo(HttpURLConnection.HTTP_CONFLICT)));
  }

  @Test
  void shouldReturn405NotAllowedWhenAddingNoteToNonCandidate() throws IOException {
    var nonCandidate = setupNonApplicableCandidate(topLevelOrganizationId);
    var request =
        createRequest(nonCandidate.identifier(), randomNote().toJsonString(), randomString());
    handler.handleRequest(request, output, CONTEXT);
    var response = GatewayResponse.fromOutputStream(output, Problem.class);

    assertThat(response.getStatusCode(), is(Matchers.equalTo(HttpURLConnection.HTTP_BAD_METHOD)));
  }

  @Test
  void shouldSetUserAsAssigneeWhenUsersInstitutionApprovalIsUnassigned() throws IOException {
    var candidate = setupValidCandidate();
    assertNull(candidate.getApprovals().get(topLevelOrganizationId).getAssigneeUsername());
    var userName = randomString();
    var request =
        createRequest(candidate.identifier(), randomNote(), userName, topLevelOrganizationId);
    handler.handleRequest(request, output, CONTEXT);
    var response =
        GatewayResponse.fromOutputStream(output, CandidateDto.class)
            .getBodyObject(CandidateDto.class);
    var actualAssignee = getActualAssignee(response, topLevelOrganizationId);
    assertEquals(userName, actualAssignee);
  }

  @Test
  void shouldNotSetUserAsAssigneeWhenUsersInstitutionApprovalHasAssignee() throws IOException {
    var candidate = setupValidCandidate();
    var existingApprovalAssignee = randomString();
    var updateRequest = new UpdateAssigneeRequest(topLevelOrganizationId, existingApprovalAssignee);
    approvalService.updateApproval(candidate, updateRequest, curatorUser);
    var updatedCandidate = candidateService.getByIdentifier(candidate.identifier());
    assertNotNull(
        updatedCandidate.getApprovals().get(topLevelOrganizationId).getAssigneeUsername());

    var request =
        createRequest(candidate.identifier(), randomNote(), randomString(), topLevelOrganizationId);
    handler.handleRequest(request, output, CONTEXT);
    var response =
        GatewayResponse.fromOutputStream(output, CandidateDto.class)
            .getBodyObject(CandidateDto.class);
    var actualAssignee = getActualAssignee(response, topLevelOrganizationId);
    assertEquals(existingApprovalAssignee, actualAssignee);
  }

  @Test
  void shouldIncludeAllowedOperations() throws IOException {
    var candidate = setupValidCandidate();

    var request =
        createRequest(candidate.identifier(), new NviNoteRequest(randomString()), randomString());
    var candidateDto = handleRequest(request);

    var actualAllowedOperations = candidateDto.allowedOperations();
    assertThat(
        actualAllowedOperations, containsInAnyOrder(CURATOR_CAN_FINALIZE_APPROVAL.toArray()));
  }

  private static String getActualAssignee(CandidateDto response, URI institutionId) {
    return response.approvals().stream()
        .filter(approval -> isInstitutionId(institutionId, approval))
        .findFirst()
        .orElseThrow()
        .assignee();
  }

  private static boolean isInstitutionId(URI institutionId, ApprovalDto approval) {
    return approval.institutionId().equals(institutionId);
  }

  private InputStream createRequest(UUID identifier, NviNoteRequest body, String userName)
      throws JsonProcessingException {
    return createRequest(identifier, body, userName, topLevelOrganizationId);
  }

  private InputStream createRequest(
      UUID identifier, NviNoteRequest body, String userName, URI topLevelOrganizationId)
      throws JsonProcessingException {
    return new HandlerRequestBuilder<NviNoteRequest>(JsonUtils.dtoObjectMapper)
        .withBody(body)
        .withTopLevelCristinOrgId(topLevelOrganizationId)
        .withPathParameters(Map.of(resourcePathParameter, identifier.toString()))
        .withAccessRights(topLevelOrganizationId, AccessRight.MANAGE_NVI_CANDIDATES)
        .withUserName(userName)
        .build();
  }

  private InputStream createRequest(UUID identifier, String body, String userName)
      throws JsonProcessingException {
    var topLevelOrganizationId = randomUri();
    return new HandlerRequestBuilder<String>(JsonUtils.dtoObjectMapper)
        .withBody(body)
        .withTopLevelCristinOrgId(topLevelOrganizationId)
        .withPathParameters(Map.of(resourcePathParameter, identifier.toString()))
        .withAccessRights(topLevelOrganizationId, AccessRight.MANAGE_NVI_CANDIDATES)
        .withUserName(userName)
        .build();
  }

  private NviNoteRequest randomNote() {
    return new NviNoteRequest(randomString());
  }

  private InputStream requestWithoutAccessRights(UUID candidateIdentifier, NviNoteRequest request)
      throws JsonProcessingException {
    return new HandlerRequestBuilder<NviNoteRequest>(JsonUtils.dtoObjectMapper)
        .withPathParameters(Map.of(resourcePathParameter, candidateIdentifier.toString()))
        .withBody(request)
        .build();
  }
}
