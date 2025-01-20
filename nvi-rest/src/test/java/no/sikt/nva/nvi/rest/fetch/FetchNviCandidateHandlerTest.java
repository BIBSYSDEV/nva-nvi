package no.sikt.nva.nvi.rest.fetch;

import static java.util.UUID.randomUUID;
import static no.sikt.nva.nvi.rest.fetch.FetchNviCandidateHandler.CANDIDATE_IDENTIFIER;
import static no.sikt.nva.nvi.test.TestUtils.CURRENT_YEAR;
import static no.sikt.nva.nvi.test.TestUtils.createUpsertCandidateRequest;
import static no.sikt.nva.nvi.test.TestUtils.createUpsertNonCandidateRequest;
import static no.sikt.nva.nvi.test.TestUtils.periodRepositoryReturningOpenedPeriod;
import static no.unit.nva.commons.json.JsonUtils.dtoObjectMapper;
import static no.unit.nva.testutils.RandomDataGenerator.randomString;
import static no.unit.nva.testutils.RandomDataGenerator.randomUri;
import static nva.commons.apigateway.AccessRight.MANAGE_NVI;
import static nva.commons.apigateway.AccessRight.MANAGE_NVI_CANDIDATES;
import static org.hamcrest.CoreMatchers.equalTo;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;

import com.amazonaws.services.lambda.runtime.Context;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.google.common.net.HttpHeaders;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URI;
import java.util.Map;
import java.util.UUID;
import no.sikt.nva.nvi.common.db.CandidateRepository;
import no.sikt.nva.nvi.common.db.PeriodRepository;
import no.sikt.nva.nvi.common.service.dto.ApprovalStatusDto;
import no.sikt.nva.nvi.common.service.dto.CandidateDto;
import no.sikt.nva.nvi.common.service.model.Candidate;
import no.sikt.nva.nvi.common.service.requests.UpsertCandidateRequest;
import no.sikt.nva.nvi.test.FakeViewingScopeValidator;
import no.sikt.nva.nvi.test.LocalDynamoTest;
import no.unit.nva.testutils.HandlerRequestBuilder;
import nva.commons.apigateway.AccessRight;
import nva.commons.apigateway.GatewayResponse;
import org.apache.hc.core5.http.ContentType;
import org.apache.hc.core5.http.HttpStatus;
import org.hamcrest.Matchers;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.zalando.problem.Problem;
import software.amazon.awssdk.services.dynamodb.DynamoDbClient;

class FetchNviCandidateHandlerTest extends LocalDynamoTest {

  private static final Context CONTEXT = mock(Context.class);
  private final DynamoDbClient localDynamo = initializeTestDatabase();
  private ByteArrayOutputStream output;
  private FetchNviCandidateHandler handler;
  private CandidateRepository candidateRepository;
  private PeriodRepository periodRepository;

  @BeforeEach
  public void setUp() {
    output = new ByteArrayOutputStream();
    candidateRepository = new CandidateRepository(localDynamo);
    periodRepository = periodRepositoryReturningOpenedPeriod(CURRENT_YEAR);
    handler =
        new FetchNviCandidateHandler(
            candidateRepository, periodRepository, new FakeViewingScopeValidator(true));
  }

  @Test
  void shouldReturnNotFoundWhenCandidateDoesNotExist() throws IOException {
    handler.handleRequest(
        createRequest(randomUUID(), randomUri(), MANAGE_NVI_CANDIDATES), output, CONTEXT);
    var gatewayResponse = getGatewayResponse();

    assertEquals(HttpStatus.SC_NOT_FOUND, gatewayResponse.getStatusCode());
  }

  @Test
  void shouldReturnNotFoundWhenCandidateExistsButNotApplicable() throws IOException {
    var institutionId = randomUri();
    var nonApplicableCandidate = setupNonApplicableCandidate(institutionId);
    handler.handleRequest(
        createRequest(nonApplicableCandidate.getIdentifier(), institutionId, MANAGE_NVI_CANDIDATES),
        output,
        CONTEXT);
    var gatewayResponse = getGatewayResponse();

    assertEquals(HttpStatus.SC_NOT_FOUND, gatewayResponse.getStatusCode());
  }

  @Test
  void shouldReturnUnauthorizedWhenCandidateIsNotInUsersViewingScope() throws IOException {
    var institutionId = randomUri();
    var candidate = upsert(createUpsertCandidateRequest(institutionId).build());
    var request = createRequest(candidate.getIdentifier(), institutionId, MANAGE_NVI_CANDIDATES);
    var viewingScopeValidatorReturningFalse = new FakeViewingScopeValidator(false);
    handler =
        new FetchNviCandidateHandler(
            candidateRepository, periodRepository, viewingScopeValidatorReturningFalse);
    handler.handleRequest(request, output, CONTEXT);
    var response = GatewayResponse.fromOutputStream(output, Problem.class);
    assertThat(response.getStatusCode(), is(equalTo(HttpURLConnection.HTTP_UNAUTHORIZED)));
  }

  @Test
  void shouldReturnUnauthorizedWhenUserDoesNotHaveSufficientAccessRight() throws IOException {
    var institutionId = randomUri();
    var candidate = upsert(createUpsertCandidateRequest(institutionId).build());
    var request = createRequestWithoutAccessRight(candidate.getIdentifier(), institutionId);
    handler.handleRequest(request, output, CONTEXT);
    var response = GatewayResponse.fromOutputStream(output, Problem.class);

    assertThat(response.getStatusCode(), is(Matchers.equalTo(HttpURLConnection.HTTP_UNAUTHORIZED)));
  }

  @Test
  void shouldReturnValidCandidateWhenCandidateExists() throws IOException {
    var institutionId = randomUri();
    var candidate = upsert(createUpsertCandidateRequest(institutionId).build());
    var request = createRequest(candidate.getIdentifier(), institutionId, MANAGE_NVI_CANDIDATES);

    handler.handleRequest(request, output, CONTEXT);
    var response = GatewayResponse.fromOutputStream(output, CandidateDto.class);
    var expectedResponse = candidate.toDto();
    var actualResponse = response.getBodyObject(CandidateDto.class);

    assertEquals(expectedResponse, actualResponse);
  }

  @Test
  void shouldReturnCandidateDtoWithApprovalStatusNewWhenApprovalStatusIsPendingAndUnassigned()
      throws IOException {
    var institutionId = randomUri();
    var candidate = upsert(createUpsertCandidateRequest(institutionId).build());
    var request = createRequest(candidate.getIdentifier(), institutionId, MANAGE_NVI_CANDIDATES);
    handler.handleRequest(request, output, CONTEXT);
    var response = GatewayResponse.fromOutputStream(output, CandidateDto.class);
    var actualResponse = response.getBodyObject(CandidateDto.class);
    var actualStatus = actualResponse.approvals().get(0).status();

    assertEquals(ApprovalStatusDto.NEW, actualStatus);
  }

  @Test
  void shouldReturnValidCandidateWhenUserIsNviAdmin() throws IOException {
    var institutionId = randomUri();
    var candidate = upsert(createUpsertCandidateRequest(institutionId).build());
    var request = createRequest(candidate.getIdentifier(), institutionId, MANAGE_NVI);

    handler.handleRequest(request, output, CONTEXT);
    var response = GatewayResponse.fromOutputStream(output, CandidateDto.class);
    var expectedResponse = candidate.toDto();
    var actualResponse = response.getBodyObject(CandidateDto.class);

    assertEquals(expectedResponse, actualResponse);
  }

  private static InputStream createRequest(
      UUID candidateIdentifier, URI cristinInstitutionId, AccessRight accessRight)
      throws JsonProcessingException {
    var customerId = randomUri();
    return new HandlerRequestBuilder<InputStream>(dtoObjectMapper)
        .withHeaders(Map.of(HttpHeaders.ACCEPT, ContentType.APPLICATION_JSON.getMimeType()))
        .withCurrentCustomer(customerId)
        .withTopLevelCristinOrgId(cristinInstitutionId)
        .withAccessRights(customerId, accessRight)
        .withUserName(randomString())
        .withPathParameters(Map.of(CANDIDATE_IDENTIFIER, candidateIdentifier.toString()))
        .build();
  }

  private static InputStream createRequestWithoutAccessRight(
      UUID candidateIdentifier, URI cristinInstitutionId) throws JsonProcessingException {
    var customerId = randomUri();
    return new HandlerRequestBuilder<>(dtoObjectMapper)
        .withHeaders(Map.of(HttpHeaders.ACCEPT, ContentType.APPLICATION_JSON.getMimeType()))
        .withCurrentCustomer(customerId)
        .withTopLevelCristinOrgId(cristinInstitutionId)
        .withUserName(randomString())
        .withPathParameters(Map.of(CANDIDATE_IDENTIFIER, candidateIdentifier.toString()))
        .build();
  }

  private Candidate upsert(UpsertCandidateRequest request) {
    Candidate.upsert(request, candidateRepository, periodRepository);
    return Candidate.fetchByPublicationId(
        request::publicationId, candidateRepository, periodRepository);
  }

  private Candidate setupNonApplicableCandidate(URI institutionId) {
    var candidate = upsert(createUpsertCandidateRequest(institutionId).build());
    return Candidate.updateNonCandidate(
            createUpsertNonCandidateRequest(candidate.getPublicationId()), candidateRepository)
        .orElseThrow();
  }

  private GatewayResponse<CandidateDto> getGatewayResponse() throws JsonProcessingException {
    return dtoObjectMapper.readValue(
        output.toString(),
        dtoObjectMapper
            .getTypeFactory()
            .constructParametricType(GatewayResponse.class, CandidateDto.class));
  }
}
