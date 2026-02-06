package no.sikt.nva.nvi.index.apigateway;

import static java.net.HttpURLConnection.HTTP_OK;
import static java.util.Objects.isNull;

import com.amazonaws.services.lambda.runtime.Context;
import nva.commons.apigateway.ApiGatewayHandler;
import nva.commons.apigateway.RequestInfo;
import nva.commons.apigateway.exceptions.ApiGatewayException;
import nva.commons.core.Environment;

public class FetchReportHandler extends ApiGatewayHandler<Void, ReportResponse> {

  public FetchReportHandler(Environment environment) {
    super(Void.class, environment);
  }

  @Override
  protected void validateRequest(Void unused, RequestInfo requestInfo, Context context)
      throws ApiGatewayException {}

  @Override
  protected ReportResponse processInput(Void unused, RequestInfo requestInfo, Context context)
      throws ApiGatewayException {
    var requestType = ReportRequestType.fromRequestInfo(requestInfo);
    return switch (requestType) {
      case ALL_PERIODS -> new PeriodReport();
      case ALL_INSTITUTIONS -> new AllInstitutionsReport();
      case INSTITUTION -> new InstitutionReport();
    };
  }

  @Override
  protected Integer getSuccessStatusCode(Void unused, ReportResponse o) {
    return HTTP_OK;
  }

  private enum ReportRequestType {
    ALL_PERIODS,
    ALL_INSTITUTIONS,
    INSTITUTION;

    private static final String PERIOD_PATH_PARAM = "period";
    private static final String INSTITUTION_PATH_PARAM = "institution";

    public static ReportRequestType fromRequestInfo(RequestInfo requestInfo) {
      var period = requestInfo.getPathParameters().getOrDefault(PERIOD_PATH_PARAM, null);
      var institution = requestInfo.getPathParameters().getOrDefault(INSTITUTION_PATH_PARAM, null);
      if (isNull(period)) {
        return ALL_PERIODS;
      }
      if (isNull(institution)) {
        return ALL_INSTITUTIONS;
      }
      return INSTITUTION;
    }
  }
}
