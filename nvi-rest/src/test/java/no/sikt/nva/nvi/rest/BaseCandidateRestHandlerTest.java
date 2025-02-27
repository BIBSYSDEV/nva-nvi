package no.sikt.nva.nvi.rest;

import static java.math.BigDecimal.ZERO;
import static no.sikt.nva.nvi.common.UpsertRequestBuilder.randomUpsertRequestBuilder;
import static no.sikt.nva.nvi.common.UpsertRequestFixtures.createUpsertCandidateRequest;
import static no.sikt.nva.nvi.common.UpsertRequestFixtures.createUpsertNonCandidateRequest;
import static no.sikt.nva.nvi.common.model.OrganizationFixtures.mockOrganizationResponseForAffiliation;
import static no.sikt.nva.nvi.test.TestUtils.CURRENT_YEAR;
import static no.sikt.nva.nvi.test.TestUtils.randomBigDecimal;
import static no.sikt.nva.nvi.test.TestUtils.randomUriWithSuffix;
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
import java.math.BigDecimal;
import java.net.URI;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import no.sikt.nva.nvi.common.TestScenario;
import no.sikt.nva.nvi.common.UpsertRequestBuilder;
import no.sikt.nva.nvi.common.client.OrganizationRetriever;
import no.sikt.nva.nvi.common.model.UpdateStatusRequest;
import no.sikt.nva.nvi.common.service.dto.CandidateDto;
import no.sikt.nva.nvi.common.service.dto.NviCreatorDto;
import no.sikt.nva.nvi.common.service.dto.UnverifiedNviCreatorDto;
import no.sikt.nva.nvi.common.service.dto.VerifiedNviCreatorDto;
import no.sikt.nva.nvi.common.service.model.ApprovalStatus;
import no.sikt.nva.nvi.common.service.model.Candidate;
import no.sikt.nva.nvi.common.service.model.InstitutionPoints;
import no.sikt.nva.nvi.common.service.model.InstitutionPoints.CreatorAffiliationPoints;
import no.sikt.nva.nvi.common.validator.FakeViewingScopeValidator;
import no.sikt.nva.nvi.common.validator.ViewingScopeValidator;
import no.unit.nva.auth.uriretriever.UriRetriever;
import no.unit.nva.testutils.HandlerRequestBuilder;
import nva.commons.apigateway.AccessRight;
import nva.commons.apigateway.ApiGatewayHandler;
import nva.commons.apigateway.GatewayResponse;
import org.apache.hc.core5.http.ContentType;
import org.junit.jupiter.api.BeforeEach;

/** Base test class for handlers that return a CandidateDto. */
@SuppressWarnings("PMD.CouplingBetweenObjects")
public abstract class BaseCandidateRestHandlerTest {
  protected static final ViewingScopeValidator mockViewingScopeValidator =
      new FakeViewingScopeValidator(true);
  protected static final Context CONTEXT = mock(Context.class);
  protected UriRetriever mockUriRetriever = mock(UriRetriever.class);
  protected OrganizationRetriever mockOrganizationRetriever =
      new OrganizationRetriever(mockUriRetriever);
  protected String resourcePathParameter;
  protected URI topLevelOrganizationId;
  protected URI subOrganizationId;
  protected ByteArrayOutputStream output;
  protected ApiGatewayHandler<?, CandidateDto> handler;
  protected TestScenario scenario;

  protected InputStream createRequestWithAdminAccess(String resourceIdentifier)
      throws JsonProcessingException {
    return createRequest(resourceIdentifier, topLevelOrganizationId, MANAGE_NVI);
  }

  protected InputStream createRequest(
      String resourceIdentifier, URI organizationId, AccessRight accessRight)
      throws JsonProcessingException {
    return new HandlerRequestBuilder<InputStream>(dtoObjectMapper)
        .withHeaders(Map.of(HttpHeaders.ACCEPT, ContentType.APPLICATION_JSON.getMimeType()))
        .withTopLevelCristinOrgId(organizationId)
        .withAccessRights(organizationId, accessRight)
        .withUserName(randomString())
        .withPathParameters(Map.of(resourcePathParameter, resourceIdentifier))
        .build();
  }

  @BeforeEach
  protected void commonSetup() {
    mockUriRetriever = mock(UriRetriever.class);
    mockOrganizationRetriever = new OrganizationRetriever(mockUriRetriever);
    subOrganizationId = randomUriWithSuffix("subOrganization");
    topLevelOrganizationId = randomUriWithSuffix("topLevelOrganization");
    mockOrganizationResponseForAffiliation(
        topLevelOrganizationId, subOrganizationId, mockUriRetriever);

    output = new ByteArrayOutputStream();
    scenario = new TestScenario();
    scenario.setupOpenPeriod(String.valueOf(CURRENT_YEAR));
    handler = createHandler();
  }

  protected abstract ApiGatewayHandler<?, CandidateDto> createHandler();

  protected InputStream createRequestWithoutAccessRight(String resourceIdentifier)
      throws JsonProcessingException {
    return new HandlerRequestBuilder<InputStream>(dtoObjectMapper)
        .withHeaders(Map.of(HttpHeaders.ACCEPT, ContentType.APPLICATION_JSON.getMimeType()))
        .withTopLevelCristinOrgId(topLevelOrganizationId)
        .withUserName(randomString())
        .withPathParameters(Map.of(resourcePathParameter, resourceIdentifier))
        .build();
  }

  protected InputStream createRequestWithCuratorAccess(String resourceIdentifier)
      throws JsonProcessingException {
    return createRequest(resourceIdentifier, topLevelOrganizationId, MANAGE_NVI_CANDIDATES);
  }

  protected Candidate setupValidCandidate(URI topLevelOrganizationId) {
    var verifiedCreator = setupDefaultVerifiedCreator();
    var request =
        createUpsertCandidateRequestWithPoints(
                Map.of(topLevelOrganizationId, List.of(verifiedCreator)))
            .build();
    return scenario.upsertCandidate(request);
  }

  protected VerifiedNviCreatorDto setupDefaultVerifiedCreator() {
    return setupVerifiedCreator(randomUri(), List.of(subOrganizationId), topLevelOrganizationId);
  }

  protected static UpsertRequestBuilder createUpsertCandidateRequestWithPoints(
      Map<URI, Collection<NviCreatorDto>> nviCreatorsPerInstitution) {
    var institutionPoints =
        nviCreatorsPerInstitution.entrySet().stream()
            .map(BaseCandidateRestHandlerTest::getCreatorPoints)
            .toList();
    var totalPoints =
        institutionPoints.stream()
            .map(InstitutionPoints::institutionPoints)
            .reduce(ZERO, BigDecimal::add);
    var verifiedCreators =
        nviCreatorsPerInstitution.values().stream()
            .flatMap(Collection::stream)
            .filter(VerifiedNviCreatorDto.class::isInstance)
            .map(VerifiedNviCreatorDto.class::cast)
            .toList();
    var unverifiedCreators =
        nviCreatorsPerInstitution.values().stream()
            .flatMap(Collection::stream)
            .filter(UnverifiedNviCreatorDto.class::isInstance)
            .map(UnverifiedNviCreatorDto.class::cast)
            .toList();

    var creatorMap =
        nviCreatorsPerInstitution.entrySet().stream()
            .flatMap(
                entry ->
                    entry.getValue().stream()
                        .filter(VerifiedNviCreatorDto.class::isInstance)
                        .map(VerifiedNviCreatorDto.class::cast)
                        .map(creator -> Map.entry(creator.id(), creator.affiliations())))
            .collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue));
    return randomUpsertRequestBuilder()
        .withVerifiedCreators(verifiedCreators)
        .withUnverifiedCreators(unverifiedCreators)
        .withCreators(creatorMap)
        .withPoints(institutionPoints)
        .withTotalPoints(totalPoints);
  }

  protected VerifiedNviCreatorDto setupVerifiedCreator(
      URI id, Collection<URI> affiliations, URI topLevelInstitutionId) {
    affiliations.forEach(
        affiliation ->
            mockOrganizationResponseForAffiliation(
                topLevelInstitutionId, affiliation, mockUriRetriever));
    return new VerifiedNviCreatorDto(id, List.copyOf(affiliations));
  }

  private static InstitutionPoints getCreatorPoints(
      Map.Entry<URI, Collection<NviCreatorDto>> entry) {
    var institution = entry.getKey();
    var creators = entry.getValue();
    var creatorPoints = getCreatorPoints(creators);
    var pointsPerInstitution =
        creatorPoints.stream().map(CreatorAffiliationPoints::points).reduce(ZERO, BigDecimal::add);
    return new InstitutionPoints(institution, pointsPerInstitution, creatorPoints);
  }

  private static List<CreatorAffiliationPoints> getCreatorPoints(
      Collection<NviCreatorDto> creators) {
    return creators.stream()
        .filter(VerifiedNviCreatorDto.class::isInstance)
        .map(VerifiedNviCreatorDto.class::cast)
        .map(BaseCandidateRestHandlerTest::getCreatorPoints)
        .toList();
  }

  private static CreatorAffiliationPoints getCreatorPoints(VerifiedNviCreatorDto creator) {
    return new CreatorAffiliationPoints(
        creator.id(), creator.affiliations().getFirst(), randomBigDecimal());
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
    var request =
        createUpsertCandidateRequestWithPoints(
                Map.of(topLevelOrganizationId, List.of(verifiedCreator, unverifiedCreator)))
            .build();
    return scenario.upsertCandidate(request);
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

  protected Candidate setupCandidateWithApproval() {
    // Create a candidate in a "valid" state
    var verifiedCreator = setupDefaultVerifiedCreator();
    var initialRequest =
        createUpsertCandidateRequestWithPoints(
                Map.of(topLevelOrganizationId, List.of(verifiedCreator)))
            .build();
    var candidate = scenario.upsertCandidate(initialRequest);

    // Approve the candidate
    var updateStatusRequest = createStatusRequest(ApprovalStatus.APPROVED);
    return candidate.updateApprovalStatus(updateStatusRequest, mockOrganizationRetriever);
  }

  protected UpdateStatusRequest createStatusRequest(ApprovalStatus status) {
    return UpdateStatusRequest.builder()
        .withInstitutionId(topLevelOrganizationId)
        .withApprovalStatus(status)
        .withUsername(randomString())
        .withReason(ApprovalStatus.REJECTED.equals(status) ? randomString() : null)
        .build();
  }

  protected Candidate setupCandidateWithUnverifiedCreatorFromAnotherInstitution() {
    var verifiedCreator = setupDefaultVerifiedCreator();
    var otherInstitutionId = randomUri();
    var unverifiedCreator =
        setupUnverifiedCreator(randomString(), List.of(otherInstitutionId), otherInstitutionId);
    var request =
        createUpsertCandidateRequestWithPoints(
                Map.of(topLevelOrganizationId, List.of(verifiedCreator, unverifiedCreator)))
            .build();
    return scenario.upsertCandidate(request);
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
}
