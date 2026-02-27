package no.sikt.nva.nvi.index.report;

import static java.net.HttpURLConnection.HTTP_OK;

import com.amazonaws.services.lambda.runtime.Context;
import java.io.IOException;
import java.util.NoSuchElementException;
import no.sikt.nva.nvi.common.service.NviPeriodService;
import no.sikt.nva.nvi.index.report.request.ReportRequestFactory;
import no.sikt.nva.nvi.index.report.response.ReportResponse;
import no.sikt.nva.nvi.index.report.response.ReportResponseFactory;
import nva.commons.apigateway.AccessRight;
import nva.commons.apigateway.ApiGatewayHandler;
import nva.commons.apigateway.RequestInfo;
import nva.commons.apigateway.exceptions.ApiGatewayException;
import nva.commons.apigateway.exceptions.BadGatewayException;
import nva.commons.apigateway.exceptions.ForbiddenException;
import nva.commons.apigateway.exceptions.NotFoundException;
import nva.commons.core.Environment;
import nva.commons.core.JacocoGenerated;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class FetchReportHandlerV3 extends ApiGatewayHandler<Void, ReportResponse> {

  private static final Logger LOGGER = LoggerFactory.getLogger(FetchReportHandlerV3.class);
  private final ReportResponseFactory reportResponseFactory;

  @JacocoGenerated
  public FetchReportHandlerV3() {
    this(
        new Environment(),
        NviPeriodService.defaultNviPeriodService(),
        ReportAggregationClient.defaultClient());
  }

  public FetchReportHandlerV3(
      Environment environment,
      NviPeriodService nviPeriodService,
      ReportAggregationClient reportAggregationClient) {
    super(Void.class, environment);
    this.reportResponseFactory =
        new ReportResponseFactory(nviPeriodService, reportAggregationClient);
  }

  @Override
  protected void validateRequest(Void unused, RequestInfo requestInfo, Context context)
      throws ApiGatewayException {
    if (!requestInfo.userIsAuthorized(AccessRight.MANAGE_NVI)) {
      throw new ForbiddenException();
    }
  }

  @Override
  protected ReportResponse processInput(Void unused, RequestInfo requestInfo, Context context)
      throws ApiGatewayException {
    var reportRequest = ReportRequestFactory.getRequest(requestInfo, environment);
    try {
      return reportResponseFactory.getResponse(reportRequest);
    } catch (NoSuchElementException exception) {
      LOGGER.error("No institution found for query request: {}", reportRequest, exception);
      throw new NotFoundException(exception.getMessage());
    } catch (IOException exception) {
      LOGGER.error("Failed to execute query request: {}", reportRequest, exception);
      throw new BadGatewayException("Something went wrong! Contact application administrator.");
    }
  }

  @Override
  protected Integer getSuccessStatusCode(Void unused, ReportResponse o) {
    return HTTP_OK;
  }
}
