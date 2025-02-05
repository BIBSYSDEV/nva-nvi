package no.sikt.nva.nvi.rest.fetch;

import static no.sikt.nva.nvi.rest.fetch.FetchNviCandidateByPublicationIdHandler.CANDIDATE_PUBLICATION_ID;
import static no.sikt.nva.nvi.test.TestUtils.randomYear;
import static no.sikt.nva.nvi.test.TestUtils.setupReportedCandidate;
import static no.unit.nva.testutils.RandomDataGenerator.randomUri;
import static org.hamcrest.CoreMatchers.equalTo;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.containsInAnyOrder;
import static org.hamcrest.Matchers.is;
import static org.junit.jupiter.api.Assertions.assertEquals;

import java.io.IOException;
import java.net.HttpURLConnection;
import java.util.List;
import no.sikt.nva.nvi.common.db.ReportStatus;
import no.sikt.nva.nvi.common.service.dto.ApprovalStatusDto;
import no.sikt.nva.nvi.common.service.dto.CandidateDto;
import no.sikt.nva.nvi.common.service.dto.CandidateOperation;
import no.sikt.nva.nvi.rest.BaseCandidateRestHandlerTest;
import no.sikt.nva.nvi.test.FakeViewingScopeValidator;
import nva.commons.apigateway.ApiGatewayHandler;
import nva.commons.apigateway.GatewayResponse;
import org.apache.hc.core5.http.HttpStatus;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.zalando.problem.Problem;

class FetchNviCandidateByPublicationIdHandlerTest extends BaseCandidateRestHandlerTest {

  @Override
  protected ApiGatewayHandler<Void, CandidateDto> createHandler() {
    return new FetchNviCandidateByPublicationIdHandler(
        candidateRepository,
        periodRepository,
        mockViewingScopeValidator,
        mockOrganizationRetriever);
  }

  @BeforeEach
  void setUp() {
    resourcePathParameter = CANDIDATE_PUBLICATION_ID;
  }

  @Test
  void shouldReturnNotFoundWhenCandidateDoesNotExist() throws IOException {
    var request = createRequestWithCuratorAccess(randomUri().toString());
    handler.handleRequest(request, output, CONTEXT);
    var gatewayResponse = getGatewayResponse();

    assertEquals(HttpStatus.SC_NOT_FOUND, gatewayResponse.getStatusCode());
  }

  @Test
  void shouldReturnNotFoundWhenCandidateExistsButNotApplicable() throws IOException {
    var nonApplicableCandidate = setupNonApplicableCandidate(topLevelOrganizationId);
    var request = createRequestWithCuratorAccess(nonApplicableCandidate.getIdentifier().toString());
    handler.handleRequest(request, output, CONTEXT);
    var gatewayResponse = getGatewayResponse();

    assertEquals(HttpStatus.SC_NOT_FOUND, gatewayResponse.getStatusCode());
  }

  @Test
  void shouldReturnValidCandidateWhenCandidateExists() throws IOException {
    var candidate = setupValidCandidate(topLevelOrganizationId);
    var request = createRequestWithCuratorAccess(candidate.getPublicationId().toString());

    handler.handleRequest(request, output, CONTEXT);
    var response = GatewayResponse.fromOutputStream(output, CandidateDto.class);
    var expectedResponse = candidate.toDto(topLevelOrganizationId, mockOrganizationRetriever);
    var actualResponse = response.getBodyObject(CandidateDto.class);

    assertEquals(expectedResponse, actualResponse);
  }

  @Test
  void shouldReturnCandidateWithReportStatus() throws IOException {
    var candidate =
        setupReportedCandidate(candidateRepository, randomYear(), topLevelOrganizationId);
    var publicationId = candidate.candidate().publicationId();
    var request = createRequestWithCuratorAccess(publicationId.toString());

    handler.handleRequest(request, output, CONTEXT);
    var response = GatewayResponse.fromOutputStream(output, CandidateDto.class);
    var actualCandidate = response.getBodyObject(CandidateDto.class);

    assertEquals(ReportStatus.REPORTED.getValue(), actualCandidate.status());
  }

  @Test
  void shouldReturnCandidateDtoWithApprovalStatusNewWhenApprovalStatusIsPendingAndUnassigned()
      throws IOException {
    var candidate = setupValidCandidate(topLevelOrganizationId);
    var request = createRequestWithCuratorAccess(candidate.getPublicationId().toString());
    handler.handleRequest(request, output, CONTEXT);
    var response = GatewayResponse.fromOutputStream(output, CandidateDto.class);
    var actualResponse = response.getBodyObject(CandidateDto.class);
    var actualStatus = actualResponse.approvals().getFirst().status();

    assertEquals(ApprovalStatusDto.NEW, actualStatus);
  }

  @Test
  void shouldReturnUnauthorizedWhenCandidateIsNotInUsersViewingScope() throws IOException {
    var candidate = setupValidCandidate(topLevelOrganizationId);
    var request = createRequestWithCuratorAccess(candidate.getPublicationId().toString());
    handler =
        new FetchNviCandidateByPublicationIdHandler(
            candidateRepository,
            periodRepository,
            new FakeViewingScopeValidator(false),
            mockOrganizationRetriever);
    handler.handleRequest(request, output, CONTEXT);
    var response = GatewayResponse.fromOutputStream(output, Problem.class);

    assertThat(response.getStatusCode(), is(equalTo(HttpURLConnection.HTTP_UNAUTHORIZED)));
  }

  @Test
  void shouldIncludeAllowedOperations() throws IOException {
    var candidate = setupValidCandidate(topLevelOrganizationId);
    var request = createRequestWithCuratorAccess(candidate.getPublicationId().toString());

    var candidateDto = handleRequest(request);

    var actualAllowedOperations = candidateDto.allowedOperations();
    var expectedAllowedOperations =
        List.of(CandidateOperation.APPROVAL_APPROVE, CandidateOperation.APPROVAL_REJECT);
    assertThat(actualAllowedOperations, containsInAnyOrder(expectedAllowedOperations.toArray()));
  }
}
