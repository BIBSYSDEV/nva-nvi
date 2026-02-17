package no.sikt.nva.nvi.index.report.request;

import static no.sikt.nva.nvi.index.report.ReportConstants.API_HOST_KEY;
import static no.sikt.nva.nvi.index.report.ReportConstants.BASE_PATH_KEY;
import static no.sikt.nva.nvi.index.report.ReportConstants.REPORTS_PATH_SEGMENT;

import java.net.URI;
import nva.commons.core.Environment;
import nva.commons.core.paths.UriWrapper;

public record AllPeriodsReportRequest(URI queryId) implements ReportRequest {

  public static AllPeriodsReportRequest from(Environment environment) {
    return new AllPeriodsReportRequest(getQueryId(environment));
  }

  private static URI getQueryId(Environment environment) {
    return UriWrapper.fromHost(environment.readEnv(API_HOST_KEY))
        .addChild(environment.readEnv(BASE_PATH_KEY))
        .addChild(REPORTS_PATH_SEGMENT)
        .getUri();
  }
}
