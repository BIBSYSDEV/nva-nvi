package no.sikt.nva.nvi.index.apigateway;

import static java.util.Collections.emptyMap;
import static no.sikt.nva.nvi.common.model.OrganizationFixtures.organizationIdFromIdentifier;
import static no.sikt.nva.nvi.common.model.OrganizationFixtures.randomOrganizationId;
import static no.sikt.nva.nvi.common.model.OrganizationFixtures.randomOrganizationIdentifier;
import static no.sikt.nva.nvi.common.utils.CollectionUtils.mergeCollections;
import static no.sikt.nva.nvi.index.IndexDocumentFixtures.createRandomIndexDocument;
import static no.sikt.nva.nvi.index.IndexDocumentFixtures.documentWithApprovals;
import static no.sikt.nva.nvi.index.IndexDocumentFixtures.documentsForAllStatusCombinations;
import static no.sikt.nva.nvi.index.IndexDocumentFixtures.randomApproval;
import static no.sikt.nva.nvi.index.IndexDocumentFixtures.randomIndexDocumentBuilder;
import static no.sikt.nva.nvi.index.IndexDocumentFixtures.randomPublicationDetailsBuilder;
import static no.sikt.nva.nvi.index.IndexDocumentTestUtils.randomNviContributor;
import static no.sikt.nva.nvi.index.IndexHandlerEnvironments.forHandler;
import static no.sikt.nva.nvi.test.TestUtils.CURRENT_YEAR;
import static no.unit.nva.testutils.RandomDataGenerator.FAKER;
import static no.unit.nva.testutils.RandomDataGenerator.objectMapper;
import static nva.commons.apigateway.RequestInfoConstants.BACKEND_SCOPE_AS_DEFINED_IN_IDENTITY_SERVICE;
import static org.assertj.core.api.Assertions.assertThat;

import com.amazonaws.services.lambda.runtime.Context;
import com.fasterxml.jackson.core.JsonProcessingException;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.math.BigDecimal;
import java.net.HttpURLConnection;
import java.net.URI;
import java.util.Collection;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;
import no.sikt.nva.nvi.common.FakeEnvironment;
import no.sikt.nva.nvi.common.model.OrganizationFixtures;
import no.sikt.nva.nvi.common.service.model.GlobalApprovalStatus;
import no.sikt.nva.nvi.index.OpenSearchContainerContext;
import no.sikt.nva.nvi.index.model.ApprovalFactory;
import no.sikt.nva.nvi.index.model.document.ApprovalStatus;
import no.sikt.nva.nvi.index.model.document.ApprovalView;
import no.sikt.nva.nvi.index.model.document.InstitutionPointsView;
import no.sikt.nva.nvi.index.model.document.NviCandidateIndexDocument;
import no.sikt.nva.nvi.index.model.report.DirectAffiliationAggregation;
import no.sikt.nva.nvi.index.model.report.InstitutionStatusAggregationReport;
import no.sikt.nva.nvi.index.model.report.TopLevelAggregation;
import no.unit.nva.commons.json.JsonUtils;
import no.unit.nva.stubs.FakeContext;
import no.unit.nva.testutils.HandlerRequestBuilder;
import nva.commons.apigateway.AccessRight;
import nva.commons.apigateway.GatewayResponse;
import nva.commons.core.paths.UriWrapper;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.zalando.problem.Problem;
import org.zalando.problem.StatusType;

class FetchInstitutionStatusAggregationHandlerTest {

  private static final FakeEnvironment ENVIRONMENT =
      forHandler(FetchInstitutionStatusAggregationHandler.class);
  private static final OpenSearchContainerContext CONTAINER =
      new OpenSearchContainerContext(ENVIRONMENT);
  private static final Context CONTEXT = new FakeContext();
  private String username;
  private URI userTopLevelOrg;
  private AccessRight userAccessRight;
  private String queryYear;
  private Map<String, String> queryParameters;
  private FetchInstitutionStatusAggregationHandler handler;
  private ByteArrayOutputStream output;

  private static final String YEAR = "year";
  private static final String INSTITUTION_ID = "institutionId";
  private static final String MALFORMED_IDENTIFIER = "not-a-cristin-identifier";
  private static final URI OUR_ORGANIZATION = organizationIdFromIdentifier("123.0.0.0");
  private static final URI OUR_SUB_ORGANIZATION =
      organizationIdFromIdentifier(FAKER.numerify("123.###.###.###"));

  @BeforeAll
  static void beforeAll() {
    CONTAINER.start();
  }

  @AfterAll
  static void afterAll() {
    CONTAINER.stop();
  }

  @BeforeEach
  void beforeEach() {
    CONTAINER.createIndex();

    username = "Curator from our organization";
    userTopLevelOrg = OUR_ORGANIZATION;
    userAccessRight = AccessRight.MANAGE_NVI_CANDIDATES;
    queryYear = String.valueOf(CURRENT_YEAR);
    queryParameters = emptyMap();

    handler =
        new FetchInstitutionStatusAggregationHandler(CONTAINER.getOpenSearchClient(), ENVIRONMENT);
    output = new ByteArrayOutputStream();
  }

  @AfterEach
  void afterEach() {
    CONTAINER.deleteIndex();
  }

  @Nested
  @DisplayName("Access control")
  class AccessControlTests {
    @Test
    void shouldReturnUnauthorizedWhenUserDoesNotHaveRequiredAccessRight() {
      userAccessRight = AccessRight.MANAGE_OWN_RESOURCES;
      var response = handleRequestExpectingProblem();
      assertThat(response)
          .extracting(Problem::getStatus)
          .extracting(StatusType::getStatusCode)
          .isEqualTo(HttpURLConnection.HTTP_UNAUTHORIZED);
    }

    @ParameterizedTest
    @EnumSource(
        value = AccessRight.class,
        names = {"MANAGE_NVI_CANDIDATES", "MANAGE_RESOURCES_ALL", "MANAGE_NVI"})
    void shouldAllowAccessForCuratorsEditorsAndAdmins(AccessRight accessRight) {
      userAccessRight = accessRight;
      var approval =
          new ApprovalFactory(OUR_ORGANIZATION).withCreatorAffiliation(OUR_ORGANIZATION).build();
      CONTAINER.addDocumentsToIndex(documentWithApprovals(approval));

      var response = handleRequest();

      assertThat(response.totals().candidateCount()).isOne();
    }

    @Test
    void shouldAllowAccessForInternalBackendClient() {
      var approval =
          new ApprovalFactory(OUR_ORGANIZATION).withCreatorAffiliation(OUR_ORGANIZATION).build();
      CONTAINER.addDocumentsToIndex(documentWithApprovals(approval));

      var response = handleRequest(backendClientRequestForInstitution(OUR_ORGANIZATION));

      assertThat(response.totals().candidateCount()).isOne();
    }

    @Test
    void shouldReturnBadRequestWhenBackendClientOmitsRequestedInstitution() {
      var response = handleRequestExpectingProblem(backendClientRequestWithoutInstitution());

      assertThat(response)
          .extracting(Problem::getStatus)
          .extracting(StatusType::getStatusCode)
          .isEqualTo(HttpURLConnection.HTTP_BAD_REQUEST);
    }

    @ParameterizedTest
    @EnumSource(
        value = AccessRight.class,
        names = {"MANAGE_NVI_CANDIDATES", "MANAGE_RESOURCES_ALL"})
    void shouldAllowCuratorsAndEditorsToRequestTheirOwnInstitution(AccessRight accessRight) {
      userAccessRight = accessRight;
      var approval =
          new ApprovalFactory(OUR_ORGANIZATION).withCreatorAffiliation(OUR_ORGANIZATION).build();
      CONTAINER.addDocumentsToIndex(documentWithApprovals(approval));
      queryParameters = Map.of(INSTITUTION_ID, getIdentifier(OUR_ORGANIZATION));

      var response = handleRequest();

      assertThat(response.topLevelOrganizationId()).isEqualTo(OUR_ORGANIZATION);
      assertThat(response.totals().candidateCount()).isOne();
    }

    @ParameterizedTest
    @ValueSource(strings = {"185.90.0", "185.90.0.0/../..", "1850000", "185.90.0.0.0"})
    void shouldReturnBadRequestWhenRequestedInstitutionIsNotACristinIdentifier(String identifier) {
      userAccessRight = AccessRight.MANAGE_NVI;
      queryParameters = Map.of(INSTITUTION_ID, identifier);

      assertThat(handleRequestReturningStatusCode()).isEqualTo(HttpURLConnection.HTTP_BAD_REQUEST);
    }

    @Test
    void shouldRejectMalformedInstitutionBeforeCheckingAccessToIt() {
      queryParameters = Map.of(INSTITUTION_ID, MALFORMED_IDENTIFIER);

      assertThat(handleRequestReturningStatusCode()).isEqualTo(HttpURLConnection.HTTP_BAD_REQUEST);
    }

    @Test
    void shouldReturnUnauthorizedWhenCuratorRequestsOtherInstitution() {
      queryParameters = Map.of(INSTITUTION_ID, randomOrganizationIdentifier());

      var response = handleRequestExpectingProblem();

      assertThat(response)
          .extracting(Problem::getStatus)
          .extracting(StatusType::getStatusCode)
          .isEqualTo(HttpURLConnection.HTTP_UNAUTHORIZED);
    }

    @Test
    void shouldReturnUnauthorizedWhenEditorRequestsOtherInstitution() {
      userAccessRight = AccessRight.MANAGE_RESOURCES_ALL;
      queryParameters = Map.of(INSTITUTION_ID, randomOrganizationIdentifier());

      var response = handleRequestExpectingProblem();

      assertThat(response)
          .extracting(Problem::getStatus)
          .extracting(StatusType::getStatusCode)
          .isEqualTo(HttpURLConnection.HTTP_UNAUTHORIZED);
    }

    @Test
    void shouldReturnAggregationForRequestedInstitutionWhenUserIsAdmin() {
      var otherOrganization = randomOrganizationId();
      var approval =
          new ApprovalFactory(otherOrganization).withCreatorAffiliation(otherOrganization).build();
      CONTAINER.addDocumentsToIndex(documentWithApprovals(approval));

      userAccessRight = AccessRight.MANAGE_NVI;
      queryParameters = Map.of(INSTITUTION_ID, getIdentifier(otherOrganization));

      var response = handleRequest();

      assertThat(response.topLevelOrganizationId()).isEqualTo(otherOrganization);
      assertThat(response.totals().candidateCount()).isOne();
    }

    @Test
    void shouldFallBackToOwnInstitutionWhenAdminOmitsRequestedInstitution() {
      userAccessRight = AccessRight.MANAGE_NVI;

      var response = handleRequest();

      assertThat(response.topLevelOrganizationId()).isEqualTo(OUR_ORGANIZATION);
    }

    @Test
    void shouldAggregateLegacyDocumentWithNviContributorsInContributorsList() {
      var approval =
          new ApprovalFactory(OUR_ORGANIZATION).withCreatorAffiliation(OUR_ORGANIZATION).build();
      var nviContributors = List.of(randomNviContributor(OUR_ORGANIZATION));
      var legacyPublicationDetails =
          randomPublicationDetailsBuilder()
              .withNviContributors(nviContributors)
              .withContributors(nviContributors)
              .build();
      var legacyDocument =
          randomIndexDocumentBuilder(legacyPublicationDetails, List.of(approval)).build();
      CONTAINER.addDocumentsToIndex(legacyDocument);

      var response = handleRequest();

      assertThat(response.totals().candidateCount()).isOne();
    }

    @Test
    void shouldExcludeUnrelatedCandidates() {
      var otherOrganization = randomOrganizationId();
      var approval =
          new ApprovalFactory(otherOrganization).withCreatorAffiliation(otherOrganization).build();
      CONTAINER.addDocumentsToIndex(documentWithApprovals(approval));

      var response = handleRequest();

      assertThat(response.totals().candidateCount()).isZero();
      assertThat(response.byOrganization()).extractingByKey(otherOrganization).isNull();
    }
  }

  @Nested
  @DisplayName("Totals for top-level organization")
  class TotalAggregationTests {
    @Test
    void shouldIncludeYearAndTopLevelOrganizationInReport() {
      userTopLevelOrg = randomOrganizationId();
      var response = handleRequest();

      assertThat(response)
          .extracting(
              InstitutionStatusAggregationReport::topLevelOrganizationId,
              InstitutionStatusAggregationReport::year)
          .containsExactly(userTopLevelOrg, queryYear);
    }

    @Test
    void shouldReturnEmptyAggregateForOrganizationWithNoData() {
      userTopLevelOrg = randomOrganizationId();
      var response = handleRequest();

      var expectedTotals =
          new TopLevelAggregation(
              0, BigDecimal.ZERO, getEmptyGlobalApprovalStatusMap(), getEmptyApprovalStatusMap());
      var expectedResponse =
          new InstitutionStatusAggregationReport(
              queryYear, userTopLevelOrg, expectedTotals, emptyMap());

      assertThat(response).isEqualTo(expectedResponse);
    }

    @Test
    void shouldIncludeTotalsForTopLevelOrganization() {
      var documentsForTopLevelOrganization =
          documentsForAllStatusCombinations(OUR_ORGANIZATION, OUR_ORGANIZATION);
      var documentsForSubOrganization =
          documentsForAllStatusCombinations(OUR_ORGANIZATION, OUR_SUB_ORGANIZATION);
      var unrelatedDocuments = createUnrelatedDocuments();
      CONTAINER.addDocumentsToIndex(
          mergeCollections(
              documentsForTopLevelOrganization, documentsForSubOrganization, unrelatedDocuments));

      var relevantDocuments =
          mergeCollections(documentsForTopLevelOrganization, documentsForSubOrganization);
      var expectedTotals = getExpectedTotalAggregation(relevantDocuments);

      var response = handleRequest();
      assertThat(response.totals()).isEqualTo(expectedTotals);
    }

    @Test
    void shouldExcludePointsFromRejectedCandidates() {
      var approval =
          new ApprovalFactory(OUR_ORGANIZATION)
              .withCreatorAffiliation(OUR_ORGANIZATION)
              .withApprovalStatus(ApprovalStatus.REJECTED)
              .build();
      CONTAINER.addDocumentsToIndex(documentWithApprovals(approval, randomApproval()));

      var response = handleRequest();

      assertThat(response.totals().points()).isZero();
    }

    @Test
    void shouldExcludePointsFromPendingCandidates() {
      var approval =
          new ApprovalFactory(OUR_ORGANIZATION)
              .withCreatorAffiliation(OUR_ORGANIZATION)
              .withGlobalApprovalStatus(GlobalApprovalStatus.PENDING)
              .build();
      CONTAINER.addDocumentsToIndex(documentWithApprovals(approval, randomApproval()));

      var response = handleRequest();

      assertThat(response.totals().points()).isZero();
    }

    private TopLevelAggregation getExpectedTotalAggregation(
        Collection<NviCandidateIndexDocument> relevantDocuments) {
      var expectedTotalPoints = getSumOfTopLevelPoints(OUR_ORGANIZATION, relevantDocuments);
      var expectedGlobalStatusMap = getGlobalApprovalStatusCounts(relevantDocuments);
      var expectedStatusMap = getApprovalStatusCounts(userTopLevelOrg, relevantDocuments);

      return new TopLevelAggregation(
          relevantDocuments.size(),
          expectedTotalPoints,
          expectedGlobalStatusMap,
          expectedStatusMap);
    }
  }

  @Nested
  @DisplayName("Aggregated by direct affiliation")
  class DirectAffiliationAggregationTests {
    @Test
    void shouldExcludeRejectedCandidatesFromPoints() {
      CONTAINER.addDocumentsToIndex(getRejectedCandidate());

      var response = handleRequest();

      var organizationAggregation = response.byOrganization().get(OUR_SUB_ORGANIZATION);
      assertThat(organizationAggregation.points()).isZero();
      assertThat(organizationAggregation.approvalStatus())
          .extractingByKey(ApprovalStatus.REJECTED)
          .isEqualTo(1);
    }

    @Test
    void shouldIncludeRejectedCandidatesInCount() {
      CONTAINER.addDocumentsToIndex(getRejectedCandidate());

      var response = handleRequest();

      var organizationAggregation = response.byOrganization().get(OUR_SUB_ORGANIZATION);
      var rejectedCount = organizationAggregation.approvalStatus().get(ApprovalStatus.REJECTED);
      assertThat(organizationAggregation.candidateCount()).isOne();
      assertThat(rejectedCount).isOne();
    }

    @Test
    void shouldReturnExpectedAggregatesForDirectAffiliations() {
      var documentsForTopLevelOrganization =
          documentsForAllStatusCombinations(OUR_ORGANIZATION, OUR_ORGANIZATION);
      var documentsForSubOrganization =
          documentsForAllStatusCombinations(OUR_ORGANIZATION, OUR_SUB_ORGANIZATION);
      var unrelatedDocuments = createUnrelatedDocuments();
      CONTAINER.addDocumentsToIndex(
          mergeCollections(
              documentsForTopLevelOrganization, documentsForSubOrganization, unrelatedDocuments));

      var expectedAggregationForTopLevelOrganization =
          getExpectedDirectAffiliationAggregation(
              OUR_ORGANIZATION, documentsForTopLevelOrganization);
      var expectedAggregationForSubOrganization =
          getExpectedDirectAffiliationAggregation(
              OUR_SUB_ORGANIZATION, documentsForSubOrganization);

      var response = handleRequest();
      assertThat(response.byOrganization())
          .extractingByKeys(OUR_ORGANIZATION, OUR_SUB_ORGANIZATION)
          .containsExactly(
              expectedAggregationForTopLevelOrganization, expectedAggregationForSubOrganization);
    }

    @Test
    void shouldHandleAggregationForUpToOneThousandInvolvedOrganizations() {
      addIndexDocumentWithOneThousandInvolvedSubOrganizations();

      var response = handleRequest();

      assertThat(response.totals().candidateCount()).isOne();
      assertThat(response.byOrganization()).hasSize(1000);
    }

    private void addIndexDocumentWithOneThousandInvolvedSubOrganizations() {
      var affiliations =
          Stream.generate(OrganizationFixtures::randomOrganizationId).limit(1000).toList();
      var approval =
          new ApprovalFactory(OUR_ORGANIZATION).withCreatorAffiliations(affiliations).build();
      CONTAINER.addDocumentsToIndex(documentWithApprovals(approval, randomApproval()));
    }

    @Test
    void shouldExcludeDisputedCandidates() {
      CONTAINER.addDocumentsToIndex(getRejectedCandidate(), getDisputedCandidate());

      var response = handleRequest();

      var organizationAggregation = response.byOrganization().get(OUR_SUB_ORGANIZATION);
      assertThat(organizationAggregation.points()).isZero();
      assertThat(organizationAggregation.candidateCount()).isOne();
      assertThat(organizationAggregation.approvalStatus())
          .extractingByKey(ApprovalStatus.APPROVED)
          .isEqualTo(0);
    }

    private NviCandidateIndexDocument getRejectedCandidate() {
      var ourApproval =
          new ApprovalFactory(OUR_ORGANIZATION)
              .withCreatorAffiliation(OUR_SUB_ORGANIZATION)
              .withApprovalStatus(ApprovalStatus.REJECTED)
              .withGlobalApprovalStatus(GlobalApprovalStatus.REJECTED)
              .build();
      var otherApproval =
          new ApprovalFactory(randomOrganizationId())
              .withApprovalStatus(ApprovalStatus.REJECTED)
              .withGlobalApprovalStatus(GlobalApprovalStatus.REJECTED)
              .build();
      return documentWithApprovals(ourApproval, otherApproval);
    }

    private NviCandidateIndexDocument getDisputedCandidate() {
      var ourApproval =
          new ApprovalFactory(OUR_ORGANIZATION)
              .withCreatorAffiliation(OUR_SUB_ORGANIZATION)
              .withApprovalStatus(ApprovalStatus.APPROVED)
              .withGlobalApprovalStatus(GlobalApprovalStatus.DISPUTE)
              .build();
      var otherApproval =
          new ApprovalFactory(randomOrganizationId())
              .withApprovalStatus(ApprovalStatus.REJECTED)
              .withGlobalApprovalStatus(GlobalApprovalStatus.DISPUTE)
              .build();
      return documentWithApprovals(ourApproval, otherApproval);
    }
  }

  private static List<NviCandidateIndexDocument> createUnrelatedDocuments() {
    var fromOtherOrganization = createRandomIndexDocument(randomOrganizationId(), CURRENT_YEAR);
    var fromLastYear = createRandomIndexDocument(OUR_ORGANIZATION, CURRENT_YEAR - 1);
    var fromNextYear = createRandomIndexDocument(OUR_ORGANIZATION, CURRENT_YEAR + 1);
    return List.of(fromOtherOrganization, fromLastYear, fromNextYear);
  }

  private InstitutionStatusAggregationReport handleRequest() {
    return handleRequest(createRequest());
  }

  private InstitutionStatusAggregationReport handleRequest(InputStream request) {
    try {
      handler.handleRequest(request, output, CONTEXT);
      var response = GatewayResponse.fromOutputStream(output, String.class);
      assertThat(response.getStatusCode()).isEqualTo(HttpURLConnection.HTTP_OK);
      return objectMapper.readValue(response.getBody(), InstitutionStatusAggregationReport.class);
    } catch (IOException e) {
      throw new RuntimeException(e);
    }
  }

  private int handleRequestReturningStatusCode() {
    try {
      handler.handleRequest(createRequest(), output, CONTEXT);
      return GatewayResponse.fromOutputStream(output, String.class).getStatusCode();
    } catch (IOException e) {
      throw new RuntimeException(e);
    }
  }

  private Problem handleRequestExpectingProblem() {
    return handleRequestExpectingProblem(createRequest());
  }

  private Problem handleRequestExpectingProblem(InputStream request) {
    try {
      handler.handleRequest(request, output, CONTEXT);
      var response = GatewayResponse.fromOutputStream(output, Problem.class);
      return objectMapper.readValue(response.getBody(), Problem.class);
    } catch (IOException e) {
      throw new RuntimeException(e);
    }
  }

  private InputStream createRequest() {
    try {
      return new HandlerRequestBuilder<InputStream>(JsonUtils.dtoObjectMapper)
          .withTopLevelCristinOrgId(userTopLevelOrg)
          .withAccessRights(userTopLevelOrg, userAccessRight)
          .withUserName(username)
          .withPathParameters(Map.of(YEAR, queryYear))
          .withQueryParameters(queryParameters)
          .build();
    } catch (JsonProcessingException e) {
      throw new RuntimeException(e);
    }
  }

  private InputStream backendClientRequestForInstitution(URI institution) {
    return backendClientRequest(Map.of(INSTITUTION_ID, getIdentifier(institution)));
  }

  private InputStream backendClientRequestWithoutInstitution() {
    return backendClientRequest(emptyMap());
  }

  private InputStream backendClientRequest(Map<String, String> requestQueryParameters) {
    try {
      return new HandlerRequestBuilder<InputStream>(JsonUtils.dtoObjectMapper)
          .withScope(BACKEND_SCOPE_AS_DEFINED_IN_IDENTITY_SERVICE)
          .withUserName(username)
          .withPathParameters(Map.of(YEAR, queryYear))
          .withQueryParameters(requestQueryParameters)
          .build();
    } catch (JsonProcessingException e) {
      throw new RuntimeException(e);
    }
  }

  private static String getIdentifier(URI organizationId) {
    return UriWrapper.fromUri(organizationId).getLastPathElement();
  }

  private DirectAffiliationAggregation getExpectedDirectAffiliationAggregation(
      URI organization, Collection<NviCandidateIndexDocument> relevantDocuments) {
    var expectedTotalPoints = getSumOfCreatorPoints(organization, relevantDocuments);
    var expectedGlobalStatusMap = getGlobalApprovalStatusCounts(relevantDocuments);
    var expectedStatusMap = getApprovalStatusCounts(userTopLevelOrg, relevantDocuments);

    return new DirectAffiliationAggregation(
        relevantDocuments.size(), expectedTotalPoints, expectedGlobalStatusMap, expectedStatusMap);
  }

  private BigDecimal getSumOfTopLevelPoints(
      URI organization, Collection<NviCandidateIndexDocument> ourDocuments) {
    return ourDocuments.stream()
        .map(NviCandidateIndexDocument::approvals)
        .flatMap(List::stream)
        .filter(approval -> organization.equals(approval.institutionId()))
        .filter(approval -> approval.globalApprovalStatus() == GlobalApprovalStatus.APPROVED)
        .map(ApprovalView::points)
        .map(InstitutionPointsView::institutionPoints)
        .reduce(BigDecimal.ZERO, BigDecimal::add);
  }

  private BigDecimal getSumOfCreatorPoints(
      URI organization, Collection<NviCandidateIndexDocument> ourDocuments) {
    return ourDocuments.stream()
        .map(NviCandidateIndexDocument::approvals)
        .flatMap(List::stream)
        .filter(approval -> approval.globalApprovalStatus() == GlobalApprovalStatus.APPROVED)
        .map(ApprovalView::points)
        .map(InstitutionPointsView::creatorAffiliationPoints)
        .flatMap(List::stream)
        .filter(points -> organization.equals(points.affiliationId()))
        .map(InstitutionPointsView.CreatorAffiliationPointsView::points)
        .reduce(BigDecimal.ZERO, BigDecimal::add);
  }

  private static Map<ApprovalStatus, Integer> getEmptyApprovalStatusMap() {
    var map = new EnumMap<ApprovalStatus, Integer>(ApprovalStatus.class);
    for (var status : ApprovalStatus.values()) {
      map.put(status, 0);
    }
    return map;
  }

  private static Map<GlobalApprovalStatus, Integer> getEmptyGlobalApprovalStatusMap() {
    var map = new EnumMap<GlobalApprovalStatus, Integer>(GlobalApprovalStatus.class);
    for (var status : GlobalApprovalStatus.values()) {
      map.put(status, 0);
    }
    return map;
  }

  private static Map<ApprovalStatus, Integer> getApprovalStatusCounts(
      URI topLevelOrganizationId, Collection<NviCandidateIndexDocument> documents) {
    var map = getEmptyApprovalStatusMap();
    for (var document : documents) {
      var status = document.getApprovalStatusForInstitution(topLevelOrganizationId);
      map.merge(status, 1, Integer::sum);
    }
    return map;
  }

  private static Map<GlobalApprovalStatus, Integer> getGlobalApprovalStatusCounts(
      Collection<NviCandidateIndexDocument> documents) {
    var map = getEmptyGlobalApprovalStatusMap();
    for (var document : documents) {
      var status = document.globalApprovalStatus();
      map.merge(status, 1, Integer::sum);
    }
    return map;
  }
}
