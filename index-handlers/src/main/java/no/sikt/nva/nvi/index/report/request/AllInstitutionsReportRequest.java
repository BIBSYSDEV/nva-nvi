package no.sikt.nva.nvi.index.report.request;

import static no.sikt.nva.nvi.index.utils.ReportConstants.API_HOST_KEY;
import static no.sikt.nva.nvi.index.utils.ReportConstants.BASE_PATH_KEY;
import static no.sikt.nva.nvi.index.utils.ReportConstants.INSTITUTIONS_PATH_SEGMENT;
import static no.sikt.nva.nvi.index.utils.ReportConstants.REPORTS_PATH_SEGMENT;

import java.net.URI;
import nva.commons.core.Environment;
import nva.commons.core.paths.UriWrapper;

public record AllInstitutionsReportRequest(URI queryId, String period) implements ReportRequest {

  public static AllInstitutionsReportRequest from(Environment environment, String period) {
    var queryId = getQueryId(environment, period);
    return new AllInstitutionsReportRequest(queryId, period);
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
