package no.sikt.nva.nvi.rest.remove;

import static no.sikt.nva.nvi.common.db.UsernameFixtures.randomUsername;
import static no.sikt.nva.nvi.rest.fetch.FetchNviCandidateHandler.CANDIDATE_IDENTIFIER;
import static no.sikt.nva.nvi.rest.remove.RemoveNoteHandler.PARAM_NOTE_IDENTIFIER;
import static no.sikt.nva.nvi.test.TestUtils.CURRENT_YEAR;
import static no.unit.nva.commons.json.JsonUtils.dtoObjectMapper;
import static no.unit.nva.testutils.RandomDataGenerator.randomString;
import static no.unit.nva.testutils.RandomDataGenerator.randomUri;
import static org.hamcrest.CoreMatchers.equalTo;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.containsInAnyOrder;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.is;

import java.io.IOException;
import java.net.HttpURLConnection;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import no.sikt.nva.nvi.common.db.model.Username;
import no.sikt.nva.nvi.common.model.CreateNoteRequest;
import no.sikt.nva.nvi.common.service.dto.CandidateDto;
import no.sikt.nva.nvi.common.service.dto.CandidateOperation;
import no.sikt.nva.nvi.common.service.model.Candidate;
import no.sikt.nva.nvi.common.validator.FakeViewingScopeValidator;
import no.sikt.nva.nvi.rest.BaseCandidateRestHandlerTest;
import no.sikt.nva.nvi.rest.create.NviNoteRequest;
import no.unit.nva.testutils.HandlerRequestBuilder;
import nva.commons.apigateway.AccessRight;
import nva.commons.apigateway.ApiGatewayHandler;
import nva.commons.apigateway.GatewayResponse;
import org.hamcrest.Matchers;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.zalando.problem.Problem;

class RemoveNoteHandlerTest extends BaseCandidateRestHandlerTest {
  private static HandlerRequestBuilder<NviNoteRequest> createRequestWithoutAccessRights(
      String candidateId, String noteId, String userName) {
    return new HandlerRequestBuilder<NviNoteRequest>(dtoObjectMapper)
        .withPathParameters(
            Map.of(CANDIDATE_IDENTIFIER, candidateId, PARAM_NOTE_IDENTIFIER, noteId))
        .withUserName(userName);
  }

  @Override
  protected ApiGatewayHandler<Void, CandidateDto> createHandler() {
    return new RemoveNoteHandler(
        scenario.getCandidateRepository(),
        scenario.getPeriodRepository(),
        mockViewingScopeValidator,
        mockOrganizationRetriever);
  }

  @BeforeEach
  void setUp() {
    resourcePathParameter = "candidateIdentifier";
  }

  @Test
  void shouldReturnUnauthorizedWhenMissingAccessRights() throws IOException {
    handler.handleRequest(
        createRequestWithoutAccessRights(randomString(), randomString(), randomString()).build(),
        output,
        CONTEXT);
    var response = GatewayResponse.fromOutputStream(output, Problem.class);

    assertThat(response.getStatusCode(), is(equalTo(HttpURLConnection.HTTP_UNAUTHORIZED)));
  }

  @Test
  void shouldReturnUnauthorizedWhenCandidateIsNotInUsersViewingScope() throws IOException {
    var candidate = setupValidCandidate(topLevelOrganizationId);
    var user = randomUsername();
    var candidateWithNote = createNote(candidate, user);
    var noteId = getIdOfFirstNote(candidateWithNote);
    var request = createRequest(candidate.getIdentifier(), noteId, user.value()).build();
    var viewingScopeValidatorReturningFalse = new FakeViewingScopeValidator(false);
    handler =
        new RemoveNoteHandler(
            scenario.getCandidateRepository(),
            scenario.getPeriodRepository(),
            viewingScopeValidatorReturningFalse,
            mockOrganizationRetriever);
    handler.handleRequest(request, output, CONTEXT);
    var response = GatewayResponse.fromOutputStream(output, Problem.class);
    assertThat(response.getStatusCode(), is(equalTo(HttpURLConnection.HTTP_UNAUTHORIZED)));
  }

  @Test
  void shouldReturnUnauthorizedWhenNotTheUserThatCreatedTheNote() throws IOException {
    var candidate = setupValidCandidate(topLevelOrganizationId);
    var user = randomUsername();
    var candidateWithNote = createNote(candidate, user);
    var noteId = getIdOfFirstNote(candidateWithNote);
    var request = createRequest(candidate.getIdentifier(), noteId, randomString()).build();
    handler.handleRequest(request, output, CONTEXT);
    var response = GatewayResponse.fromOutputStream(output, Problem.class);
    assertThat(response.getStatusCode(), is(equalTo(HttpURLConnection.HTTP_UNAUTHORIZED)));
  }

  @Test
  void shouldBeAbleToRemoveNoteWhenTheUserThatCreatedIt() throws IOException {
    var candidate = setupValidCandidate(topLevelOrganizationId);
    var user = randomUsername();
    var candidateWithNote = createNote(candidate, user);
    var noteId = getIdOfFirstNote(candidateWithNote);
    var request = createRequest(candidate.getIdentifier(), noteId, user.value()).build();
    handler.handleRequest(request, output, CONTEXT);
    var gatewayResponse = GatewayResponse.fromOutputStream(output, CandidateDto.class);
    var body = gatewayResponse.getBodyObject(CandidateDto.class);
    assertThat(body.notes(), hasSize(0));
  }

  @Test
  void shouldReturnNotFoundWhenNoteDoesntExist() throws IOException {
    var request = createRequest(UUID.randomUUID(), UUID.randomUUID(), randomString()).build();
    handler.handleRequest(request, output, CONTEXT);
    var response = GatewayResponse.fromOutputStream(output, Problem.class);
    assertThat(response.getStatusCode(), is(equalTo(HttpURLConnection.HTTP_NOT_FOUND)));
  }

  @Test
  void shouldReturnConflictWhenRemovingNoteAndReportingPeriodIsClosed() throws IOException {
    var candidate = setupValidCandidate(topLevelOrganizationId);
    var user = randomString();
    candidate.createNote(
        new CreateNoteRequest(randomString(), user, randomUri()),
        scenario.getCandidateRepository());
    var noteId = getIdOfFirstNote(candidate);
    var request = createRequest(candidate.getIdentifier(), noteId, user).build();
    scenario.setupClosedPeriod(CURRENT_YEAR);
    handler.handleRequest(request, output, CONTEXT);
    var response = GatewayResponse.fromOutputStream(output, Problem.class);

    assertThat(response.getStatusCode(), is(Matchers.equalTo(HttpURLConnection.HTTP_CONFLICT)));
  }

  @Test
  void shouldIncludeAllowedOperations() throws IOException {
    var candidate = setupValidCandidate(topLevelOrganizationId);
    var user = randomUsername();
    var candidateWithNote = createNote(candidate, user);
    var noteId = getIdOfFirstNote(candidateWithNote);

    var request = createRequest(candidate.getIdentifier(), noteId, user.value()).build();
    var candidateDto = handleRequest(request);

    var actualAllowedOperations = candidateDto.allowedOperations();
    var expectedAllowedOperations =
        List.of(CandidateOperation.APPROVAL_APPROVE, CandidateOperation.APPROVAL_REJECT);
    assertThat(actualAllowedOperations, containsInAnyOrder(expectedAllowedOperations.toArray()));
  }

  private Candidate createNote(Candidate candidate, Username user) {
    return candidate.createNote(
        new CreateNoteRequest(randomString(), user.value(), topLevelOrganizationId),
        scenario.getCandidateRepository());
  }

  private HandlerRequestBuilder<NviNoteRequest> createRequest(
      UUID candidateIdentifier, UUID noteIdentifier, String userName) {
    return createRequestWithoutAccessRights(
            candidateIdentifier.toString(), noteIdentifier.toString(), userName)
        .withTopLevelCristinOrgId(topLevelOrganizationId)
        .withAccessRights(topLevelOrganizationId, AccessRight.MANAGE_NVI_CANDIDATES);
  }

  private UUID getIdOfFirstNote(Candidate candidateWithNote) {
    return candidateWithNote
        .toDto(topLevelOrganizationId, mockOrganizationRetriever)
        .notes()
        .getFirst()
        .identifier();
  }
}
