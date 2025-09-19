package no.sikt.nva.nvi.rest;

import static no.sikt.nva.nvi.common.UpsertRequestBuilder.randomUpsertRequestBuilder;
import static no.sikt.nva.nvi.common.UpsertRequestFixtures.createUpsertCandidateRequest;
import static no.sikt.nva.nvi.common.UpsertRequestFixtures.createUpsertNonCandidateRequest;
import static no.sikt.nva.nvi.common.db.PeriodRepositoryFixtures.setupOpenPeriod;
import static no.sikt.nva.nvi.common.dto.NviCreatorDtoFixtures.verifiedNviCreatorDtoFrom;
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
import no.sikt.nva.nvi.common.UpsertRequestBuilder;
import no.sikt.nva.nvi.common.client.model.Organization;
import no.sikt.nva.nvi.common.dto.NviCreatorDtoFixtures;
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
  protected String resourcePathParameter;
  protected List<Organization> topLevelOrganizations;
  protected Organization topLevelOrganization;
  protected URI topLevelOrganizationId;
  protected URI subOrganizationId;
  protected ByteArrayOutputStream output;
  protected ApiGatewayHandler<?, CandidateDto> handler;
  protected TestScenario scenario;

  @BeforeEach
  protected void commonSetup() {
    scenario = new TestScenario();
    setupOpenPeriod(scenario, CURRENT_YEAR);
    topLevelOrganization = scenario.getDefaultOrganization();
    topLevelOrganizations = List.of(topLevelOrganization);
    topLevelOrganizationId = topLevelOrganization.id();
    subOrganizationId = topLevelOrganization.hasPart().getFirst().id();
    mockUriRetriever = scenario.getMockedUriRetriever();

    output = new ByteArrayOutputStream();
    handler = createHandler();
  }

  protected abstract ApiGatewayHandler<?, CandidateDto> createHandler();

  protected VerifiedNviCreatorDto setupVerifiedCreator(
      Collection<URI> affiliations, URI topLevelInstitutionId) {
    affiliations.forEach(
        affiliation ->
            mockOrganizationResponseForAffiliation(
                topLevelInstitutionId, affiliation, mockUriRetriever));
    return verifiedNviCreatorDtoFrom(affiliations);
  }

  protected VerifiedNviCreatorDto setupDefaultVerifiedCreator() {
    return setupVerifiedCreator(List.of(subOrganizationId), topLevelOrganizationId);
  }

  protected UnverifiedNviCreatorDto setupDefaultUnverifiedCreator() {
    return setupUnverifiedCreator(List.of(subOrganizationId), topLevelOrganizationId);
  }

  protected UnverifiedNviCreatorDto setupUnverifiedCreator(
      Collection<URI> affiliations, URI topLevelOrganizationId) {
    affiliations.forEach(
        affiliation ->
            mockOrganizationResponseForAffiliation(
                topLevelOrganizationId, affiliation, mockUriRetriever));
    return NviCreatorDtoFixtures.unverifiedNviCreatorDtoFrom(affiliations);
  }

  protected UpsertRequestBuilder upsertRequestWithUnverifiedCreator() {
    var verifiedCreator = setupDefaultVerifiedCreator();
    var unverifiedCreator = setupDefaultUnverifiedCreator();
    return randomUpsertRequestBuilder()
        .withCreatorsAndPoints(
            Map.of(topLevelOrganization, List.of(verifiedCreator, unverifiedCreator)));
  }

  protected UpsertRequestBuilder upsertRequestWithOneVerifiedCreator() {
    var verifiedCreator = setupDefaultVerifiedCreator();
    return randomUpsertRequestBuilder()
        .withCreatorsAndPoints(Map.of(topLevelOrganization, List.of(verifiedCreator)));
  }

  protected Candidate setupValidCandidate() {
    return scenario.upsertCandidate(upsertRequestWithOneVerifiedCreator().build());
  }

  protected Candidate setupNonApplicableCandidate(URI institutionId) {
    var candidate = scenario.upsertCandidate(createUpsertCandidateRequest(institutionId).build());
    return Candidate.updateNonCandidate(
            createUpsertNonCandidateRequest(candidate.getPublicationId()),
            scenario.getCandidateRepository(),
            scenario.getPeriodRepository())
        .orElseThrow();
  }

  protected Candidate setupCandidateWithUnverifiedCreator() {
    return scenario.upsertCandidate(upsertRequestWithUnverifiedCreator().build());
  }

  protected Candidate setupCandidateWithApproval() {
    var candidate = setupValidCandidate();
    return scenario.updateApprovalStatusDangerously(
        candidate, ApprovalStatus.APPROVED, topLevelOrganizationId);
  }

  protected Candidate setupCandidateWithUnverifiedCreatorFromAnotherInstitution() {
    var verifiedCreator = setupDefaultVerifiedCreator();
    var otherOrganization = scenario.setupTopLevelOrganizationWithSubUnits();
    var unverifiedCreator =
        setupUnverifiedCreator(List.of(otherOrganization.id()), otherOrganization.id());

    return CandidateFixtures.setupRandomApplicableCandidate(
        scenario,
        Map.of(
            topLevelOrganization,
            List.of(verifiedCreator),
            otherOrganization,
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
        .withCurrentCustomer(randomUri())
        .withPathParameters(Map.of(resourcePathParameter, resourceIdentifier));
  }

  protected InputStream createRequestWithAdminAccess(String resourceIdentifier)
      throws JsonProcessingException {
    return createDefaultRequestBuilder(resourceIdentifier)
        .withAccessRights(topLevelOrganizationId, MANAGE_NVI)
        .withTopLevelCristinOrgId(topLevelOrganizationId)
        .build();
  }

  protected InputStream createRequestWithCuratorAccess(
      String resourceIdentifier, URI userTopLevelOrganizationId) {
    try {
      return createDefaultRequestBuilder(resourceIdentifier)
          .withAccessRights(userTopLevelOrganizationId, MANAGE_NVI_CANDIDATES)
          .withTopLevelCristinOrgId(userTopLevelOrganizationId)
          .build();
    } catch (JsonProcessingException e) {
      throw new RuntimeException(e);
    }
  }

  protected InputStream createRequestWithCuratorAccess(String resourceIdentifier) {
    return createRequestWithCuratorAccess(resourceIdentifier, topLevelOrganizationId);
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
