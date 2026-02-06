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
import java.util.Map;
import no.unit.nva.stubs.FakeContext;
import no.unit.nva.testutils.HandlerRequestBuilder;
import nva.commons.apigateway.GatewayResponse;
import nva.commons.core.Environment;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.zalando.problem.Problem;

class FetchReportHandlerTest {

  private static final Context CONTEXT = new FakeContext();
  public static final String PERIOD = "period";
  public static final String INSTITUTION = "institution";
  private FetchReportHandler handler;
  private ByteArrayOutputStream output;

  @BeforeEach
  void setUp() {
    output = new ByteArrayOutputStream();
    handler = new FetchReportHandler(new Environment());
  }

  @Test
  void shouldReturnOkOnSuccess() throws IOException {
    handler.handleRequest(createRequest(Map.of()), output, CONTEXT);

    var response = fromOutputStream(output, Problem.class);

    assertEquals(HttpURLConnection.HTTP_OK, response.getStatusCode());
  }

  @Test
  void shouldReturnPeriodReportWhenNoPathParametersAreProvided() throws IOException {
    var request = createRequest(Map.of());
    handler.handleRequest(request, output, CONTEXT);

    var response = GatewayResponse.fromOutputStream(output, ReportResponse.class);

    assertInstanceOf(PeriodReport.class, response.getBodyObject(ReportResponse.class));
  }

  @Test
  void shouldReturnAllInstitutionsReportWhenPeriodIsProvidedInPathParameters() throws IOException {
    var request = createRequest(Map.of(PERIOD, randomString()));
    handler.handleRequest(request, output, CONTEXT);

    var response = GatewayResponse.fromOutputStream(output, ReportResponse.class);

    assertInstanceOf(AllInstitutionsReport.class, response.getBodyObject(ReportResponse.class));
  }

  @Test
  void shouldReturnInstitutionsReportWhenPeriodAndInstitutionAreProvidedInPathParameters()
      throws IOException {
    var request = createRequest(Map.of(PERIOD, randomString(), INSTITUTION, randomString()));
    handler.handleRequest(request, output, CONTEXT);

    var response = GatewayResponse.fromOutputStream(output, ReportResponse.class);

    assertInstanceOf(InstitutionReport.class, response.getBodyObject(ReportResponse.class));
  }

  private static InputStream createRequest(Map<String, String> pathParameters)
      throws JsonProcessingException {
    return new HandlerRequestBuilder<InputStream>(dtoObjectMapper)
        .withPathParameters(pathParameters)
        .build();
  }
}
