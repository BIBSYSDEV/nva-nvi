package no.sikt.nva.nvi.index.apigateway;

import static java.util.Collections.emptyMap;
import static java.util.function.Predicate.not;
import static no.sikt.nva.nvi.common.EnvironmentFixtures.getFetchInstitutionStatusAggregationHandlerEnvironment;
import static no.sikt.nva.nvi.common.model.OrganizationFixtures.organizationIdFromIdentifier;
import static no.sikt.nva.nvi.common.model.OrganizationFixtures.randomOrganizationId;
import static no.sikt.nva.nvi.index.IndexDocumentFixtures.createRandomIndexDocument;
import static no.sikt.nva.nvi.index.IndexDocumentFixtures.randomApproval;
import static no.sikt.nva.nvi.index.IndexDocumentFixtures.randomIndexDocumentBuilder;
import static no.sikt.nva.nvi.index.IndexDocumentFixtures.randomPublicationDetailsBuilder;
import static no.sikt.nva.nvi.test.TestUtils.CURRENT_YEAR;
import static no.unit.nva.testutils.RandomDataGenerator.FAKER;
import static no.unit.nva.testutils.RandomDataGenerator.objectMapper;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

import com.amazonaws.services.lambda.runtime.Context;
import com.fasterxml.jackson.core.JsonProcessingException;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.math.BigDecimal;
import java.net.HttpURLConnection;
import java.net.URI;
import java.util.ArrayList;
import java.util.Collection;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;
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
import no.unit.nva.testutils.HandlerRequestBuilder;
import nva.commons.apigateway.AccessRight;
import nva.commons.apigateway.GatewayResponse;
import nva.commons.core.Environment;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.zalando.problem.Problem;
import org.zalando.problem.StatusType;

class FetchInstitutionStatusAggregationHandlerTest {

  private static final OpenSearchContainerContext CONTAINER = new OpenSearchContainerContext();
  private static final Context CONTEXT = mock(Context.class);
  private static final Environment ENVIRONMENT =
      getFetchInstitutionStatusAggregationHandlerEnvironment();
  private String username;
  private URI userTopLevelOrg;
  private AccessRight userAccessRight;
  private String queryYear;
  private FetchInstitutionStatusAggregationHandler handler;
  private ByteArrayOutputStream output;

  private static final String YEAR = "year";
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
          createIndexDocumentsForAllApprovalStatusTypes(OUR_ORGANIZATION, OUR_ORGANIZATION);
      var documentsForSubOrganization =
          createIndexDocumentsForAllApprovalStatusTypes(OUR_ORGANIZATION, OUR_SUB_ORGANIZATION);
      var unrelatedDocuments = createUnrelatedDocuments();
      CONTAINER.addDocumentsToIndex(
          mergeDocumentSets(
              documentsForTopLevelOrganization, documentsForSubOrganization, unrelatedDocuments));

      var relevantDocuments =
          mergeDocumentSets(documentsForTopLevelOrganization, documentsForSubOrganization);
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
      var approval =
          new ApprovalFactory(OUR_ORGANIZATION)
              .withCreatorAffiliation(OUR_SUB_ORGANIZATION)
              .withApprovalStatus(ApprovalStatus.REJECTED)
              .build();
      CONTAINER.addDocumentsToIndex(documentWithApprovals(approval, randomApproval()));

      var response = handleRequest();

      var organizationAggregation = response.byOrganization().get(OUR_SUB_ORGANIZATION);
      assertThat(organizationAggregation.points()).isZero();
      assertThat(organizationAggregation.approvalStatus())
          .extractingByKey(ApprovalStatus.REJECTED)
          .isEqualTo(1);
    }

    @Test
    void shouldIncludeRejectedCandidatesInCount() {
      var approval =
          new ApprovalFactory(OUR_ORGANIZATION)
              .withCreatorAffiliation(OUR_SUB_ORGANIZATION)
              .withApprovalStatus(ApprovalStatus.REJECTED)
              .build();
      CONTAINER.addDocumentsToIndex(documentWithApprovals(approval, randomApproval()));

      var response = handleRequest();

      var organizationAggregation = response.byOrganization().get(OUR_SUB_ORGANIZATION);
      var rejectedCount = organizationAggregation.approvalStatus().get(ApprovalStatus.REJECTED);
      assertThat(organizationAggregation.candidateCount()).isOne();
      assertThat(rejectedCount).isOne();
    }

    @Test
    void shouldReturnExpectedAggregatesForDirectAffiliations() {
      var documentsForTopLevelOrganization =
          createIndexDocumentsForAllApprovalStatusTypes(OUR_ORGANIZATION, OUR_ORGANIZATION);
      var documentsForSubOrganization =
          createIndexDocumentsForAllApprovalStatusTypes(OUR_ORGANIZATION, OUR_SUB_ORGANIZATION);
      var unrelatedDocuments = createUnrelatedDocuments();
      CONTAINER.addDocumentsToIndex(
          mergeDocumentSets(
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
  }

  private static List<NviCandidateIndexDocument> createUnrelatedDocuments() {
    var fromOtherOrganization = createRandomIndexDocument(randomOrganizationId(), CURRENT_YEAR);
    var fromLastYear = createRandomIndexDocument(OUR_ORGANIZATION, CURRENT_YEAR - 1);
    var fromNextYear = createRandomIndexDocument(OUR_ORGANIZATION, CURRENT_YEAR + 1);
    return List.of(fromOtherOrganization, fromLastYear, fromNextYear);
  }

  private static List<NviCandidateIndexDocument> createIndexDocumentsForAllApprovalStatusTypes(
      URI topLevelOrganization, URI creatorAffiliation) {
    var documents = new ArrayList<NviCandidateIndexDocument>();
    var approvalFactory = new ApprovalFactory(topLevelOrganization);
    for (var status : ApprovalStatus.values()) {
      var approval =
          approvalFactory
              .copy()
              .withApprovalStatus(status)
              .withCreatorAffiliation(creatorAffiliation)
              .build();
      documents.add(documentWithApprovals(approval));
    }
    return documents;
  }

  private static NviCandidateIndexDocument documentWithApprovals(ApprovalView... approvals) {
    var allApprovals = List.of(approvals);
    var topLevelOrganizations = allApprovals.stream().map(ApprovalView::institutionId).toList();
    var details = randomPublicationDetailsBuilder(topLevelOrganizations).build();
    return randomIndexDocumentBuilder(details, List.of(approvals)).build();
  }

  private InstitutionStatusAggregationReport handleRequest() {
    try {
      var request = createRequest();
      handler.handleRequest(request, output, CONTEXT);
      var response = GatewayResponse.fromOutputStream(output, String.class);
      assertThat(response.getStatusCode()).isEqualTo(HttpURLConnection.HTTP_OK);
      return objectMapper.readValue(response.getBody(), InstitutionStatusAggregationReport.class);
    } catch (IOException e) {
      throw new RuntimeException(e);
    }
  }

  private Problem handleRequestExpectingProblem() {
    try {
      var request = createRequest();
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
          .build();
    } catch (JsonProcessingException e) {
      throw new RuntimeException(e);
    }
  }

  private static List<NviCandidateIndexDocument> mergeDocumentSets(
      Collection<NviCandidateIndexDocument>... documentCollections) {
    return Stream.of(documentCollections).flatMap(Collection::stream).toList();
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
        .filter(not(approval -> ApprovalStatus.REJECTED.equals(approval.approvalStatus())))
        .map(ApprovalView::points)
        .map(InstitutionPointsView::institutionPoints)
        .reduce(BigDecimal.ZERO, BigDecimal::add);
  }

  private BigDecimal getSumOfCreatorPoints(
      URI organization, Collection<NviCandidateIndexDocument> ourDocuments) {
    return ourDocuments.stream()
        .map(NviCandidateIndexDocument::approvals)
        .flatMap(List::stream)
        .filter(not(approval -> ApprovalStatus.REJECTED.equals(approval.approvalStatus())))
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
