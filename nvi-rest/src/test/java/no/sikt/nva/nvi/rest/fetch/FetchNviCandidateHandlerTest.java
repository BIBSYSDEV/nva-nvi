package no.sikt.nva.nvi.rest.fetch;

import static java.util.Collections.emptySet;
import static no.sikt.nva.nvi.common.UpsertRequestBuilder.randomUpsertRequestBuilder;
import static no.sikt.nva.nvi.common.dto.AllowedOperationFixtures.CURATOR_CANNOT_UPDATE_APPROVAL;
import static no.sikt.nva.nvi.common.dto.AllowedOperationFixtures.CURATOR_CAN_FINALIZE_APPROVAL;
import static no.sikt.nva.nvi.common.dto.AllowedOperationFixtures.CURATOR_CAN_RESET_APPROVAL;
import static no.sikt.nva.nvi.common.dto.NviCreatorDtoFixtures.verifiedNviCreatorDtoFrom;
import static no.sikt.nva.nvi.common.model.OrganizationFixtures.createOrganizationHierarchy;
import static no.sikt.nva.nvi.common.model.OrganizationFixtures.randomOrganizationId;
import static no.unit.nva.commons.json.JsonUtils.dtoObjectMapper;
import static no.unit.nva.testutils.RandomDataGenerator.randomString;
import static no.unit.nva.testutils.RandomDataGenerator.randomUri;
import static org.hamcrest.CoreMatchers.equalTo;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.containsInAnyOrder;
import static org.hamcrest.Matchers.is;
import static org.junit.jupiter.api.Assertions.assertEquals;

import com.google.common.net.HttpHeaders;
import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URI;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import no.sikt.nva.nvi.common.FakeEnvironment;
import no.sikt.nva.nvi.common.client.model.Organization;
import no.sikt.nva.nvi.common.service.dto.ApprovalStatusDto;
import no.sikt.nva.nvi.common.service.dto.CandidateDto;
import no.sikt.nva.nvi.common.service.dto.UnverifiedNviCreatorDto;
import no.sikt.nva.nvi.common.service.dto.problem.UnverifiedCreatorFromOrganizationProblem;
import no.sikt.nva.nvi.common.service.dto.problem.UnverifiedCreatorProblem;
import no.sikt.nva.nvi.common.service.model.Candidate;
import no.sikt.nva.nvi.rest.BaseCandidateRestHandlerTest;
import no.sikt.nva.nvi.rest.EnvironmentFixtures;
import no.unit.nva.testutils.HandlerRequestBuilder;
import nva.commons.apigateway.AccessRight;
import nva.commons.apigateway.ApiGatewayHandler;
import nva.commons.apigateway.GatewayResponse;
import org.apache.hc.core5.http.ContentType;
import org.apache.hc.core5.http.HttpStatus;
import org.assertj.core.api.Assertions;
import org.hamcrest.Matchers;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.zalando.problem.Problem;

class FetchNviCandidateHandlerTest extends BaseCandidateRestHandlerTest {

  @Override
  protected ApiGatewayHandler<Void, CandidateDto> createHandler() {
    return new FetchNviCandidateHandler(scenario.getCandidateService(), environment);
  }

  @Override
  protected FakeEnvironment getHandlerEnvironment() {
    return EnvironmentFixtures.FETCH_NVI_CANDIDATE_HANDLER;
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
    var nonApplicableCandidate = setupNonApplicableCandidate(topLevelOrganizationId);
    var request = createRequestWithCuratorAccess(nonApplicableCandidate.identifier().toString());
    handler.handleRequest(request, output, CONTEXT);
    var gatewayResponse = getGatewayResponse();

    assertEquals(HttpStatus.SC_NOT_FOUND, gatewayResponse.getStatusCode());
  }

  @ParameterizedTest
  @EnumSource(
      value = AccessRight.class,
      names = {"MANAGE_NVI_CANDIDATES", "MANAGE_NVI"},
      mode = EnumSource.Mode.INCLUDE)
  void shouldReturnCandidateWhenUserHasNviRoleInAnyOrganization(AccessRight accessRight)
      throws IOException {
    var candidate = setupValidCandidate();
    var randomOrganizationId = randomUri();

    var request =
        createRequest(candidate.identifier().toString(), randomOrganizationId, accessRight);
    var responseDto = handleRequest(request);

    Assertions.assertThat(responseDto)
        .extracting(CandidateDto::id, CandidateDto::publicationId)
        .containsExactly(candidate.getId(), candidate.getPublicationId());
  }

  @ParameterizedTest
  @EnumSource(
      value = AccessRight.class,
      names = {"MANAGE_NVI_CANDIDATES", "MANAGE_NVI"},
      mode = EnumSource.Mode.EXCLUDE)
  void shouldReturnUnauthorizedWhenUserDoesNotHaveRoleWithAccess(AccessRight accessRight)
      throws IOException {
    var candidate = setupValidCandidate();
    var request = createRequest(candidate.identifier().toString(), randomUri(), accessRight);
    handler.handleRequest(request, output, CONTEXT);
    var response = GatewayResponse.fromOutputStream(output, Problem.class);
    assertThat(response.getStatusCode(), is(equalTo(HttpURLConnection.HTTP_UNAUTHORIZED)));
  }

  @Test
  void shouldReturnUnauthorizedWhenUserDoesNotHaveSufficientAccessRight() throws IOException {
    var candidate = setupValidCandidate();
    var request = createRequestWithoutAccessRight(candidate.identifier().toString());
    handler.handleRequest(request, output, CONTEXT);
    var response = GatewayResponse.fromOutputStream(output, Problem.class);

    assertThat(response.getStatusCode(), is(Matchers.equalTo(HttpURLConnection.HTTP_UNAUTHORIZED)));
  }

  @Test
  void shouldReturnUnauthorizedWhenRequestDoesNotContainOrganizationId() throws IOException {
    var candidate = setupValidCandidate();
    var request =
        new HandlerRequestBuilder<InputStream>(dtoObjectMapper)
            .withHeaders(Map.of(HttpHeaders.ACCEPT, ContentType.APPLICATION_JSON.getMimeType()))
            .withUserName(randomString())
            .withPathParameters(Map.of(resourcePathParameter, candidate.identifier().toString()))
            .build();

    handler.handleRequest(request, output, CONTEXT);
    var response = GatewayResponse.fromOutputStream(output, Problem.class);

    assertThat(response.getStatusCode(), is(Matchers.equalTo(HttpURLConnection.HTTP_UNAUTHORIZED)));
  }

  @Test
  void shouldReturnValidCandidateWhenCandidateExists() throws IOException {
    var candidate = setupValidCandidate();
    var request = createRequestWithCuratorAccess(candidate.identifier().toString());

    var responseDto = handleRequest(request);

    Assertions.assertThat(responseDto)
        .extracting(CandidateDto::id, CandidateDto::publicationId)
        .containsExactly(candidate.getId(), candidate.getPublicationId());
  }

  @Test
  void shouldReturnCandidateDtoWithApprovalStatusNewWhenApprovalStatusIsPendingAndUnassigned()
      throws IOException {
    var candidate = setupValidCandidate();
    var request = createRequestWithCuratorAccess(candidate.identifier().toString());
    handler.handleRequest(request, output, CONTEXT);
    var response = GatewayResponse.fromOutputStream(output, CandidateDto.class);
    var actualResponse = response.getBodyObject(CandidateDto.class);
    var actualStatus = actualResponse.approvals().getFirst().status();

    assertEquals(ApprovalStatusDto.NEW, actualStatus);
  }

  @Test
  void shouldReturnValidCandidateWhenUserIsNviAdmin() throws IOException {
    var candidate = setupValidCandidate();
    var request = createRequestWithAdminAccess(candidate.identifier().toString());

    var responseDto = handleRequest(request);

    Assertions.assertThat(responseDto)
        .extracting(
            CandidateDto::id, CandidateDto::publicationId,
            CandidateDto::allowedOperations, CandidateDto::problems)
        .containsExactly(candidate.getId(), candidate.getPublicationId(), emptySet(), emptySet());
  }

  @Test
  void shouldAllowFinalizingNewValidCandidate() throws IOException {
    var candidate = setupValidCandidate();
    var request = createRequestWithCuratorAccess(candidate.identifier().toString());

    var candidateDto = handleRequest(request);

    var actualAllowedOperations = candidateDto.allowedOperations();
    assertThat(
        actualAllowedOperations, containsInAnyOrder(CURATOR_CAN_FINALIZE_APPROVAL.toArray()));
  }

  @Test
  void shouldNotAllowFinalizingNewCandidateWithUnverifiedCreator() throws IOException {
    var candidate = setupCandidateWithUnverifiedCreator();
    var request = createRequestWithCuratorAccess(candidate.identifier().toString());

    var candidateDto = handleRequest(request);

    var actualAllowedOperations = candidateDto.allowedOperations();
    assertThat(
        actualAllowedOperations, containsInAnyOrder(CURATOR_CANNOT_UPDATE_APPROVAL.toArray()));
  }

  @Test
  void shouldAllowFinalizingCandidateWithUnverifiedCreatorFromAnotherInstitution()
      throws IOException {
    var candidate = setupCandidateWithUnverifiedCreatorFromAnotherInstitution();
    var request = createRequestWithCuratorAccess(candidate.identifier().toString());

    var candidateDto = handleRequest(request);

    var actualAllowedOperations = candidateDto.allowedOperations();
    assertThat(
        actualAllowedOperations, containsInAnyOrder(CURATOR_CAN_FINALIZE_APPROVAL.toArray()));
  }

  @Test
  void shouldAllowResettingApprovalForApprovedCandidate() throws IOException {
    var candidate = setupCandidateWithApproval();
    var request = createRequestWithCuratorAccess(candidate.identifier().toString());

    var candidateDto = handleRequest(request);

    var actualAllowedOperations = candidateDto.allowedOperations();
    assertThat(actualAllowedOperations, containsInAnyOrder(CURATOR_CAN_RESET_APPROVAL.toArray()));
  }

  @Test
  void shouldHaveNoProblemsWhenCandidateIsValid() throws IOException {
    var candidate = setupValidCandidate();
    var request = createRequestWithCuratorAccess(candidate.identifier().toString());

    var candidateDto = handleRequest(request);

    var actualProblems = candidateDto.problems();
    var expectedProblems = emptySet();
    assertEquals(expectedProblems, actualProblems);
  }

  @Test
  void shouldIncludeProblemsWhenCandidateHasUnverifiedCreator() throws IOException {
    var candidate = setupCandidateWithUnverifiedCreator();
    var request = createRequestWithCuratorAccess(candidate.identifier().toString());
    var unverifiedNviCreatorNames = getUnverifiedNviCreatorNames(candidate);

    var candidateDto = handleRequest(request);

    var expectedProblems =
        Set.of(
            new UnverifiedCreatorProblem(),
            new UnverifiedCreatorFromOrganizationProblem(unverifiedNviCreatorNames));
    var actualProblems = candidateDto.problems();
    assertEquals(expectedProblems, actualProblems);
  }

  private static Set<String> getUnverifiedNviCreatorNames(Candidate candidate) {
    return candidate.publicationDetails().unverifiedCreators().stream()
        .map(UnverifiedNviCreatorDto::name)
        .collect(Collectors.toSet());
  }

  @Test
  void shouldIncludeProblemWhenCandidateHasUnverifiedCreatorFromAnotherOrganization()
      throws IOException {
    var candidate = setupCandidateWithUnverifiedCreatorFromAnotherInstitution();
    var request = createRequestWithCuratorAccess(candidate.identifier().toString());

    var candidateDto = handleRequest(request);

    var expectedProblems = Set.of(new UnverifiedCreatorProblem());
    var actualProblems = candidateDto.problems();
    assertEquals(expectedProblems, actualProblems);
  }

  @Test
  void shouldAllowTopLevelCuratorAccessToDirectSubUnits() throws IOException {
    var departmentId = randomOrganizationId();
    var subDepartmentId = randomOrganizationId();
    var groupId = randomOrganizationId();
    var topLevelOrganization =
        createOrganizationHierarchy(topLevelOrganizationId, departmentId, subDepartmentId, groupId);
    var candidate = setupCandidateWithCreatorFrom(topLevelOrganization, departmentId);

    var request = createRequestWithCuratorAccess(candidate.identifier().toString());
    var candidateDto = handleRequest(request);

    Assertions.assertThat(candidateDto.allowedOperations())
        .containsExactlyInAnyOrderElementsOf(CURATOR_CAN_FINALIZE_APPROVAL);
  }

  @Test
  void shouldAllowTopLevelCuratorAccessToNestedSubUnits() throws IOException {
    var departmentId = randomOrganizationId();
    var subDepartmentId = randomOrganizationId();
    var groupId = randomOrganizationId();
    var topLevelOrganization =
        createOrganizationHierarchy(topLevelOrganizationId, departmentId, subDepartmentId, groupId);
    var candidate = setupCandidateWithCreatorFrom(topLevelOrganization, subDepartmentId);

    var request = createRequestWithCuratorAccess(candidate.identifier().toString());
    var candidateDto = handleRequest(request);

    Assertions.assertThat(candidateDto.allowedOperations())
        .containsExactlyInAnyOrderElementsOf(CURATOR_CAN_FINALIZE_APPROVAL);
  }

  @Test
  void shouldHandleUserWithMultipleAffiliationsToSameTopLevelOrganization() throws IOException {
    var departmentId = randomOrganizationId();
    var subDepartmentId = randomOrganizationId();
    var groupId1 = randomOrganizationId();
    var groupId2 = randomOrganizationId();
    var topLevelOrganization =
        createOrganizationHierarchy(
            topLevelOrganizationId, departmentId, subDepartmentId, groupId1, groupId2);
    var candidate = setupCandidateWithCreatorFrom(topLevelOrganization, groupId1, groupId2);

    var request =
        createRequestWithCuratorAccess(
            candidate.identifier().toString(), topLevelOrganization.id());
    var candidateDto = handleRequest(request);

    Assertions.assertThat(candidateDto.allowedOperations())
        .containsExactlyInAnyOrderElementsOf(CURATOR_CAN_FINALIZE_APPROVAL);
  }

  private Candidate setupCandidateWithCreatorFrom(
      Organization topLevelOrganization, URI... affiliations) {
    var upsertRequest =
        randomUpsertRequestBuilder()
            .withCreatorsAndPoints(
                Map.of(topLevelOrganization, List.of(verifiedNviCreatorDtoFrom(affiliations))));

    var candidate = scenario.upsertCandidate(upsertRequest.build());
    return candidate;
  }
}
