package no.sikt.nva.nvi.index.apigateway.requests;

import static no.sikt.nva.nvi.index.utils.ReportConstants.API_HOST_KEY;
import static no.sikt.nva.nvi.index.utils.ReportConstants.BASE_PATH_KEY;
import static no.sikt.nva.nvi.index.utils.ReportConstants.INSTITUTIONS_PATH_SEGMENT;
import static no.sikt.nva.nvi.index.utils.ReportConstants.REPORTS_PATH_SEGMENT;

import java.net.URI;
import nva.commons.core.Environment;
import nva.commons.core.paths.UriWrapper;

public record InstitutionReportRequest(URI queryId, String period, URI institutionId)
    implements ReportRequest {

  private static final String CRISTIN_PATH_SEGMENT = "cristin";
  private static final String ORGANIZATION_PATH_SEGMENT = "organization";

  public static InstitutionReportRequest from(
      Environment environment, String period, String institutionIdentifier) {
    var queryId = getQueryId(environment, period, institutionIdentifier);
    var institutionId = getInstitutionId(environment, institutionIdentifier);
    return new InstitutionReportRequest(queryId, period, institutionId);
  }

  private static URI getQueryId(
      Environment environment, String period, String institutionIdentifier) {
    return UriWrapper.fromHost(environment.readEnv(API_HOST_KEY))
        .addChild(environment.readEnv(BASE_PATH_KEY))
        .addChild(REPORTS_PATH_SEGMENT)
        .addChild(period)
        .addChild(INSTITUTIONS_PATH_SEGMENT)
        .addChild(institutionIdentifier)
        .getUri();
  }

  private static URI getInstitutionId(Environment environment, String institutionIdentifier) {
    return UriWrapper.fromHost(environment.readEnv(API_HOST_KEY))
        .addChild(CRISTIN_PATH_SEGMENT)
        .addChild(ORGANIZATION_PATH_SEGMENT)
        .addChild(institutionIdentifier)
        .getUri();
  }
}
