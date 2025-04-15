package no.sikt.nva.nvi.rest;

import static no.sikt.nva.nvi.common.UpsertRequestFixtures.createUpsertCandidateRequest;
import static no.sikt.nva.nvi.common.UpsertRequestFixtures.createUpsertNonCandidateRequest;
import static no.sikt.nva.nvi.common.db.PeriodRepositoryFixtures.setupOpenPeriod;
import static no.sikt.nva.nvi.common.model.OrganizationFixtures.mockOrganizationResponseForAffiliation;
import static no.sikt.nva.nvi.test.TestUtils.CURRENT_YEAR;
import static no.unit.nva.commons.json.JsonUtils.dtoObjectMapper;
import static no.unit.nva.testutils.RandomDataGenerator.randomString;
import static no.unit.nva.testutils.RandomDataGenerator.randomUri;
import static nva.commons.apigateway.AccessRight.MANAGE_NVI;
import static nva.commons.apigateway.AccessRight.MANAGE_NVI_CANDIDATES;
import static org.mockito.Mockito.mock;

import com.amazonaws.services.lambda.runtime.Context;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.google.common.net.HttpHeaders;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import no.sikt.nva.nvi.common.TestScenario;
import no.sikt.nva.nvi.common.client.OrganizationRetriever;
import no.sikt.nva.nvi.common.model.CandidateFixtures;
import no.sikt.nva.nvi.common.model.UpdateStatusRequest;
import no.sikt.nva.nvi.common.service.dto.CandidateDto;
import no.sikt.nva.nvi.common.service.dto.UnverifiedNviCreatorDto;
import no.sikt.nva.nvi.common.service.dto.VerifiedNviCreatorDto;
import no.sikt.nva.nvi.common.service.model.ApprovalStatus;
import no.sikt.nva.nvi.common.service.model.Candidate;
import no.sikt.nva.nvi.common.validator.FakeViewingScopeValidator;
import no.sikt.nva.nvi.common.validator.ViewingScopeValidator;
import no.unit.nva.auth.uriretriever.UriRetriever;
import no.unit.nva.testutils.HandlerRequestBuilder;
import nva.commons.apigateway.AccessRight;
import nva.commons.apigateway.ApiGatewayHandler;
import nva.commons.apigateway.GatewayResponse;
import nva.commons.core.Environment;
import org.apache.hc.core5.http.ContentType;
import org.junit.jupiter.api.BeforeEach;

/** Base test class for handlers that return a CandidateDto. */
@SuppressWarnings("PMD.CouplingBetweenObjects")
public abstract class BaseCandidateRestHandlerTest {
  protected static final ViewingScopeValidator mockViewingScopeValidator =
      new FakeViewingScopeValidator(true);
  protected static final Context CONTEXT = mock(Context.class);
  protected static final Environment ENVIRONMENT = new Environment();
  protected UriRetriever mockUriRetriever;
  protected OrganizationRetriever mockOrganizationRetriever;
  protected String resourcePathParameter;
  protected URI topLevelOrganizationId;
  protected URI subOrganizationId;
  protected ByteArrayOutputStream output;
  protected ApiGatewayHandler<?, CandidateDto> handler;
  protected TestScenario scenario;

  @BeforeEach
  protected void commonSetup() {
    scenario = new TestScenario();
    setupOpenPeriod(scenario, CURRENT_YEAR);
    topLevelOrganizationId = scenario.getDefaultOrganization().id();
    subOrganizationId = scenario.getDefaultOrganization().hasPart().getFirst().id();
    mockOrganizationRetriever = scenario.getOrganizationRetriever();
    mockUriRetriever = scenario.getUriRetriever();

    output = new ByteArrayOutputStream();
    handler = createHandler();
  }

  protected abstract ApiGatewayHandler<?, CandidateDto> createHandler();

  protected VerifiedNviCreatorDto setupVerifiedCreator(
      URI id, Collection<URI> affiliations, URI topLevelInstitutionId) {
    affiliations.forEach(
        affiliation ->
            mockOrganizationResponseForAffiliation(
                topLevelInstitutionId, affiliation, mockUriRetriever));
    return new VerifiedNviCreatorDto(id, List.copyOf(affiliations));
  }

  protected VerifiedNviCreatorDto setupDefaultVerifiedCreator() {
    return setupVerifiedCreator(randomUri(), List.of(subOrganizationId), topLevelOrganizationId);
  }

  protected UnverifiedNviCreatorDto setupDefaultUnverifiedCreator() {
    return setupUnverifiedCreator(
        randomString(), List.of(subOrganizationId), topLevelOrganizationId);
  }

  protected UnverifiedNviCreatorDto setupUnverifiedCreator(
      String name, Collection<URI> affiliations, URI topLevelOrganizationId) {
    affiliations.forEach(
        affiliation ->
            mockOrganizationResponseForAffiliation(
                topLevelOrganizationId, affiliation, mockUriRetriever));
    return new UnverifiedNviCreatorDto(name, List.copyOf(affiliations));
  }

  protected Candidate setupValidCandidate(URI topLevelOrganizationId) {
    var verifiedCreator = setupDefaultVerifiedCreator();
    return CandidateFixtures.setupRandomApplicableCandidate(
        scenario, Map.of(topLevelOrganizationId, List.of(verifiedCreator)));
  }

  protected Candidate setupNonApplicableCandidate(URI institutionId) {
    var candidate = scenario.upsertCandidate(createUpsertCandidateRequest(institutionId).build());
    return Candidate.updateNonCandidate(
            createUpsertNonCandidateRequest(candidate.getPublicationId()),
            scenario.getCandidateRepository())
        .orElseThrow();
  }

  protected Candidate setupCandidateWithUnverifiedCreator() {
    var verifiedCreator = setupDefaultVerifiedCreator();
    var unverifiedCreator = setupDefaultUnverifiedCreator();
    return CandidateFixtures.setupRandomApplicableCandidate(
        scenario, Map.of(topLevelOrganizationId, List.of(verifiedCreator, unverifiedCreator)));
  }

  protected Candidate setupCandidateWithApproval() {
    var verifiedCreator = setupDefaultVerifiedCreator();
    var candidate =
        CandidateFixtures.setupRandomApplicableCandidate(
            scenario, Map.of(topLevelOrganizationId, List.of(verifiedCreator)));
    return scenario.updateApprovalStatus(
        candidate, ApprovalStatus.APPROVED, topLevelOrganizationId);
  }

  protected Candidate setupCandidateWithUnverifiedCreatorFromAnotherInstitution() {
    var verifiedCreator = setupDefaultVerifiedCreator();
    var otherInstitutionId = randomUri();
    var unverifiedCreator =
        setupUnverifiedCreator(randomString(), List.of(otherInstitutionId), otherInstitutionId);

    return CandidateFixtures.setupRandomApplicableCandidate(
        scenario,
        Map.of(
            topLevelOrganizationId,
            List.of(verifiedCreator),
            otherInstitutionId,
            List.of(unverifiedCreator)));
  }

  protected UpdateStatusRequest createStatusRequest(ApprovalStatus status) {
    return UpdateStatusRequest.builder()
        .withInstitutionId(topLevelOrganizationId)
        .withApprovalStatus(status)
        .withUsername(randomString())
        .withReason(ApprovalStatus.REJECTED.equals(status) ? randomString() : null)
        .build();
  }

  protected CandidateDto handleRequest(InputStream request) throws IOException {
    handler.handleRequest(request, output, CONTEXT);
    return getGatewayResponse().getBodyObject(CandidateDto.class);
  }

  protected GatewayResponse<CandidateDto> getGatewayResponse() throws JsonProcessingException {
    return dtoObjectMapper.readValue(
        output.toString(),
        dtoObjectMapper
            .getTypeFactory()
            .constructParametricType(GatewayResponse.class, CandidateDto.class));
  }

  protected HandlerRequestBuilder<InputStream> createDefaultRequestBuilder(
      String resourceIdentifier) {
    return new HandlerRequestBuilder<InputStream>(dtoObjectMapper)
        .withHeaders(Map.of(HttpHeaders.ACCEPT, ContentType.APPLICATION_JSON.getMimeType()))
        .withUserName(randomString())
        .withPathParameters(Map.of(resourcePathParameter, resourceIdentifier));
  }

  protected InputStream createRequestWithAdminAccess(String resourceIdentifier)
      throws JsonProcessingException {
    return createDefaultRequestBuilder(resourceIdentifier)
        .withAccessRights(topLevelOrganizationId, MANAGE_NVI)
        .withTopLevelCristinOrgId(topLevelOrganizationId)
        .build();
  }

  protected InputStream createRequestWithCuratorAccess(String resourceIdentifier)
      throws JsonProcessingException {
    return createDefaultRequestBuilder(resourceIdentifier)
        .withAccessRights(topLevelOrganizationId, MANAGE_NVI_CANDIDATES)
        .withTopLevelCristinOrgId(topLevelOrganizationId)
        .build();
  }

  protected InputStream createRequestWithoutAccessRight(String resourceIdentifier)
      throws JsonProcessingException {
    return createDefaultRequestBuilder(resourceIdentifier)
        .withTopLevelCristinOrgId(topLevelOrganizationId)
        .build();
  }

  protected InputStream createRequest(
      String resourceIdentifier, URI organizationId, AccessRight accessRight)
      throws JsonProcessingException {
    return createDefaultRequestBuilder(resourceIdentifier)
        .withAccessRights(organizationId, accessRight)
        .withTopLevelCristinOrgId(organizationId)
        .build();
  }
}
