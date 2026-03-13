package no.sikt.nva.nvi.index.report;

import static java.net.HttpURLConnection.HTTP_OK;
import static no.sikt.nva.nvi.common.utils.RequestUtil.isNviAdmin;
import static no.sikt.nva.nvi.common.utils.RequestUtil.isNviCurator;
import static nva.commons.apigateway.MediaType.JSON_UTF_8;
import static nva.commons.apigateway.MediaType.OOXML_SHEET;
import static org.apache.http.HttpHeaders.ACCEPT;
import static org.apache.http.HttpHeaders.CONTENT_TYPE;

import com.amazonaws.services.lambda.runtime.Context;
import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;
import no.sikt.nva.nvi.common.service.NviPeriodService;
import no.sikt.nva.nvi.common.service.exception.PeriodNotFoundException;
import no.sikt.nva.nvi.index.report.request.AllInstitutionsReportRequest;
import no.sikt.nva.nvi.index.report.request.InstitutionReportRequest;
import no.sikt.nva.nvi.index.report.request.ReportRequest;
import no.sikt.nva.nvi.index.report.request.ReportRequestFactory;
import no.sikt.nva.nvi.index.report.response.ReportResponse;
import no.sikt.nva.nvi.index.report.response.ReportService;
import nva.commons.apigateway.ApiGatewayHandler;
import nva.commons.apigateway.MediaType;
import nva.commons.apigateway.RequestInfo;
import nva.commons.apigateway.exceptions.ApiGatewayException;
import nva.commons.apigateway.exceptions.BadGatewayException;
import nva.commons.apigateway.exceptions.BadRequestException;
import nva.commons.apigateway.exceptions.ForbiddenException;
import nva.commons.apigateway.exceptions.NotFoundException;
import nva.commons.core.Environment;
import nva.commons.core.JacocoGenerated;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class FetchReportHandler extends ApiGatewayHandler<Void, ReportResponse> {

  private static final Logger LOGGER = LoggerFactory.getLogger(FetchReportHandler.class);
  private final ReportService reportService;

  @JacocoGenerated
  public FetchReportHandler() {
    this(
        new Environment(),
        NviPeriodService.defaultNviPeriodService(),
        ReportAggregationClient.defaultClient());
  }

  public FetchReportHandler(
      Environment environment,
      NviPeriodService nviPeriodService,
      ReportAggregationClient reportAggregationClient) {
    super(Void.class, environment);
    this.reportService = new ReportService(nviPeriodService, reportAggregationClient);
  }

  @Override
  protected List<MediaType> listSupportedMediaTypes() {
    return List.of(JSON_UTF_8, OOXML_SHEET);
  }

  @Override
  protected void validateRequest(Void unused, RequestInfo requestInfo, Context context)
      throws ApiGatewayException {
    if (!(isNviAdmin(requestInfo) || isNviCurator(requestInfo))) {
      throw new ForbiddenException();
    }
  }

  @JacocoGenerated
  @Override
  protected ReportResponse processInput(Void unused, RequestInfo requestInfo, Context context)
      throws ApiGatewayException {
    var reportRequest = ReportRequestFactory.getRequest(requestInfo, environment);
    try {
      addAdditionalHeaders(requestInfo, reportRequest);

      return reportService.getResponse(reportRequest);
    } catch (NoSuchElementException | PeriodNotFoundException exception) {
      LOGGER.error("Resource not found for query request: {}", reportRequest, exception);
      throw new NotFoundException(exception.getMessage());
    } catch (IOException exception) {
      LOGGER.error("Failed to execute query request: {}", reportRequest, exception);
      throw new BadGatewayException("Something went wrong! Contact application administrator.");
    }
  }

  private void addAdditionalHeaders(RequestInfo requestInfo, ReportRequest reportRequest)
      throws BadRequestException {
    if (reportRequest instanceof InstitutionReportRequest request
        && request.isXlsxReportRequest()) {
      addAdditionalHeaders(() -> Map.of(CONTENT_TYPE, OOXML_SHEET.toString()));
    } else if (reportRequest instanceof AllInstitutionsReportRequest request
        && request.isXlsxReportRequest()) {
      addAdditionalHeaders(() -> Map.of(CONTENT_TYPE, OOXML_SHEET.toString()));
    } else if (hasXlsxAcceptHeader(requestInfo)) {
      throw new BadRequestException("XLSX is not supported for that type of report!");
    }
  }

  private static boolean hasXlsxAcceptHeader(RequestInfo requestInfo) {
    return requestInfo
        .getHeaderOptional(ACCEPT)
        .filter(value -> value.equals(OOXML_SHEET.toString()))
        .isPresent();
  }

  @Override
  protected Integer getSuccessStatusCode(Void unused, ReportResponse o) {
    return HTTP_OK;
  }
}
