package no.sikt.nva.nvi.index.report.request;

import static no.sikt.nva.nvi.index.report.ReportConstants.API_HOST_KEY;
import static no.sikt.nva.nvi.index.report.ReportConstants.BASE_PATH_KEY;
import static no.sikt.nva.nvi.index.report.ReportConstants.INSTITUTIONS_PATH_SEGMENT;
import static no.sikt.nva.nvi.index.report.ReportConstants.REPORTS_PATH_SEGMENT;

import java.net.URI;
import java.util.List;
import nva.commons.apigateway.MediaType;
import nva.commons.core.Environment;
import nva.commons.core.paths.UriWrapper;

public record AllInstitutionsReportRequest(URI queryId, String period, ReportFormat reportType)
    implements ReportRequest {

  public static AllInstitutionsReportRequest from(
      Environment environment, String period, ReportFormat reportType) {
    var queryId = getQueryId(environment, period);
    return new AllInstitutionsReportRequest(queryId, period, reportType);
  }

  @Override
  public boolean hasSupportedReportType() {
    return List.of(MediaType.JSON_UTF_8, MediaType.OOXML_SHEET, MediaType.CSV_UTF_8)
        .contains(reportType.getMediaType());
  }

  private static URI getQueryId(Environment environment, String period) {
    return UriWrapper.fromHost(environment.readEnv(API_HOST_KEY))
        .addChild(environment.readEnv(BASE_PATH_KEY))
        .addChild(REPORTS_PATH_SEGMENT)
        .addChild(period)
        .addChild(INSTITUTIONS_PATH_SEGMENT)
        .getUri();
  }
}
