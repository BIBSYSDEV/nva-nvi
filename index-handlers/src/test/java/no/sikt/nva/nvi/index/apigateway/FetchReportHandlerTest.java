package no.sikt.nva.nvi.index.apigateway;

import static com.google.common.net.MediaType.JSON_UTF_8;
import static java.net.HttpURLConnection.HTTP_OK;
import static java.util.Collections.emptyMap;
import static no.sikt.nva.nvi.common.EnvironmentFixtures.ALLOWED_ORIGIN;
import static no.sikt.nva.nvi.common.EnvironmentFixtures.getHandlerEnvironment;
import static no.sikt.nva.nvi.index.report.ReportConstants.INSTITUTIONS_PATH_SEGMENT;
import static no.sikt.nva.nvi.index.report.ReportConstants.INSTITUTION_PATH_PARAM;
import static no.sikt.nva.nvi.index.report.ReportConstants.PERIOD_PATH_PARAM;
import static no.sikt.nva.nvi.index.report.ReportConstants.REPORTS_PATH_SEGMENT;
import static no.sikt.nva.nvi.test.TestUtils.randomYear;
import static no.unit.nva.commons.json.JsonUtils.dtoObjectMapper;
import static no.unit.nva.testutils.RandomDataGenerator.randomString;
import static no.unit.nva.testutils.RandomDataGenerator.randomUri;
import static nva.commons.apigateway.GatewayResponse.fromOutputStream;
import static nva.commons.core.StringUtils.EMPTY_STRING;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static software.amazon.awssdk.http.Header.ACCEPT;
import static software.amazon.awssdk.http.HttpStatusCode.BAD_REQUEST;

import com.amazonaws.services.lambda.runtime.Context;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.google.common.net.HttpHeaders;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.util.List;
import java.util.Map;
import no.sikt.nva.nvi.common.service.NviPeriodService;
import no.sikt.nva.nvi.index.report.FetchReportHandler;
import no.sikt.nva.nvi.index.report.ReportAggregationClient;
import no.sikt.nva.nvi.index.report.query.AllPeriodsQuery;
import no.sikt.nva.nvi.index.report.response.AllPeriodsReport;
import no.sikt.nva.nvi.index.report.response.FakeReportUploader;
import no.sikt.nva.nvi.index.report.response.ReportResponse;
import no.unit.nva.stubs.FakeContext;
import no.unit.nva.testutils.HandlerRequestBuilder;
import nva.commons.apigateway.AccessRight;
import nva.commons.apigateway.MediaType;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.zalando.problem.Problem;

class FetchReportHandlerTest {

  private static final Context CONTEXT = new FakeContext();
  private static final String PATH = "path";
  private static final String CSV_UTF_8 = MediaType.CSV_UTF_8.toString();
  private static final String OOXML_SHEET = MediaType.OOXML_SHEET.toString();
  private FetchReportHandler handler;
  private ByteArrayOutputStream output;

  @BeforeEach
  void setUp() throws IOException {
    output = new ByteArrayOutputStream();
    var mockClient = mock(ReportAggregationClient.class);
    when(mockClient.executeQuery(any(AllPeriodsQuery.class))).thenReturn(List.of());
    handler =
        new FetchReportHandler(
            getHandlerEnvironment(ALLOWED_ORIGIN),
            mock(NviPeriodService.class),
            mockClient,
            new FakeReportUploader());
  }

  @ParameterizedTest
  @EnumSource(
      value = AccessRight.class,
      names = {"MANAGE_NVI_CANDIDATES", "MANAGE_NVI"},
      mode = EnumSource.Mode.INCLUDE)
  void shouldReturnOkWhenUserHasNviAccessRight(AccessRight accessRight) throws IOException {
    handler.handleRequest(
        createRequest(emptyMap(), REPORTS_PATH_SEGMENT, emptyMap(), accessRight), output, CONTEXT);

    var statusCode = fromOutputStream(output, ReportResponse.class).getStatusCode();

    assertEquals(HTTP_OK, statusCode);
  }

  @ParameterizedTest
  @EnumSource(
      value = AccessRight.class,
      names = {"MANAGE_NVI_CANDIDATES", "MANAGE_NVI"},
      mode = EnumSource.Mode.EXCLUDE)
  void shouldReturnForbiddenWhenUserDoesNotHaveNviAccessRight(AccessRight accessRight)
      throws IOException {
    handler.handleRequest(createRequestWithAccessRight(accessRight), output, CONTEXT);

    var statusCode = fromOutputStream(output, Problem.class).getStatusCode();

    assertEquals(HttpURLConnection.HTTP_FORBIDDEN, statusCode);
  }

  @Test
  void shouldReturnForbiddenWhenUserHasNoAccessRights() throws IOException {
    handler.handleRequest(requestWithoutAccessRights(), output, CONTEXT);

    var statusCode = fromOutputStream(output, Problem.class).getStatusCode();

    assertEquals(HttpURLConnection.HTTP_FORBIDDEN, statusCode);
  }

  @Test
  void shouldReturnAllPeriodsReportWhenNoPathParametersAreProvided() {
    var request =
        createRequest(emptyMap(), REPORTS_PATH_SEGMENT, emptyMap(), AccessRight.MANAGE_NVI);

    var response = handleRequest(request);

    assertInstanceOf(AllPeriodsReport.class, response);
  }

  @Test
  void shouldReturnReportMediaTypeJsonWhenXlsxRequestedForInstitutionReport() throws IOException {
    var headers = Map.of(ACCEPT, OOXML_SHEET);
    var pathParams =
        Map.of(PERIOD_PATH_PARAM, randomYear(), INSTITUTION_PATH_PARAM, randomString());
    var path =
        "%s/%s/%s/%s"
            .formatted(
                REPORTS_PATH_SEGMENT, randomYear(), INSTITUTIONS_PATH_SEGMENT, randomString());
    var request = createRequestWithHeader(pathParams, path, headers);

    handler.handleRequest(request, output, CONTEXT);

    var response = fromOutputStream(output, ReportResponse.class);

    assertThat(
        response.getHeaders().get(HttpHeaders.CONTENT_TYPE), is(MediaType.JSON_UTF_8.toString()));
  }

  @Test
  void shouldReturnReportMediaTypeJsonWhenCsvRequestedForInstitutionReport() throws IOException {
    var headers = Map.of(ACCEPT, CSV_UTF_8);
    var pathParams =
        Map.of(PERIOD_PATH_PARAM, randomYear(), INSTITUTION_PATH_PARAM, randomString());
    var path =
        "%s/%s/%s/%s"
            .formatted(
                REPORTS_PATH_SEGMENT, randomYear(), INSTITUTIONS_PATH_SEGMENT, randomString());
    var request = createRequestWithHeader(pathParams, path, headers);

    handler.handleRequest(request, output, CONTEXT);

    var response = fromOutputStream(output, ReportResponse.class);

    assertThat(
        response.getHeaders().get(HttpHeaders.CONTENT_TYPE), is(MediaType.JSON_UTF_8.toString()));
  }

  @Test
  void shouldReturnReportMediaTypeJsonWhenXlsxRequestedForAllInstitutionsReport()
      throws IOException {
    var headers = Map.of(ACCEPT, CSV_UTF_8);
    var pathParams = Map.of(PERIOD_PATH_PARAM, randomYear());
    var path = "%s/%s/%s".formatted(REPORTS_PATH_SEGMENT, randomYear(), INSTITUTIONS_PATH_SEGMENT);
    var request = createRequestWithHeader(pathParams, path, headers);

    handler.handleRequest(request, output, CONTEXT);

    var response = fromOutputStream(output, ReportResponse.class);

    assertThat(
        response.getHeaders().get(HttpHeaders.CONTENT_TYPE), is(MediaType.JSON_UTF_8.toString()));
  }

  @Test
  void shouldReturnBadRequestWhenOpenXmlOfficeReportRequestedForPeriodReport() throws IOException {
    var headers = Map.of(ACCEPT, OOXML_SHEET);
    var pathParams = Map.of(PERIOD_PATH_PARAM, randomYear());
    var path = "%s/%s".formatted(REPORTS_PATH_SEGMENT, randomYear());
    var request = createRequestWithHeader(pathParams, path, headers);

    handler.handleRequest(request, output, CONTEXT);

    var response = fromOutputStream(output, Problem.class);

    assertEquals(BAD_REQUEST, response.getStatusCode());
  }

  @Test
  void shouldReturnBadRequestWhenOpenXmlOfficeReportRequestedFoAllPeriodsReport()
      throws IOException {
    var headers = Map.of(ACCEPT, OOXML_SHEET);
    var request = createRequestWithHeader(Map.of(), REPORTS_PATH_SEGMENT, headers);

    handler.handleRequest(request, output, CONTEXT);

    var response = fromOutputStream(output, Problem.class);

    assertEquals(BAD_REQUEST, response.getStatusCode());
  }

  @Test
  void shouldReturnBadRequestWhenCsvReportRequestedFoAllPeriodsReport() throws IOException {
    var headers = Map.of(ACCEPT, CSV_UTF_8);
    var request = createRequestWithHeader(Map.of(), REPORTS_PATH_SEGMENT, headers);

    handler.handleRequest(request, output, CONTEXT);

    var response = fromOutputStream(output, Problem.class);

    assertEquals(BAD_REQUEST, response.getStatusCode());
  }

  @Test
  void shouldReturnOkWhenRequestedNonInstitutionReportWithAcceptHeaderJson() throws IOException {
    var headers = Map.of(ACCEPT, JSON_UTF_8.toString());
    var request = createRequestWithHeader(Map.of(), EMPTY_STRING, headers);

    handler.handleRequest(request, output, CONTEXT);

    var response = fromOutputStream(output, Void.class);

    assertEquals(HTTP_OK, response.getStatusCode());
  }

  private static InputStream createRequest(
      Map<String, String> pathParameters,
      String path,
      Map<String, String> headers,
      AccessRight... accessRights) {
    try {
      return new HandlerRequestBuilder<InputStream>(dtoObjectMapper)
          .withOtherProperties(Map.of(PATH, path))
          .withPathParameters(pathParameters)
          .withAccessRights(randomUri(), accessRights)
          .withHeaders(headers)
          .build();
    } catch (JsonProcessingException e) {
      throw new RuntimeException(e);
    }
  }

  private static InputStream createRequestWithAccessRight(AccessRight... accessRights) {
    return createRequest(
        Map.of(PATH, REPORTS_PATH_SEGMENT), EMPTY_STRING, emptyMap(), accessRights);
  }

  private static InputStream requestWithoutAccessRights() {
    return createRequest(Map.of(PATH, REPORTS_PATH_SEGMENT), EMPTY_STRING, emptyMap());
  }

  private static InputStream createRequestWithHeader(
      Map<String, String> pathParams, String path, Map<String, String> headers) {
    return createRequest(pathParams, path, headers, AccessRight.MANAGE_NVI);
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
