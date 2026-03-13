package no.sikt.nva.nvi.index.report.request;

import static java.util.Objects.isNull;
import static java.util.Objects.nonNull;
import static no.sikt.nva.nvi.index.report.ReportConstants.INSTITUTIONS_PATH_SEGMENT;
import static no.sikt.nva.nvi.index.report.ReportConstants.INSTITUTION_PATH_PARAM;
import static no.sikt.nva.nvi.index.report.ReportConstants.PERIOD_PATH_PARAM;
import static no.sikt.nva.nvi.index.report.request.ReportType.CSV;
import static no.sikt.nva.nvi.index.report.request.ReportType.JSON;
import static no.sikt.nva.nvi.index.report.request.ReportType.XLSX;
import static org.apache.http.HttpHeaders.ACCEPT;

import nva.commons.apigateway.MediaType;
import nva.commons.apigateway.RequestInfo;
import nva.commons.core.Environment;

public final class ReportRequestFactory {

  private ReportRequestFactory() {}

  public static ReportRequest getRequest(RequestInfo requestInfo, Environment environment) {
    var pathParameters = requestInfo.getPathParameters();
    var period = pathParameters.get(PERIOD_PATH_PARAM);
    var institutionIdentifier = pathParameters.get(INSTITUTION_PATH_PARAM);
    var path = requestInfo.getPath();
    var reportType = getReportType(requestInfo);

    if (isNull(period)) {
      return AllPeriodsReportRequest.from(environment, reportType);
    }
    if (nonNull(institutionIdentifier)) {
      return InstitutionReportRequest.from(environment, period, institutionIdentifier, reportType);
    }
    if (path.contains(INSTITUTIONS_PATH_SEGMENT)) {
      return AllInstitutionsReportRequest.from(environment, period, reportType);
    }
    return PeriodReportRequest.from(environment, period, reportType);
  }

  public static ReportType getReportType(RequestInfo requestInfo) {
    return requestInfo
        .getHeaderOptional(ACCEPT)
        .map(ReportRequestFactory::toReportType)
        .orElse(JSON);
  }

  private static ReportType toReportType(String value) {
    if (MediaType.OOXML_SHEET.toString().equals(value)) {
      return XLSX;
    }
    if (MediaType.CSV_UTF_8.toString().equals(value)) {
      return CSV;
    }
    return JSON;
  }
}
