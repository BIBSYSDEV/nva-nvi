package no.sikt.nva.nvi.rest.fetch;

import static java.util.Collections.emptyList;
import static no.unit.nva.commons.json.JsonUtils.dtoObjectMapper;
import static no.unit.nva.testutils.RandomDataGenerator.randomString;
import static org.hamcrest.CoreMatchers.equalTo;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.containsInAnyOrder;
import static org.hamcrest.Matchers.is;
import static org.junit.jupiter.api.Assertions.assertEquals;

import com.google.common.net.HttpHeaders;
import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import no.sikt.nva.nvi.common.service.dto.ApprovalStatusDto;
import no.sikt.nva.nvi.common.service.dto.CandidateDto;
import no.sikt.nva.nvi.common.service.dto.CandidateOperation;
import no.sikt.nva.nvi.rest.BaseCandidateRestHandlerTest;
import no.sikt.nva.nvi.test.FakeViewingScopeValidator;
import no.unit.nva.testutils.HandlerRequestBuilder;
import nva.commons.apigateway.AccessRight;
import nva.commons.apigateway.ApiGatewayHandler;
import nva.commons.apigateway.GatewayResponse;
import org.apache.hc.core5.http.ContentType;
import org.apache.hc.core5.http.HttpStatus;
import org.hamcrest.Matchers;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.zalando.problem.Problem;

class FetchNviCandidateHandlerTest extends BaseCandidateRestHandlerTest {

  @Override
  protected ApiGatewayHandler<Void, CandidateDto> createHandler() {
    return new FetchNviCandidateHandler(
        candidateRepository,
        periodRepository,
        mockViewingScopeValidator,
        mockOrganizationRetriever);
  }

  @BeforeEach
  void setUp() {
    resourcePathParameter = "candidateIdentifier";
  }

  @Test
  void shouldReturnNotFoundWhenCandidateDoesNotExist() throws IOException {
    var request = createRequestWithCuratorAccess(UUID.randomUUID().toString());
    handler.handleRequest(request, output, CONTEXT);
    var gatewayResponse = getGatewayResponse();

    assertEquals(HttpStatus.SC_NOT_FOUND, gatewayResponse.getStatusCode());
  }

  @Test
  void shouldReturnNotFoundWhenCandidateExistsButNotApplicable() throws IOException {
    var nonApplicableCandidate = setupNonApplicableCandidate(topLevelCristinOrgId);
    var request = createRequestWithCuratorAccess(nonApplicableCandidate.getIdentifier().toString());
    handler.handleRequest(request, output, CONTEXT);
    var gatewayResponse = getGatewayResponse();

    assertEquals(HttpStatus.SC_NOT_FOUND, gatewayResponse.getStatusCode());
  }

  @ParameterizedTest
  @EnumSource(
      value = AccessRight.class,
      names = {"MANAGE_NVI_CANDIDATES", "MANAGE_NVI"},
      mode = EnumSource.Mode.INCLUDE)
  void shouldReturnUnauthorizedWhenCandidateIsNotInUsersViewingScope(AccessRight accessRight)
      throws IOException {
    var candidate = setupValidCandidate(topLevelCristinOrgId);
    var request =
        createRequest(candidate.getIdentifier().toString(), topLevelCristinOrgId, accessRight);
    var viewingScopeValidatorReturningFalse = new FakeViewingScopeValidator(false);
    handler =
        new FetchNviCandidateHandler(
            candidateRepository,
            periodRepository,
            viewingScopeValidatorReturningFalse,
            mockOrganizationRetriever);
    handler.handleRequest(request, output, CONTEXT);
    var response = GatewayResponse.fromOutputStream(output, Problem.class);
    assertThat(response.getStatusCode(), is(equalTo(HttpURLConnection.HTTP_UNAUTHORIZED)));
  }

  @Test
  void shouldReturnUnauthorizedWhenUserDoesNotHaveSufficientAccessRight() throws IOException {
    var candidate = setupValidCandidate(topLevelCristinOrgId);
    var request = createRequestWithoutAccessRight(candidate.getIdentifier().toString());
    handler.handleRequest(request, output, CONTEXT);
    var response = GatewayResponse.fromOutputStream(output, Problem.class);

    assertThat(response.getStatusCode(), is(Matchers.equalTo(HttpURLConnection.HTTP_UNAUTHORIZED)));
  }

  @Test
  void shouldReturnUnauthorizedWhenRequestDoesNotContainOrganizationId() throws IOException {
    var candidate = setupValidCandidate(topLevelCristinOrgId);
    var request =
        new HandlerRequestBuilder<InputStream>(dtoObjectMapper)
            .withHeaders(Map.of(HttpHeaders.ACCEPT, ContentType.APPLICATION_JSON.getMimeType()))
            .withUserName(randomString())
            .withPathParameters(Map.of(resourcePathParameter, candidate.getIdentifier().toString()))
            .build();

    handler.handleRequest(request, output, CONTEXT);
    var response = GatewayResponse.fromOutputStream(output, Problem.class);

    assertThat(response.getStatusCode(), is(Matchers.equalTo(HttpURLConnection.HTTP_UNAUTHORIZED)));
  }

  @Test
  void shouldReturnValidCandidateWhenCandidateExists() throws IOException {
    var candidate = setupValidCandidate(topLevelCristinOrgId);
    var request = createRequestWithCuratorAccess(candidate.getIdentifier().toString());

    var responseDto = handleRequest(request);

    var expectedCandidateDto = candidate.toDto(topLevelCristinOrgId, mockOrganizationRetriever);
    assertEquals(expectedCandidateDto, responseDto);
  }

  @Test
  void shouldReturnCandidateDtoWithApprovalStatusNewWhenApprovalStatusIsPendingAndUnassigned()
      throws IOException {
    var candidate = setupValidCandidate(topLevelCristinOrgId);
    var request = createRequestWithCuratorAccess(candidate.getIdentifier().toString());
    handler.handleRequest(request, output, CONTEXT);
    var response = GatewayResponse.fromOutputStream(output, CandidateDto.class);
    var actualResponse = response.getBodyObject(CandidateDto.class);
    var actualStatus = actualResponse.approvals().getFirst().status();

    assertEquals(ApprovalStatusDto.NEW, actualStatus);
  }

  @Test
  void shouldReturnValidCandidateWhenUserIsNviAdmin() throws IOException {
    var candidate = setupValidCandidate(topLevelCristinOrgId);
    var request = createRequestWithAdminAccess(candidate.getIdentifier().toString());

    var responseDto = handleRequest(request);

    var expectedCandidateDto = candidate.toDto(topLevelCristinOrgId, mockOrganizationRetriever);
    assertEquals(expectedCandidateDto, responseDto);
  }

  @Test
  void shouldAllowFinalizingNewValidCandidate() throws IOException {
    var candidate = setupValidCandidate(topLevelCristinOrgId);
    var request = createRequestWithCuratorAccess(candidate.getIdentifier().toString());

    var candidateDto = handleRequest(request);

    var actualAllowedOperations = candidateDto.allowedOperations();
    var expectedAllowedOperations =
        List.of(CandidateOperation.APPROVAL_APPROVE, CandidateOperation.APPROVAL_REJECT);
    assertThat(actualAllowedOperations, containsInAnyOrder(expectedAllowedOperations.toArray()));
  }

  @Test
  void shouldNotAllowFinalizingNewCandidateWithUnverifiedCreator() throws IOException {
    var candidate = setupCandidateWithUnverifiedCreator(topLevelCristinOrgId);
    var request = createRequestWithCuratorAccess(candidate.getIdentifier().toString());

    var candidateDto = handleRequest(request);

    var actualAllowedOperations = candidateDto.allowedOperations();
    var expectedAllowedOperations = emptyList();
    assertThat(actualAllowedOperations, containsInAnyOrder(expectedAllowedOperations.toArray()));
  }

  @Test
  void shouldAllowFinalizingCandidateWithUnverifiedCreatorFromAnotherInstitution()
      throws IOException {
    var candidate = setupCandidateWithUnverifiedCreatorFromAnotherInstitution();
    var request = createRequestWithCuratorAccess(candidate.getIdentifier().toString());

    var candidateDto = handleRequest(request);

    var actualAllowedOperations = candidateDto.allowedOperations();
    var expectedAllowedOperations =
        List.of(CandidateOperation.APPROVAL_APPROVE, CandidateOperation.APPROVAL_REJECT);
    assertThat(actualAllowedOperations, containsInAnyOrder(expectedAllowedOperations.toArray()));
  }

  @Test
  void shouldAllowResettingApprovalForApprovedCandidate() throws IOException {
    var candidate = setupCandidateWithApproval();
    var request = createRequestWithCuratorAccess(candidate.getIdentifier().toString());

    var candidateDto = handleRequest(request);

    var actualAllowedOperations = candidateDto.allowedOperations();
    var expectedAllowedOperations =
        List.of(CandidateOperation.APPROVAL_PENDING, CandidateOperation.APPROVAL_REJECT);
    assertThat(actualAllowedOperations, containsInAnyOrder(expectedAllowedOperations.toArray()));
  }
}
