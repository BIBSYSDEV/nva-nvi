package no.sikt.nva.nvi.index.apigateway;

import static com.google.common.net.HttpHeaders.ACCEPT;
import static com.google.common.net.MediaType.OOXML_SHEET;
import static java.util.Collections.emptyMap;
import static no.sikt.nva.nvi.common.EnvironmentFixtures.getFetchInstitutionStatusAggregationHandlerEnvironment;
import static no.sikt.nva.nvi.common.model.OrganizationFixtures.organizationIdFromIdentifier;
import static no.sikt.nva.nvi.common.model.OrganizationFixtures.randomOrganizationId;
import static no.sikt.nva.nvi.common.utils.CollectionUtils.mergeCollections;
import static no.sikt.nva.nvi.common.utils.DecimalUtils.adjustScaleAndRoundingMode;
import static no.sikt.nva.nvi.index.IndexDocumentFixtures.createRandomIndexDocument;
import static no.sikt.nva.nvi.index.IndexDocumentFixtures.documentWithApprovals;
import static no.sikt.nva.nvi.index.IndexDocumentFixtures.documentsForAllStatusCombinations;
import static no.sikt.nva.nvi.index.IndexDocumentFixtures.randomApproval;
import static no.sikt.nva.nvi.index.IndexDocumentFixtures.randomIndexDocumentBuilder;
import static no.sikt.nva.nvi.index.IndexDocumentFixtures.randomPublicationDetailsBuilder;
import static no.sikt.nva.nvi.test.TestUtils.CURRENT_YEAR;
import static no.sikt.nva.nvi.test.TestUtils.SCALE;
import static no.sikt.nva.nvi.test.TestUtils.randomBigDecimal;
import static no.unit.nva.testutils.RandomDataGenerator.FAKER;
import static no.unit.nva.testutils.RandomDataGenerator.objectMapper;
import static no.unit.nva.testutils.RandomDataGenerator.randomUri;
import static nva.commons.apigateway.GatewayResponse.fromOutputStream;
import static org.assertj.core.api.Assertions.assertThat;

import com.amazonaws.services.lambda.runtime.Context;
import com.fasterxml.jackson.core.JsonProcessingException;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.math.BigDecimal;
import java.net.HttpURLConnection;
import java.net.URI;
import java.util.Base64;
import java.util.Collection;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Stream;
import no.sikt.nva.nvi.common.model.OrganizationFixtures;
import no.sikt.nva.nvi.common.service.model.GlobalApprovalStatus;
import no.sikt.nva.nvi.index.OpenSearchContainerContext;
import no.sikt.nva.nvi.index.apigateway.utils.ExcelWorkbookUtil;
import no.sikt.nva.nvi.index.model.ApprovalFactory;
import no.sikt.nva.nvi.index.model.document.ApprovalStatus;
import no.sikt.nva.nvi.index.model.document.ApprovalView;
import no.sikt.nva.nvi.index.model.document.InstitutionPointsView;
import no.sikt.nva.nvi.index.model.document.InstitutionPointsView.CreatorAffiliationPointsView;
import no.sikt.nva.nvi.index.model.document.NviCandidateIndexDocument;
import no.sikt.nva.nvi.index.model.document.NviContributor;
import no.sikt.nva.nvi.index.model.document.NviOrganization;
import no.sikt.nva.nvi.index.model.report.DirectAffiliationAggregation;
import no.sikt.nva.nvi.index.model.report.InstitutionStatusAggregationReport;
import no.sikt.nva.nvi.index.model.report.TopLevelAggregation;
import no.unit.nva.commons.json.JsonUtils;
import no.unit.nva.stubs.FakeContext;
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
  private static final Context CONTEXT = new FakeContext();
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
    void shouldMatchTotalsFromAuthorShareReport() {

      var fetchInstitutionReportHandler =
          new FetchInstitutionReportHandler(CONTAINER.getOpenSearchClient(), ENVIRONMENT);

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

      try {
        var reportRequest = createReportRequest();
        var reportOutput = new ByteArrayOutputStream();
        fetchInstitutionReportHandler.handleRequest(reportRequest, reportOutput, CONTEXT);
        var reportResponse = fromOutputStream(reportOutput, String.class);
        assertThat(reportResponse.getStatusCode()).isEqualTo(HttpURLConnection.HTTP_OK);
        var decodedResponse = Base64.getDecoder().decode(reportResponse.getBody());
        var actualAffiliationPoints =
            ExcelWorkbookUtil.extractRowsInPointsForAffiliationColumn(
                new ByteArrayInputStream(decodedResponse));
        var actualSum =
            actualAffiliationPoints.stream()
                .map(BigDecimal::new)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        var expectedReportSum = getSumOfAllInstitutionPoints(OUR_ORGANIZATION, relevantDocuments);
        assertThat(actualSum).isEqualTo(expectedReportSum);
      } catch (IOException e) {
        throw new RuntimeException(e);
      }
      assertThat(response.totals()).isEqualTo(expectedTotals);
    }

    @Test
    void shouldShowHigherAggregatedPointsWhenContributorDoesNotMatchCreatorAffiliationPoints() {

      var fetchInstitutionReportHandler =
          new FetchInstitutionReportHandler(CONTAINER.getOpenSearchClient(), ENVIRONMENT);

      var matchingCreatorPoints = randomBigDecimal(SCALE);
      var unmatchedCreatorPoints = randomBigDecimal(SCALE);

      var matchingContributorId = randomUri();
      var unmatchedContributorId = randomUri();

      var matchingAffiliationPoints =
          new CreatorAffiliationPointsView(
              matchingContributorId, OUR_ORGANIZATION, matchingCreatorPoints);
      var unmatchedAffiliationPoints =
          new CreatorAffiliationPointsView(
              unmatchedContributorId, OUR_ORGANIZATION, unmatchedCreatorPoints);

      var totalInstitutionPoints = matchingCreatorPoints.add(unmatchedCreatorPoints);
      var institutionPoints =
          new InstitutionPointsView(
              OUR_ORGANIZATION,
              totalInstitutionPoints,
              List.of(matchingAffiliationPoints, unmatchedAffiliationPoints));

      var approval =
          ApprovalView.builder()
              .withInstitutionId(OUR_ORGANIZATION)
              .withLabels(Map.of())
              .withAssignee("test")
              .withApprovalStatus(ApprovalStatus.APPROVED)
              .withGlobalApprovalStatus(GlobalApprovalStatus.APPROVED)
              .withInvolvedOrganizations(Set.of(OUR_ORGANIZATION))
              .withPoints(institutionPoints)
              .build();

      var matchingContributor =
          NviContributor.builder()
              .withId(matchingContributorId.toString())
              .withName("Matching Contributor")
              .withRole("Creator")
              .withAffiliations(
                  List.of(
                      NviOrganization.builder()
                          .withId(OUR_ORGANIZATION)
                          .withPartOf(List.of(OUR_ORGANIZATION))
                          .build()))
              .build();

      var details =
          randomPublicationDetailsBuilder().withContributors(List.of(matchingContributor)).build();
      var document =
          randomIndexDocumentBuilder(details, List.of(approval))
              .withGlobalApprovalStatus(GlobalApprovalStatus.APPROVED)
              .build();

      CONTAINER.addDocumentsToIndex(List.of(document));

      var aggregationResponse = handleRequest();

      try {
        var reportRequest = createReportRequest();
        var reportOutput = new ByteArrayOutputStream();
        fetchInstitutionReportHandler.handleRequest(reportRequest, reportOutput, CONTEXT);
        var reportResponse = fromOutputStream(reportOutput, String.class);
        assertThat(reportResponse.getStatusCode()).isEqualTo(HttpURLConnection.HTTP_OK);
        var decodedResponse = Base64.getDecoder().decode(reportResponse.getBody());
        var reportPointsSum =
            ExcelWorkbookUtil.extractRowsInPointsForAffiliationColumn(
                    new ByteArrayInputStream(decodedResponse))
                .stream()
                .map(BigDecimal::new)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        var aggregatedPoints = aggregationResponse.totals().points();
        assertThat(aggregatedPoints)
            .as(
                "Aggregated points (%s) should be higher than report points (%s) "
                    + "because one creatorAffiliationPoint has no matching contributor",
                aggregatedPoints, reportPointsSum)
            .isGreaterThan(reportPointsSum);
        assertThat(reportPointsSum).isEqualTo(adjustScaleAndRoundingMode(matchingCreatorPoints));
        assertThat(aggregatedPoints).isEqualTo(adjustScaleAndRoundingMode(totalInstitutionPoints));
      } catch (IOException e) {
        throw new RuntimeException(e);
      }
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
  }

  private static List<NviCandidateIndexDocument> createUnrelatedDocuments() {
    var fromOtherOrganization = createRandomIndexDocument(randomOrganizationId(), CURRENT_YEAR);
    var fromLastYear = createRandomIndexDocument(OUR_ORGANIZATION, CURRENT_YEAR - 1);
    var fromNextYear = createRandomIndexDocument(OUR_ORGANIZATION, CURRENT_YEAR + 1);
    return List.of(fromOtherOrganization, fromLastYear, fromNextYear);
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

  private InputStream createReportRequest() {
    try {
      return new HandlerRequestBuilder<InputStream>(JsonUtils.dtoObjectMapper)
          .withTopLevelCristinOrgId(userTopLevelOrg)
          .withAccessRights(userTopLevelOrg, userAccessRight)
          .withUserName(username)
          .withPathParameters(Map.of(YEAR, queryYear))
          .withHeaders(Map.of(ACCEPT, OOXML_SHEET.toString()))
          .build();
    } catch (JsonProcessingException e) {
      throw new RuntimeException(e);
    }
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

  private BigDecimal getSumOfAllInstitutionPoints(
      URI organization, Collection<NviCandidateIndexDocument> documents) {
    return documents.stream()
        .map(NviCandidateIndexDocument::approvals)
        .flatMap(List::stream)
        .filter(approval -> organization.equals(approval.institutionId()))
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
