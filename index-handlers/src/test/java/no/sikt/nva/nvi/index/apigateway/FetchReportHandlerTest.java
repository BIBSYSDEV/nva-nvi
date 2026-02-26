package no.sikt.nva.nvi.index.apigateway;

import static java.util.Collections.emptyMap;
import static no.sikt.nva.nvi.common.EnvironmentFixtures.ALLOWED_ORIGIN;
import static no.sikt.nva.nvi.common.EnvironmentFixtures.getHandlerEnvironment;
import static no.sikt.nva.nvi.common.db.PeriodRepositoryFixtures.setupOpenPeriod;
import static no.sikt.nva.nvi.index.report.ReportConstants.PERIOD_PATH_PARAM;
import static no.sikt.nva.nvi.index.report.ReportConstants.REPORTS_PATH_SEGMENT;
import static no.sikt.nva.nvi.test.TestUtils.randomYear;
import static no.unit.nva.commons.json.JsonUtils.dtoObjectMapper;
import static no.unit.nva.testutils.RandomDataGenerator.randomUri;
import static nva.commons.apigateway.GatewayResponse.fromOutputStream;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.mockito.Mockito.mock;

import com.amazonaws.services.lambda.runtime.Context;
import com.fasterxml.jackson.core.JsonProcessingException;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.util.Map;
import no.sikt.nva.nvi.common.TestScenario;
import no.sikt.nva.nvi.index.report.FetchReportHandler;
import no.sikt.nva.nvi.index.report.ReportAggregationClient;
import no.sikt.nva.nvi.index.report.response.AllPeriodsReport;
import no.sikt.nva.nvi.index.report.response.PeriodReport;
import no.sikt.nva.nvi.index.report.response.ReportResponse;
import no.unit.nva.stubs.FakeContext;
import no.unit.nva.testutils.HandlerRequestBuilder;
import nva.commons.apigateway.AccessRight;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.zalando.problem.Problem;

class FetchReportHandlerTest {

  private static final Context CONTEXT = new FakeContext();
  private static final String PERIOD_FOR_QUERY = randomYear();
  private static final String PATH = "path";
  private ReportAggregationClient mockAggregationClient;
  private FetchReportHandler handler;
  private ByteArrayOutputStream output;

  @BeforeEach
  void setUp() {
    var scenario = new TestScenario();
    setupOpenPeriod(scenario, PERIOD_FOR_QUERY);
    output = new ByteArrayOutputStream();
    mockAggregationClient = mock(ReportAggregationClient.class);
    handler =
        new FetchReportHandler(
            getHandlerEnvironment(ALLOWED_ORIGIN),
            scenario.getPeriodService(),
            mockAggregationClient);
  }

  @Test
  void shouldReturnOkOnSuccess() throws IOException {
    handler.handleRequest(createRequest(emptyMap(), REPORTS_PATH_SEGMENT), output, CONTEXT);

    var statusCode = fromOutputStream(output, ReportResponse.class).getStatusCode();

    assertEquals(HttpURLConnection.HTTP_OK, statusCode);
  }

  @Test
  void shouldThrowForbiddenWhenNonNviAdminMakesRequest() throws IOException {
    handler.handleRequest(requestWithoutAccessRights(), output, CONTEXT);

    var statusCode = fromOutputStream(output, Problem.class).getStatusCode();

    assertEquals(HttpURLConnection.HTTP_FORBIDDEN, statusCode);
  }

  @Test
  void shouldReturnAllPeriodsReportWhenNoPathParametersAreProvided() {
    var request = createRequest(emptyMap(), REPORTS_PATH_SEGMENT);

    var response = handleRequest(request);

    assertInstanceOf(AllPeriodsReport.class, response);
  }

  @Test
  void shouldReturnPeriodReportWhenPeriodIsProvidedInPathParameters() {
    var request = createRequest(Map.of(PERIOD_PATH_PARAM, PERIOD_FOR_QUERY), REPORTS_PATH_SEGMENT);

    var response = handleRequest(request);

    assertInstanceOf(PeriodReport.class, response);
  }

  private static InputStream createRequest(Map<String, String> pathParameters, String path) {
    try {
      return new HandlerRequestBuilder<InputStream>(dtoObjectMapper)
          .withOtherProperties(Map.of(PATH, path))
          .withPathParameters(pathParameters)
          .withAccessRights(randomUri(), AccessRight.MANAGE_NVI)
          .build();
    } catch (JsonProcessingException e) {
      throw new RuntimeException(e);
    }
  }

  private static InputStream requestWithoutAccessRights() {
    try {
      return new HandlerRequestBuilder<InputStream>(dtoObjectMapper).build();
    } catch (JsonProcessingException e) {
      throw new RuntimeException(e);
    }
  }

  private ReportResponse handleRequest(InputStream request) {
    try {
      handler.handleRequest(request, output, CONTEXT);
      var response = fromOutputStream(output, ReportResponse.class);
      output.reset();
      return response.getBodyObject(ReportResponse.class);
    } catch (IOException e) {
      throw new RuntimeException(e);
    }
  }
}
