package no.sikt.nva.nvi.index.report;

import static java.net.HttpURLConnection.HTTP_OK;

import com.amazonaws.services.lambda.runtime.Context;
import no.sikt.nva.nvi.common.service.NviPeriodService;
import no.sikt.nva.nvi.index.report.request.ReportRequestFactory;
import no.sikt.nva.nvi.index.report.response.ReportResponse;
import no.sikt.nva.nvi.index.report.response.ReportResponseFactory;
import nva.commons.apigateway.AccessRight;
import nva.commons.apigateway.ApiGatewayHandler;
import nva.commons.apigateway.RequestInfo;
import nva.commons.apigateway.exceptions.ApiGatewayException;
import nva.commons.apigateway.exceptions.ForbiddenException;
import nva.commons.core.Environment;
import nva.commons.core.JacocoGenerated;

public class FetchReportHandler extends ApiGatewayHandler<Void, ReportResponse> {

  private final ReportResponseFactory reportResponseFactory;

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
    return reportResponseFactory.getResponse(reportRequest);
  }

  @Override
  protected Integer getSuccessStatusCode(Void unused, ReportResponse o) {
    return HTTP_OK;
  }
}
