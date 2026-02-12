package no.sikt.nva.nvi.index.apigateway;

import static java.net.HttpURLConnection.HTTP_OK;

import com.amazonaws.services.lambda.runtime.Context;
import no.sikt.nva.nvi.index.model.report.ReportResponse;
import nva.commons.apigateway.AccessRight;
import nva.commons.apigateway.ApiGatewayHandler;
import nva.commons.apigateway.RequestInfo;
import nva.commons.apigateway.exceptions.ApiGatewayException;
import nva.commons.apigateway.exceptions.ForbiddenException;
import nva.commons.core.Environment;
import nva.commons.core.JacocoGenerated;

public class FetchReportHandler extends ApiGatewayHandler<Void, ReportResponse> {

  @JacocoGenerated
  public FetchReportHandler() {
    this(new Environment());
  }

  public FetchReportHandler(Environment environment) {
    super(Void.class, environment);
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
    var reportRequest = ReportRequest.from(requestInfo);
    return ReportResponseFactory.getResponse(reportRequest, environment);
  }

  @Override
  protected Integer getSuccessStatusCode(Void unused, ReportResponse o) {
    return HTTP_OK;
  }
}
