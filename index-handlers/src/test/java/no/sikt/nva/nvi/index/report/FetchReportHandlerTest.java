package no.sikt.nva.nvi.index.report;

import static java.util.Collections.emptyMap;
import static no.sikt.nva.nvi.common.EnvironmentFixtures.ALLOWED_ORIGIN;
import static no.sikt.nva.nvi.common.EnvironmentFixtures.getHandlerEnvironment;
import static no.sikt.nva.nvi.common.db.PeriodRepositoryFixtures.setupClosedPeriod;
import static no.sikt.nva.nvi.common.db.PeriodRepositoryFixtures.setupFuturePeriod;
import static no.sikt.nva.nvi.common.db.PeriodRepositoryFixtures.setupOpenPeriod;
import static no.sikt.nva.nvi.index.IndexDocumentFixtures.documentForYear;
import static no.sikt.nva.nvi.index.IndexDocumentFixtures.randomApproval;
import static no.sikt.nva.nvi.index.report.ReportConstants.INSTITUTIONS_PATH_SEGMENT;
import static no.sikt.nva.nvi.index.report.ReportConstants.PERIOD_PATH_PARAM;
import static no.sikt.nva.nvi.index.report.ReportConstants.REPORTS_PATH_SEGMENT;
import static no.sikt.nva.nvi.test.TestConstants.LAST_YEAR;
import static no.sikt.nva.nvi.test.TestConstants.NEXT_YEAR;
import static no.sikt.nva.nvi.test.TestConstants.THIS_YEAR;
import static no.sikt.nva.nvi.test.TestUtils.CURRENT_YEAR;
import static no.unit.nva.commons.json.JsonUtils.dtoObjectMapper;
import static no.unit.nva.testutils.RandomDataGenerator.randomUri;
import static nva.commons.apigateway.GatewayResponse.fromOutputStream;
import static org.assertj.core.api.Assertions.assertThat;

import com.amazonaws.services.lambda.runtime.Context;
import com.fasterxml.jackson.core.JsonProcessingException;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.Map;
import java.util.stream.IntStream;
import java.util.stream.Stream;
import no.sikt.nva.nvi.common.TestScenario;
import no.sikt.nva.nvi.index.IndexDocumentFixtures;
import no.sikt.nva.nvi.index.OpenSearchContainerContext;
import no.sikt.nva.nvi.index.report.response.AllInstitutionsReport;
import no.sikt.nva.nvi.index.report.response.AllPeriodsReport;
import no.sikt.nva.nvi.index.report.response.FakeReportUploader;
import no.sikt.nva.nvi.index.report.response.ReportResponse;
import no.unit.nva.stubs.FakeContext;
import no.unit.nva.testutils.HandlerRequestBuilder;
import nva.commons.apigateway.AccessRight;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

class FetchReportHandlerTest {

  private static final Context CONTEXT = new FakeContext();
  private static final OpenSearchContainerContext CONTAINER = new OpenSearchContainerContext();
  private FetchReportHandler handler;
  private TestScenario scenario;

  @BeforeAll
  static void setup() {
    CONTAINER.start();
  }

  @BeforeEach
  void beforeEach() {
    CONTAINER.createIndex();
    scenario = new TestScenario();
    setupClosedPeriod(scenario, LAST_YEAR);
    setupOpenPeriod(scenario, THIS_YEAR);
    setupFuturePeriod(scenario, NEXT_YEAR);
    handler =
        new FetchReportHandler(
            getHandlerEnvironment(ALLOWED_ORIGIN),
            scenario.getPeriodService(),
            CONTAINER.getReportAggregationClient(),
            new FakeReportUploader());
  }

  @AfterEach
  void afterEach() {
    CONTAINER.deleteIndex();
  }

  @AfterAll
  static void cleanup() {
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

  private static InputStream createAllPeriodsRequest() {
    return createRequest(emptyMap(), REPORTS_PATH_SEGMENT);
  }

  private static InputStream createAllInstitutionsRequest(String period) {
    var pathParams = Map.of(PERIOD_PATH_PARAM, period);
    var path = "%s/%s/%s".formatted(REPORTS_PATH_SEGMENT, period, INSTITUTIONS_PATH_SEGMENT);
    return createRequest(pathParams, path);
  }

  private AllPeriodsReport getAllPeriodsReport() {
    var request = createAllPeriodsRequest();
    return (AllPeriodsReport) handleRequest(request);
  }

  private AllInstitutionsReport getAllInstitutionsReport(String period) {
    var request = createAllInstitutionsRequest(period);
    return (AllInstitutionsReport) handleRequest(request);
  }

  @Nested
  class AllPeriodsReportTests {

    @Test
    void shouldContainListOfAllPeriodReports() {
      var numberOfClosedPeriods = 25;
      var reportingYears =
          IntStream.rangeClosed(CURRENT_YEAR - numberOfClosedPeriods, CURRENT_YEAR - 1)
              .mapToObj(Integer::toString)
              .toList();
      for (var year : reportingYears) {
        setupClosedPeriod(scenario, year);
      }
      var documents =
          reportingYears.stream()
              .map(year -> documentForYear(year, true, randomApproval()))
              .toList();
      CONTAINER.addDocumentsToIndex(documents);

      var allPeriodsReport = getAllPeriodsReport();

      assertThat(allPeriodsReport.periods()).hasSize(numberOfClosedPeriods + 2);
    }
  }

  @Nested
  class AllInstitutionsReportTests {

    @Test
    void shouldIncludeAllInstitutionsWithCandidates() {
      var numberOfInstitutions = 100;
      var documents =
          Stream.generate(IndexDocumentFixtures::randomApproval)
              .limit(numberOfInstitutions)
              .map(IndexDocumentFixtures::documentWithApprovals)
              .toList();
      CONTAINER.addDocumentsToIndex(documents);

      var report = getAllInstitutionsReport(THIS_YEAR);

      assertThat(report.institutions()).hasSize(numberOfInstitutions);
    }
  }
}
