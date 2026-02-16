package no.sikt.nva.nvi.index.apigateway;

import static java.util.Collections.emptyMap;
import static no.sikt.nva.nvi.common.model.OrganizationFixtures.randomOrganizationIdentifier;
import static no.sikt.nva.nvi.test.TestUtils.randomYear;
import static no.unit.nva.commons.json.JsonUtils.dtoObjectMapper;
import static no.unit.nva.testutils.RandomDataGenerator.randomUri;
import static nva.commons.apigateway.GatewayResponse.fromOutputStream;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;

import com.amazonaws.services.lambda.runtime.Context;
import com.fasterxml.jackson.core.JsonProcessingException;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.util.Map;
import no.sikt.nva.nvi.index.model.report.AllInstitutionsReport;
import no.sikt.nva.nvi.index.model.report.AllPeriodsReport;
import no.sikt.nva.nvi.index.model.report.InstitutionReport;
import no.sikt.nva.nvi.index.model.report.PeriodReport;
import no.sikt.nva.nvi.index.model.report.ReportResponse;
import no.unit.nva.stubs.FakeContext;
import no.unit.nva.testutils.HandlerRequestBuilder;
import nva.commons.apigateway.AccessRight;
import nva.commons.core.Environment;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.zalando.problem.Problem;

class FetchReportHandlerTest {

  private static final String PERIOD = "period";
  private static final String INSTITUTION = "institution";
  private static final Context CONTEXT = new FakeContext();
  private static final String PERIOD_FOR_QUERY = randomYear();
  private static final String REPORTS_PATH = "reports";
  private static final String REPORTS_INSTITUTIONS_PATH =
      "reports/%s/institutions".formatted(PERIOD_FOR_QUERY);
  private static final String PATH = "path";
  private FetchReportHandler handler;
  private ByteArrayOutputStream output;

  @BeforeEach
  void setUp() {
    output = new ByteArrayOutputStream();
    handler = new FetchReportHandler(new Environment());
  }

  @Test
  void shouldReturnOkOnSuccess() throws IOException {
    handler.handleRequest(createRequest(emptyMap(), REPORTS_PATH), output, CONTEXT);

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
    var request = createRequest(emptyMap(), REPORTS_PATH);

    var response = handleRequest(request);

    assertInstanceOf(AllPeriodsReport.class, response);
  }

  @Test
  void shouldReturnPeriodReportWhenPeriodIsProvidedInPathParameters() {
    var request = createRequest(Map.of(PERIOD, PERIOD_FOR_QUERY), REPORTS_PATH);

    var response = handleRequest(request);

    assertInstanceOf(PeriodReport.class, response);
  }

  @Test
  void
      shouldReturnAllInstitutionsReportWhenPeriodIsProvidedInPathParametersAndInstitutionIsPresentInPathParameters() {
    var request = createRequest(Map.of(PERIOD, PERIOD_FOR_QUERY), REPORTS_INSTITUTIONS_PATH);

    var response = handleRequest(request);

    assertInstanceOf(AllInstitutionsReport.class, response);
  }

  @Test
  void shouldReturnInstitutionsReportWhenPeriodAndInstitutionAreProvidedInPathParameters() {
    var request =
        createRequest(
            Map.of(PERIOD, PERIOD_FOR_QUERY, INSTITUTION, randomOrganizationIdentifier()),
            REPORTS_INSTITUTIONS_PATH);

    var response = handleRequest(request);

    assertInstanceOf(InstitutionReport.class, response);
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
      return response.getBodyObject(ReportResponse.class);
    } catch (IOException e) {
      throw new RuntimeException(e);
    }
  }
}
