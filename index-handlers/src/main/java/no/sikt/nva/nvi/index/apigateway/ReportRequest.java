package no.sikt.nva.nvi.index.apigateway;

import static java.util.Objects.isNull;
import static java.util.Objects.nonNull;

import java.net.URI;
import nva.commons.apigateway.RequestInfo;
import nva.commons.core.paths.UriWrapper;

public record ReportRequest(String period, String institution, String path) {

  private static final String PERIOD_PATH_PARAM = "period";
  private static final String INSTITUTION_PATH_PARAM = "institution";
  private static final String INSTITUTIONS_PATH_PARAM = "institutions";

  public static ReportRequest from(RequestInfo requestInfo) {
    var pathParameters = requestInfo.getPathParameters();
    var period = pathParameters.get(PERIOD_PATH_PARAM);
    var institution = pathParameters.get(INSTITUTION_PATH_PARAM);
    var path = requestInfo.getPath();
    return new ReportRequest(period, institution, path);
  }

  public ReportRequestType type() {
    if (isNull(period)) {
      return ReportRequestType.ALL_PERIODS;
    }
    if (nonNull(institution)) {
      return ReportRequestType.INSTITUTION;
    }
    if (path.contains(INSTITUTIONS_PATH_PARAM)) {
      return ReportRequestType.ALL_INSTITUTIONS;
    }
    return ReportRequestType.PERIOD;
  }

  public URI getQueryId(URI baseUri) {
    var queryUri = UriWrapper.fromUri(baseUri).addChild(PERIOD_PATH_PARAM);
    return switch (type()) {
      case ALL_PERIODS -> queryUri.getUri();
      case PERIOD -> queryUri.addChild(period).getUri();
      case ALL_INSTITUTIONS -> queryUri.addChild(period).addChild(INSTITUTIONS_PATH_PARAM).getUri();
      case INSTITUTION ->
          queryUri.addChild(period).addChild(INSTITUTION_PATH_PARAM).addChild(institution).getUri();
    };
  }

  public enum ReportRequestType {
    ALL_PERIODS,
    PERIOD,
    ALL_INSTITUTIONS,
    INSTITUTION
  }
}
