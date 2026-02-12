package no.sikt.nva.nvi.index.apigateway.requests;

import static java.util.Objects.isNull;
import static java.util.Objects.nonNull;
import static no.sikt.nva.nvi.index.utils.ReportConstants.INSTITUTIONS_PATH_SEGMENT;
import static no.sikt.nva.nvi.index.utils.ReportConstants.INSTITUTION_PATH_PARAM;
import static no.sikt.nva.nvi.index.utils.ReportConstants.PERIOD_PATH_PARAM;

import nva.commons.apigateway.RequestInfo;
import nva.commons.core.Environment;

public final class ReportRequestFactory {

  private ReportRequestFactory() {}

  public static ReportRequest getRequest(RequestInfo requestInfo, Environment environment) {
    var pathParameters = requestInfo.getPathParameters();
    var period = pathParameters.get(PERIOD_PATH_PARAM);
    var institutionIdentifier = pathParameters.get(INSTITUTION_PATH_PARAM);
    var path = requestInfo.getPath();

    if (isNull(period)) {
      return AllPeriodsReportRequest.from(environment);
    }
    if (nonNull(institutionIdentifier)) {
      return InstitutionReportRequest.from(environment, period, institutionIdentifier);
    }
    if (path.contains(INSTITUTIONS_PATH_SEGMENT)) {
      return AllInstitutionsReportRequest.from(environment, period);
    }
    return PeriodReportRequest.from(environment, period);
  }
}
