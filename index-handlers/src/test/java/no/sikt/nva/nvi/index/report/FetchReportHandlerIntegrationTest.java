package no.sikt.nva.nvi.index.report;

import static java.util.function.Predicate.not;
import static no.sikt.nva.nvi.common.EnvironmentFixtures.ALLOWED_ORIGIN;
import static no.sikt.nva.nvi.common.EnvironmentFixtures.getHandlerEnvironment;
import static no.sikt.nva.nvi.common.db.PeriodRepositoryFixtures.setupClosedPeriod;
import static no.sikt.nva.nvi.common.db.PeriodRepositoryFixtures.setupFuturePeriod;
import static no.sikt.nva.nvi.common.db.PeriodRepositoryFixtures.setupOpenPeriod;
import static no.sikt.nva.nvi.common.model.OrganizationFixtures.createOrganizationWithSubUnit;
import static no.sikt.nva.nvi.common.model.OrganizationFixtures.organizationIdFromIdentifier;
import static no.sikt.nva.nvi.common.model.OrganizationFixtures.randomOrganizationId;
import static no.sikt.nva.nvi.common.utils.CollectionUtils.mergeCollections;
import static no.sikt.nva.nvi.index.IndexDocumentFixtures.documentForYear;
import static no.sikt.nva.nvi.index.IndexDocumentFixtures.documentsForAllStatusCombinations;
import static no.sikt.nva.nvi.index.report.ReportConstants.INSTITUTIONS_PATH_SEGMENT;
import static no.sikt.nva.nvi.index.report.ReportConstants.INSTITUTION_PATH_PARAM;
import static no.sikt.nva.nvi.index.report.ReportConstants.PERIOD_PATH_PARAM;
import static no.sikt.nva.nvi.index.report.ReportConstants.REPORTS_PATH_SEGMENT;
import static no.sikt.nva.nvi.index.report.ReportTestUtils.getRelevantDocuments;
import static no.sikt.nva.nvi.index.report.ReportTestUtils.hasGlobalStatus;
import static no.sikt.nva.nvi.index.report.ReportTestUtils.hasLocalStatus;
import static no.sikt.nva.nvi.test.TestConstants.LAST_YEAR;
import static no.sikt.nva.nvi.test.TestConstants.NEXT_YEAR;
import static no.sikt.nva.nvi.test.TestConstants.THIS_YEAR;
import static no.unit.nva.commons.json.JsonUtils.dtoObjectMapper;
import static no.unit.nva.testutils.RandomDataGenerator.randomUri;
import static nva.commons.apigateway.GatewayResponse.fromOutputStream;
import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.params.provider.Arguments.argumentSet;

import com.amazonaws.services.lambda.runtime.Context;
import com.fasterxml.jackson.core.JsonProcessingException;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;
import no.sikt.nva.nvi.common.TestScenario;
import no.sikt.nva.nvi.common.client.model.Organization;
import no.sikt.nva.nvi.common.model.Sector;
import no.sikt.nva.nvi.common.service.model.GlobalApprovalStatus;
import no.sikt.nva.nvi.index.OpenSearchContainerContext;
import no.sikt.nva.nvi.index.model.ApprovalFactory;
import no.sikt.nva.nvi.index.model.document.ApprovalStatus;
import no.sikt.nva.nvi.index.model.document.ApprovalView;
import no.sikt.nva.nvi.index.model.document.InstitutionPointsView;
import no.sikt.nva.nvi.index.model.document.NviCandidateIndexDocument;
import no.sikt.nva.nvi.index.report.response.AllInstitutionsReport;
import no.sikt.nva.nvi.index.report.response.InstitutionReport;
import no.sikt.nva.nvi.index.report.response.ReportResponse;
import no.unit.nva.stubs.FakeContext;
import no.unit.nva.testutils.HandlerRequestBuilder;
import nva.commons.apigateway.AccessRight;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.junit.jupiter.api.TestInstance.Lifecycle;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

/**
 * This test suite sets up a complex scenario with a mix of documents for multiple
 * institutions/units. Because this is slow and the individual tests only do read operations, the
 * setup is done once and state is shared between test cases.
 */
@TestInstance(Lifecycle.PER_CLASS)
class FetchReportHandlerIntegrationTest {

  private static final Context CONTEXT = new FakeContext();
  private static final OpenSearchContainerContext CONTAINER = new OpenSearchContainerContext();
  private static final String IDENTIFIER_INSTITUTION_A = "123.0.0.0";
  private static final String IDENTIFIER_INSTITUTION_B = "456.0.0.0";
  private static final String IDENTIFIER_UNIT_A = "123.1.2.3";
  private static final Organization INSTITUTION_A =
      createOrganizationWithSubUnit(
          organizationIdFromIdentifier(IDENTIFIER_INSTITUTION_A),
          organizationIdFromIdentifier(IDENTIFIER_UNIT_A));
  private static final Organization INSTITUTION_B =
      createOrganizationWithSubUnit(
          organizationIdFromIdentifier(IDENTIFIER_INSTITUTION_B), randomOrganizationId());
  private static List<NviCandidateIndexDocument> documentsForLastYear;
  private static List<NviCandidateIndexDocument> documentsForThisYear;
  private static List<NviCandidateIndexDocument> documentsForNextYear;
  private FetchReportHandler handler;

  @BeforeAll
  void setup() {
    CONTAINER.start();

    var scenario = new TestScenario();
    setupClosedPeriod(scenario, LAST_YEAR);
    setupOpenPeriod(scenario, THIS_YEAR);
    setupFuturePeriod(scenario, NEXT_YEAR);

    handler =
        new FetchReportHandler(
            getHandlerEnvironment(ALLOWED_ORIGIN),
            scenario.getPeriodService(),
            CONTAINER.getReportAggregationClient());

    CONTAINER.createIndex();
    createIndexDocuments();
  }

  @AfterAll
  void cleanup() {
    CONTAINER.stop();
  }

  private static InputStream createRequest(Map<String, String> pathParameters, String path) {
    try {
      return new HandlerRequestBuilder<InputStream>(dtoObjectMapper)
          .withOtherProperties(Map.of("path", path))
          .withPathParameters(pathParameters)
          .withAccessRights(randomUri(), AccessRight.MANAGE_NVI)
          .build();
    } catch (JsonProcessingException e) {
      throw new RuntimeException(e);
    }
  }

  private static InputStream createAllInstitutionsRequest(String period) {
    var pathParams = Map.of(PERIOD_PATH_PARAM, period);
    var path = "%s/%s/%s".formatted(REPORTS_PATH_SEGMENT, period, INSTITUTIONS_PATH_SEGMENT);
    return createRequest(pathParams, path);
  }

  private static InputStream createInstitutionRequest(String period, String institutionIdentifier) {
    var pathParams =
        Map.of(PERIOD_PATH_PARAM, period, INSTITUTION_PATH_PARAM, institutionIdentifier);
    var path =
        "%s/%s/%s/%s"
            .formatted(
                REPORTS_PATH_SEGMENT, period, INSTITUTIONS_PATH_SEGMENT, institutionIdentifier);
    return createRequest(pathParams, path);
  }

  private static List<NviCandidateIndexDocument> documentsForLastYear(
      ApprovalFactory institutionA, ApprovalFactory institutionB) {
    var approvalForA =
        institutionA
            .withApprovalStatus(ApprovalStatus.APPROVED)
            .withGlobalApprovalStatus(GlobalApprovalStatus.APPROVED)
            .build();
    var approvalForB =
        institutionB
            .withApprovalStatus(ApprovalStatus.APPROVED)
            .withGlobalApprovalStatus(GlobalApprovalStatus.APPROVED)
            .build();
    return List.of(
        documentForYear(LAST_YEAR, true, approvalForA),
        documentForYear(LAST_YEAR, true, approvalForB),
        documentForYear(LAST_YEAR, true, approvalForA, approvalForB));
  }

  private static List<NviCandidateIndexDocument> documentsForThisYear(
      ApprovalFactory institutionA, ApprovalFactory institutionB) {
    var documentsForA = documentsForAllStatusCombinations(institutionA);
    var documentsForB = documentsForAllStatusCombinations(institutionB);
    var documentsForCollaborations = documentsForAllStatusCombinations(institutionA, institutionB);
    return mergeCollections(documentsForA, documentsForB, documentsForCollaborations);
  }

  private static List<NviCandidateIndexDocument> documentsForNextYear(
      ApprovalFactory institutionA, ApprovalFactory institutionB) {
    var approvalForA =
        institutionA
            .withApprovalStatus(ApprovalStatus.PENDING)
            .withGlobalApprovalStatus(GlobalApprovalStatus.PENDING)
            .build();
    var approvalForB =
        institutionB
            .withApprovalStatus(ApprovalStatus.NEW)
            .withGlobalApprovalStatus(GlobalApprovalStatus.PENDING)
            .build();
    return List.of(
        documentForYear(NEXT_YEAR, false, approvalForA),
        documentForYear(NEXT_YEAR, false, approvalForB),
        documentForYear(NEXT_YEAR, false, approvalForA, approvalForB));
  }

  private void createIndexDocuments() {
    var institutionA =
        new ApprovalFactory(INSTITUTION_A.id())
            .withCreatorAffiliation(organizationIdFromIdentifier(IDENTIFIER_UNIT_A))
            .withLabels(INSTITUTION_A.labels())
            .withSector(Sector.UHI);
    var institutionB =
        new ApprovalFactory(INSTITUTION_B.id())
            .withCreatorAffiliation(INSTITUTION_B.id())
            .withLabels(INSTITUTION_B.labels())
            .withSector(Sector.HEALTH);

    documentsForLastYear = documentsForLastYear(institutionA, institutionB);
    documentsForThisYear = documentsForThisYear(institutionA, institutionB);
    documentsForNextYear = documentsForNextYear(institutionA, institutionB);

    CONTAINER.addDocumentsToIndex(
        mergeCollections(documentsForThisYear, documentsForLastYear, documentsForNextYear));
  }

  private ReportResponse handleRequest(InputStream request) {
    var output = new ByteArrayOutputStream();
    try {
      handler.handleRequest(request, output, CONTEXT);
      var response = fromOutputStream(output, ReportResponse.class);
      return response.getBodyObject(ReportResponse.class);
    } catch (IOException exception) {
      throw new RuntimeException(exception);
    }
  }

  private InstitutionReport getInstitutionReport(String period, String institutionIdentifier) {
    var request = createInstitutionRequest(period, institutionIdentifier);
    return (InstitutionReport) handleRequest(request);
  }

  private AllInstitutionsReport getAllInstitutionsReport(String period) {
    var request = createAllInstitutionsRequest(period);
    return (AllInstitutionsReport) handleRequest(request);
  }

  @Nested
  class AllInstitutionsReportTests {

    @Test
    void shouldHaveExpectedType() {
      var report = getAllInstitutionsReport(THIS_YEAR);
      assertThat(report).isInstanceOf(AllInstitutionsReport.class);
    }

    private static Stream<Arguments> reportingPeriods() {
      return Stream.of(
          argumentSet("Last year", LAST_YEAR),
          argumentSet("This year", THIS_YEAR),
          argumentSet("Next year", NEXT_YEAR));
    }

    @ParameterizedTest
    @MethodSource("reportingPeriods")
    void shouldHaveExpectedPeriod(String year) {
      var report = getAllInstitutionsReport(year);
      assertThat(report.period().publishingYear()).isEqualTo(year);
    }

    @Test
    void shouldContainListOfAllInstitutionReports() {
      var allInstitutionsReport = getAllInstitutionsReport(THIS_YEAR);
      var reportForA = getInstitutionReport(THIS_YEAR, IDENTIFIER_INSTITUTION_A);
      var reportForB = getInstitutionReport(THIS_YEAR, IDENTIFIER_INSTITUTION_B);
      assertThat(allInstitutionsReport.institutions())
          .containsExactlyInAnyOrder(reportForA, reportForB);
    }
  }

  @Nested
  class InstitutionReportTests {

    private static Stream<Arguments> institutionsWithDocumentsForLastYear() {
      return Stream.of(
          argumentSet(
              IDENTIFIER_INSTITUTION_A,
              IDENTIFIER_INSTITUTION_A,
              getRelevantDocuments(documentsForLastYear, INSTITUTION_A.id())),
          argumentSet(
              IDENTIFIER_INSTITUTION_B,
              IDENTIFIER_INSTITUTION_B,
              getRelevantDocuments(documentsForLastYear, INSTITUTION_B.id())));
    }

    private static Stream<Arguments> institutionsWithDocumentsForThisYear() {
      return Stream.of(
          argumentSet(
              IDENTIFIER_INSTITUTION_A,
              IDENTIFIER_INSTITUTION_A,
              getRelevantDocuments(documentsForThisYear, INSTITUTION_A.id())),
          argumentSet(
              IDENTIFIER_INSTITUTION_B,
              IDENTIFIER_INSTITUTION_B,
              getRelevantDocuments(documentsForThisYear, INSTITUTION_B.id())));
    }

    private static Stream<Arguments> institutionsWithDocumentsForNextYear() {
      return Stream.of(
          argumentSet(
              IDENTIFIER_INSTITUTION_A,
              IDENTIFIER_INSTITUTION_A,
              getRelevantDocuments(documentsForNextYear, INSTITUTION_A.id())),
          argumentSet(
              IDENTIFIER_INSTITUTION_B,
              IDENTIFIER_INSTITUTION_B,
              getRelevantDocuments(documentsForNextYear, INSTITUTION_B.id())));
    }

    private static Stream<Arguments> institutionsWithSector() {
      return Stream.of(
          argumentSet(IDENTIFIER_INSTITUTION_A, IDENTIFIER_INSTITUTION_A, Sector.UHI),
          argumentSet(IDENTIFIER_INSTITUTION_B, IDENTIFIER_INSTITUTION_B, Sector.HEALTH));
    }

    private static Stream<Arguments> institutionsWithLabels() {
      return Stream.of(
          argumentSet(IDENTIFIER_INSTITUTION_A, IDENTIFIER_INSTITUTION_A, INSTITUTION_A.labels()),
          argumentSet(IDENTIFIER_INSTITUTION_B, IDENTIFIER_INSTITUTION_B, INSTITUTION_B.labels()));
    }

    @Test
    void shouldHaveExpectedType() {
      var report = getInstitutionReport(THIS_YEAR, IDENTIFIER_INSTITUTION_A);
      assertThat(report).isInstanceOf(InstitutionReport.class);
    }

    @Test
    void shouldHaveExpectedPeriod() {
      var report = getInstitutionReport(THIS_YEAR, IDENTIFIER_INSTITUTION_A);
      assertThat(report.period().publishingYear()).isEqualTo(THIS_YEAR);
    }

    @ParameterizedTest
    @MethodSource("institutionsWithSector")
    void shouldHaveExpectedInstitutionId(String institutionIdentifier) {
      var report = getInstitutionReport(THIS_YEAR, institutionIdentifier);

      assertThat(report.id().toString()).contains(institutionIdentifier);
      assertThat(report.institution().id().toString()).endsWith(institutionIdentifier);
    }

    @ParameterizedTest
    @MethodSource("institutionsWithSector")
    void shouldHaveExpectedSector(String institutionIdentifier, Sector expectedSector) {
      var report = getInstitutionReport(THIS_YEAR, institutionIdentifier);

      assertThat(report.sector()).isEqualTo(expectedSector);
    }

    @ParameterizedTest
    @MethodSource("institutionsWithLabels")
    void shouldHaveExpectedLabels(
        String institutionIdentifier, Map<String, String> expectedLabels) {
      var report = getInstitutionReport(THIS_YEAR, institutionIdentifier);
      assertThat(report.institution().labels()).isEqualTo(expectedLabels);
    }

    // TODO: Add test for organization tree (NP-50858)

    // TODO: Add tests for List<UnitSummary> units (NP-50858)

    @ParameterizedTest
    @MethodSource("institutionsWithDocumentsForThisYear")
    void shouldHaveExpectedPointsForOpenPeriod(
        String institutionIdentifier, List<NviCandidateIndexDocument> relevantDocs) {
      var report = getInstitutionReport(THIS_YEAR, institutionIdentifier);
      var institutionId = report.institution().id();
      var expectedPoints =
          relevantDocs.stream()
              .filter(hasGlobalStatus(GlobalApprovalStatus.APPROVED))
              .map(document -> document.getApprovalForInstitution(institutionId))
              .map(ApprovalView::points)
              .map(InstitutionPointsView::institutionPoints)
              .reduce(BigDecimal.ZERO, BigDecimal::add);

      var totals = report.institutionSummary().totals();
      assertThat(totals.validPoints()).isEqualTo(expectedPoints);
    }

    @ParameterizedTest
    @MethodSource("institutionsWithDocumentsForNextYear")
    void shouldHaveExpectedPointsForPendingPeriod(
        String institutionIdentifier, List<NviCandidateIndexDocument> relevantDocs) {
      var report = getInstitutionReport(NEXT_YEAR, institutionIdentifier);
      var institutionId = report.institution().id();
      var expectedPoints =
          relevantDocs.stream()
              .map(document -> document.getApprovalForInstitution(institutionId))
              .map(ApprovalView::points)
              .map(InstitutionPointsView::institutionPoints)
              .reduce(BigDecimal.ZERO, BigDecimal::add);

      var totals = report.institutionSummary().totals();
      assertThat(totals.validPoints()).isEqualTo(expectedPoints);
    }

    @ParameterizedTest
    @MethodSource("institutionsWithDocumentsForLastYear")
    void shouldHaveExpectedPointsForClosedPeriod(
        String institutionIdentifier, List<NviCandidateIndexDocument> relevantDocs) {
      var report = getInstitutionReport(LAST_YEAR, institutionIdentifier);
      var institutionId = report.institution().id();
      var expectedPoints =
          relevantDocs.stream()
              .filter(NviCandidateIndexDocument::reported)
              .map(document -> document.getApprovalForInstitution(institutionId))
              .map(ApprovalView::points)
              .map(InstitutionPointsView::institutionPoints)
              .reduce(BigDecimal.ZERO, BigDecimal::add);

      var totals = report.institutionSummary().totals();
      assertThat(totals.validPoints()).isEqualTo(expectedPoints);
    }

    @ParameterizedTest
    @MethodSource("institutionsWithDocumentsForThisYear")
    void shouldHaveExpectedDisputedCount(
        String institutionIdentifier, List<NviCandidateIndexDocument> relevantDocs) {
      var report = getInstitutionReport(THIS_YEAR, institutionIdentifier);
      var expectedCount =
          relevantDocs.stream().filter(hasGlobalStatus(GlobalApprovalStatus.DISPUTE)).count();

      var totals = report.institutionSummary().totals();
      assertThat(totals.disputedCount()).isEqualTo(expectedCount);
    }

    @ParameterizedTest
    @MethodSource("institutionsWithDocumentsForThisYear")
    void shouldHaveExpectedUndisputedProcessedCount(
        String institutionIdentifier, List<NviCandidateIndexDocument> relevantDocs) {
      var report = getInstitutionReport(THIS_YEAR, institutionIdentifier);
      var institutionId = report.institution().id();
      var expectedCount =
          relevantDocs.stream()
              .filter(not(hasGlobalStatus(GlobalApprovalStatus.DISPUTE)))
              .filter(
                  hasLocalStatus(institutionId, ApprovalStatus.APPROVED)
                      .or(hasLocalStatus(institutionId, ApprovalStatus.REJECTED)))
              .count();

      var totals = report.institutionSummary().totals();
      assertThat(totals.undisputedProcessedCount()).isEqualTo(expectedCount);
    }

    @ParameterizedTest
    @MethodSource("institutionsWithDocumentsForThisYear")
    void shouldHaveExpectedUndisputedNewCount(
        String institutionIdentifier, List<NviCandidateIndexDocument> relevantDocs) {
      var report = getInstitutionReport(THIS_YEAR, institutionIdentifier);
      var institutionId = report.institution().id();
      var expectedCount =
          relevantDocs.stream()
              .filter(not(hasGlobalStatus(GlobalApprovalStatus.DISPUTE)))
              .filter(hasLocalStatus(institutionId, ApprovalStatus.NEW))
              .count();

      var candidatesByStatus = report.institutionSummary().byLocalApprovalStatus();
      assertThat(candidatesByStatus.newCount()).isEqualTo(expectedCount);
    }

    @ParameterizedTest
    @MethodSource("institutionsWithDocumentsForThisYear")
    void shouldHaveExpectedUndisputedPendingCount(
        String institutionIdentifier, List<NviCandidateIndexDocument> relevantDocs) {
      var report = getInstitutionReport(THIS_YEAR, institutionIdentifier);
      var institutionId = report.institution().id();
      var expectedCount =
          relevantDocs.stream()
              .filter(not(hasGlobalStatus(GlobalApprovalStatus.DISPUTE)))
              .filter(hasLocalStatus(institutionId, ApprovalStatus.PENDING))
              .count();

      var candidatesByStatus = report.institutionSummary().byLocalApprovalStatus();
      assertThat(candidatesByStatus.pendingCount()).isEqualTo(expectedCount);
    }

    @ParameterizedTest
    @MethodSource("institutionsWithDocumentsForThisYear")
    void shouldHaveExpectedUndisputedApprovedCount(
        String institutionIdentifier, List<NviCandidateIndexDocument> relevantDocs) {
      var report = getInstitutionReport(THIS_YEAR, institutionIdentifier);
      var institutionId = report.institution().id();
      var expectedCount =
          relevantDocs.stream()
              .filter(not(hasGlobalStatus(GlobalApprovalStatus.DISPUTE)))
              .filter(hasLocalStatus(institutionId, ApprovalStatus.APPROVED))
              .count();

      var candidatesByStatus = report.institutionSummary().byLocalApprovalStatus();
      assertThat(candidatesByStatus.approvedCount()).isEqualTo(expectedCount);
    }

    @ParameterizedTest
    @MethodSource("institutionsWithDocumentsForThisYear")
    void shouldHaveExpectedUndisputedRejectedCount(
        String institutionIdentifier, List<NviCandidateIndexDocument> relevantDocs) {
      var report = getInstitutionReport(THIS_YEAR, institutionIdentifier);
      var institutionId = report.institution().id();
      var expectedCount =
          relevantDocs.stream()
              .filter(not(hasGlobalStatus(GlobalApprovalStatus.DISPUTE)))
              .filter(hasLocalStatus(institutionId, ApprovalStatus.REJECTED))
              .count();

      var candidatesByStatus = report.institutionSummary().byLocalApprovalStatus();
      assertThat(candidatesByStatus.rejectedCount()).isEqualTo(expectedCount);
    }
  }
}
