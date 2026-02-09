package no.sikt.nva.nvi.index.apigateway;

import static no.unit.nva.commons.json.JsonUtils.dtoObjectMapper;
import static no.unit.nva.testutils.RandomDataGenerator.randomString;
import static nva.commons.apigateway.GatewayResponse.fromOutputStream;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;

import com.amazonaws.services.lambda.runtime.Context;
import com.fasterxml.jackson.core.JsonProcessingException;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.util.Collections;
import java.util.Map;
import no.sikt.nva.nvi.index.model.report.AllInstitutionsReport;
import no.sikt.nva.nvi.index.model.report.AllPeriodsReport;
import no.sikt.nva.nvi.index.model.report.InstitutionReport;
import no.sikt.nva.nvi.index.model.report.PeriodReport;
import no.sikt.nva.nvi.index.model.report.ReportResponse;
import no.unit.nva.stubs.FakeContext;
import no.unit.nva.testutils.HandlerRequestBuilder;
import nva.commons.core.Environment;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class FetchReportHandlerTest {

  private static final String PERIOD = "period";
  private static final String INSTITUTION = "institution";
  private static final Context CONTEXT = new FakeContext();
  private static final String REPORTS_PATH = "reports";
  private static final String REPORTS_INSTITUTIONS_PATH =
      "reports/%s/institutions".formatted(randomString());
  public static final String PATH = "path";
  private FetchReportHandler handler;
  private ByteArrayOutputStream output;

  @BeforeEach
  void setUp() {
    output = new ByteArrayOutputStream();
    handler = new FetchReportHandler(new Environment());
  }

  @Test
  void shouldReturnOkOnSuccess() throws IOException {
    handler.handleRequest(createRequest(Collections.emptyMap(), REPORTS_PATH), output, CONTEXT);

    var statusCode = fromOutputStream(output, ReportResponse.class).getStatusCode();

    assertEquals(HttpURLConnection.HTTP_OK, statusCode);
  }

  @Test
  void shouldReturnAllPeriodReportWhenNoPathParametersAreProvided() {
    var request = createRequest(Map.of(), REPORTS_PATH);

    var response = handleRequest(request);

    assertInstanceOf(AllPeriodsReport.class, response);
  }

  @Test
  void shouldReturnPeriodReportWhenPeriodIsProvidedInPathParameters() {
    var request = createRequest(Map.of(PERIOD, randomString()), REPORTS_PATH);

    var response = handleRequest(request);

    assertInstanceOf(PeriodReport.class, response);
  }

  @Test
  void
      shouldReturnAllInstitutionsReportWhenPeriodIsProvidedInPathParametersAndInstitutionIsPresentInPathParameters() {
    var request = createRequest(Map.of(PERIOD, randomString()), REPORTS_INSTITUTIONS_PATH);

    var response = handleRequest(request);

    assertInstanceOf(AllInstitutionsReport.class, response);
  }

  @Test
  void shouldReturnInstitutionsReportWhenPeriodAndInstitutionAreProvidedInPathParameters() {
    var request =
        createRequest(
            Map.of(PERIOD, randomString(), INSTITUTION, randomString()), REPORTS_INSTITUTIONS_PATH);

    var response = handleRequest(request);

    assertInstanceOf(InstitutionReport.class, response);
  }

  private static InputStream createRequest(Map<String, String> pathParameters, String path) {
    try {
      return new HandlerRequestBuilder<InputStream>(dtoObjectMapper)
          .withOtherProperties(Map.of(PATH, path))
          .withPathParameters(pathParameters)
          .build();
    } catch (JsonProcessingException e) {
      throw new RuntimeException(e);
    }
  }

  private ReportResponse handleRequest(InputStream request) {
    try {
      handler.handleRequest(request, output, CONTEXT);
      var response = fromOutputStream(output, ReportResponse.class);
      return response.getBodyObject(ReportResponse.class);
    } catch (IOException e) {
      throw new RuntimeException(e);
    }
  }
}
