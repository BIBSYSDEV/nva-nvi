package no.sikt.nva.nvi.rest;

import static java.math.BigDecimal.ZERO;
import static no.sikt.nva.nvi.test.TestUtils.CURRENT_YEAR;
import static no.sikt.nva.nvi.test.TestUtils.createUpsertCandidateRequest;
import static no.sikt.nva.nvi.test.TestUtils.createUpsertNonCandidateRequest;
import static no.sikt.nva.nvi.test.TestUtils.mockOrganizationResponseForAffiliation;
import static no.sikt.nva.nvi.test.TestUtils.periodRepositoryReturningOpenedPeriod;
import static no.sikt.nva.nvi.test.TestUtils.randomBigDecimal;
import static no.sikt.nva.nvi.test.UpsertRequestBuilder.randomUpsertRequestBuilder;
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
import no.sikt.nva.nvi.common.client.OrganizationRetriever;
import no.sikt.nva.nvi.common.db.CandidateRepository;
import no.sikt.nva.nvi.common.db.PeriodRepository;
import no.sikt.nva.nvi.common.model.UpdateStatusRequest;
import no.sikt.nva.nvi.common.service.dto.CandidateDto;
import no.sikt.nva.nvi.common.service.dto.NviCreatorDto;
import no.sikt.nva.nvi.common.service.dto.UnverifiedNviCreatorDto;
import no.sikt.nva.nvi.common.service.dto.VerifiedNviCreatorDto;
import no.sikt.nva.nvi.common.service.model.ApprovalStatus;
import no.sikt.nva.nvi.common.service.model.Candidate;
import no.sikt.nva.nvi.common.service.model.InstitutionPoints;
import no.sikt.nva.nvi.common.service.model.InstitutionPoints.CreatorAffiliationPoints;
import no.sikt.nva.nvi.common.service.requests.UpsertCandidateRequest;
import no.sikt.nva.nvi.common.validator.ViewingScopeValidator;
import no.sikt.nva.nvi.test.FakeViewingScopeValidator;
import no.sikt.nva.nvi.test.LocalDynamoTest;
import no.sikt.nva.nvi.test.UpsertRequestBuilder;
import no.unit.nva.auth.uriretriever.UriRetriever;
import no.unit.nva.testutils.HandlerRequestBuilder;
import nva.commons.apigateway.AccessRight;
import nva.commons.apigateway.ApiGatewayHandler;
import nva.commons.apigateway.GatewayResponse;
import org.apache.hc.core5.http.ContentType;
import org.junit.jupiter.api.BeforeEach;
import software.amazon.awssdk.services.dynamodb.DynamoDbClient;

/** Base test class for handlers that return a CandidateDto. */
public abstract class BaseCandidateRestHandlerTest extends LocalDynamoTest {
  protected static final UriRetriever mockUriRetriever = mock(UriRetriever.class);
  protected static final OrganizationRetriever mockOrganizationRetriever =
      new OrganizationRetriever(mockUriRetriever);
  protected static final ViewingScopeValidator mockViewingScopeValidator =
      new FakeViewingScopeValidator(true);
  protected static final URI DEFAULT_VERIFIED_CREATOR_ID =
      URI.create("https://www.example.com/verifiedCreator");
  protected static final String DEFAULT_UNVERIFIED_CREATOR_NAME = "Unverified Creator";
  protected static final Context CONTEXT = mock(Context.class);
  protected static String resourcePathParameter;
  protected static URI currentCustomerId;
  protected static URI topLevelCristinOrgId;
  protected static URI subUnitCristinOrgId;
  protected final DynamoDbClient localDynamo = initializeTestDatabase();
  protected ByteArrayOutputStream output;
  protected CandidateRepository candidateRepository;
  protected PeriodRepository periodRepository;
  protected ApiGatewayHandler<?, CandidateDto> handler;

  protected static InputStream createRequestWithAdminAccess(String resourceIdentifier)
      throws JsonProcessingException {
    return createRequest(resourceIdentifier, topLevelCristinOrgId, currentCustomerId, MANAGE_NVI);
  }

  @BeforeEach
  protected void commonSetup() {
    output = new ByteArrayOutputStream();
    candidateRepository = new CandidateRepository(localDynamo);
    periodRepository = periodRepositoryReturningOpenedPeriod(CURRENT_YEAR);
    handler = createHandler();

    subUnitCristinOrgId = randomUri();
    topLevelCristinOrgId = randomUri();
    currentCustomerId = topLevelCristinOrgId;
  }

  protected abstract ApiGatewayHandler<?, CandidateDto> createHandler();

  protected InputStream createRequestWithoutAccessRight(String resourceIdentifier)
      throws JsonProcessingException {
    return new HandlerRequestBuilder<InputStream>(dtoObjectMapper)
        .withHeaders(Map.of(HttpHeaders.ACCEPT, ContentType.APPLICATION_JSON.getMimeType()))
        .withCurrentCustomer(currentCustomerId)
        .withTopLevelCristinOrgId(topLevelCristinOrgId)
        .withUserName(randomString())
        .withPathParameters(Map.of(resourcePathParameter, resourceIdentifier))
        .build();
  }

  protected InputStream createRequestWithCuratorAccess(String resourceIdentifier)
      throws JsonProcessingException {
    return createRequest(
        resourceIdentifier, topLevelCristinOrgId, currentCustomerId, MANAGE_NVI_CANDIDATES);
  }

  protected static InputStream createRequest(
      String resourceIdentifier, URI cristinInstitutionId, URI customerId, AccessRight accessRight)
      throws JsonProcessingException {
    return new HandlerRequestBuilder<InputStream>(dtoObjectMapper)
        .withHeaders(Map.of(HttpHeaders.ACCEPT, ContentType.APPLICATION_JSON.getMimeType()))
        .withCurrentCustomer(customerId)
        .withTopLevelCristinOrgId(cristinInstitutionId)
        .withAccessRights(customerId, accessRight)
        .withUserName(randomString())
        .withPathParameters(Map.of(resourcePathParameter, resourceIdentifier))
        .build();
  }

  protected Candidate setupValidCandidate(URI institutionId) {
    var verifiedCreator = setupDefaultVerifiedCreator();
    var request =
        createUpsertCandidateRequestWithPoints(Map.of(institutionId, List.of(verifiedCreator)))
            .build();
    return upsert(request);
  }

  protected VerifiedNviCreatorDto setupDefaultVerifiedCreator() {
    return setupVerifiedCreator(
        DEFAULT_VERIFIED_CREATOR_ID, List.of(subUnitCristinOrgId), topLevelCristinOrgId);
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

  protected Candidate upsert(UpsertCandidateRequest request) {
    Candidate.upsert(request, candidateRepository, periodRepository);
    return Candidate.fetchByPublicationId(
        request::publicationId, candidateRepository, periodRepository);
  }

  protected static VerifiedNviCreatorDto setupVerifiedCreator(
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
    var candidate = upsert(createUpsertCandidateRequest(institutionId).build());
    return Candidate.updateNonCandidate(
            createUpsertNonCandidateRequest(candidate.getPublicationId()), candidateRepository)
        .orElseThrow();
  }

  protected Candidate setupCandidateWithUnverifiedCreator() {
    var verifiedCreator = setupDefaultVerifiedCreator();
    var unverifiedCreator = setupDefaultUnverifiedCreator();
    var request =
        createUpsertCandidateRequestWithPoints(
                Map.of(subUnitCristinOrgId, List.of(verifiedCreator, unverifiedCreator)))
            .build();
    return upsert(request);
  }

  protected UnverifiedNviCreatorDto setupDefaultUnverifiedCreator() {
    return setupUnverifiedCreator(
        DEFAULT_UNVERIFIED_CREATOR_NAME, List.of(subUnitCristinOrgId), topLevelCristinOrgId);
  }

  protected static UnverifiedNviCreatorDto setupUnverifiedCreator(
      String name, Collection<URI> affiliations, URI topLevelInstitutionId) {
    affiliations.forEach(
        affiliation ->
            mockOrganizationResponseForAffiliation(
                topLevelInstitutionId, affiliation, mockUriRetriever));
    return new UnverifiedNviCreatorDto(name, List.copyOf(affiliations));
  }

  protected Candidate setupCandidateWithApproval() {
    // Create a candidate in a "valid" state
    var verifiedCreator = setupDefaultVerifiedCreator();
    var initialRequest =
        createUpsertCandidateRequestWithPoints(
                Map.of(topLevelCristinOrgId, List.of(verifiedCreator)))
            .build();
    var candidate = upsert(initialRequest);

    // Approve the candidate
    var updateStatusRequest = createStatusRequest(ApprovalStatus.APPROVED);
    return candidate.updateApprovalStatus(updateStatusRequest, mockOrganizationRetriever);
  }

  protected static UpdateStatusRequest createStatusRequest(ApprovalStatus status) {
    return UpdateStatusRequest.builder()
        .withInstitutionId(currentCustomerId)
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
                Map.of(subUnitCristinOrgId, List.of(verifiedCreator, unverifiedCreator)))
            .build();
    return upsert(request);
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
