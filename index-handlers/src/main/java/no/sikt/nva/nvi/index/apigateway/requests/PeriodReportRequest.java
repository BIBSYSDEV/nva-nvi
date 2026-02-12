package no.sikt.nva.nvi.index.apigateway.requests;

import static no.sikt.nva.nvi.index.utils.ReportConstants.API_HOST_KEY;
import static no.sikt.nva.nvi.index.utils.ReportConstants.BASE_PATH_KEY;
import static no.sikt.nva.nvi.index.utils.ReportConstants.REPORTS_PATH_SEGMENT;

import java.net.URI;
import nva.commons.core.Environment;
import nva.commons.core.paths.UriWrapper;

public record PeriodReportRequest(URI queryId, String period) implements ReportRequest {

  public static PeriodReportRequest from(Environment environment, String period) {
    var queryId = getQueryId(environment, period);
    return new PeriodReportRequest(queryId, period);
  }

  private static URI getQueryId(Environment environment, String period) {
    return UriWrapper.fromHost(environment.readEnv(API_HOST_KEY))
        .addChild(environment.readEnv(BASE_PATH_KEY))
        .addChild(REPORTS_PATH_SEGMENT)
        .addChild(period)
        .getUri();
  }
}
