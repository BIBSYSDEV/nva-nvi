package no.sikt.nva.nvi.index.report.request;

import static no.sikt.nva.nvi.index.report.ReportConstants.API_HOST_KEY;
import static no.sikt.nva.nvi.index.report.ReportConstants.BASE_PATH_KEY;
import static no.sikt.nva.nvi.index.report.ReportConstants.REPORTS_PATH_SEGMENT;
import static no.sikt.nva.nvi.index.report.request.ReportType.CSV_AUTHOR_SHARES;
import static no.sikt.nva.nvi.index.report.request.ReportType.JSON;

import java.net.URI;
import java.util.List;
import nva.commons.core.Environment;
import nva.commons.core.paths.UriWrapper;

public record PeriodReportRequest(URI queryId, String period, ReportType reportType)
    implements ReportRequest {

  public static PeriodReportRequest from(
      Environment environment, String period, ReportType reportType) {
    var queryId = getQueryId(environment, period);
    return new PeriodReportRequest(queryId, period, reportType);
  }

  @Override
  public boolean hasSupportedReportType() {
    return List.of(JSON, CSV_AUTHOR_SHARES).contains(reportType);
  }

  private static URI getQueryId(Environment environment, String period) {
    return UriWrapper.fromHost(environment.readEnv(API_HOST_KEY))
        .addChild(environment.readEnv(BASE_PATH_KEY))
        .addChild(REPORTS_PATH_SEGMENT)
        .addChild(period)
        .getUri();
  }
}
