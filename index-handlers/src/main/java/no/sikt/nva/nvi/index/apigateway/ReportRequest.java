package no.sikt.nva.nvi.index.apigateway;

import static java.util.Objects.isNull;
import static java.util.Objects.nonNull;

import nva.commons.apigateway.RequestInfo;

public final class ReportRequest {

  private static final String PERIOD_PATH_PARAM = "period";
  private static final String INSTITUTION_PATH_PARAM = "institution";
  private static final String INSTITUTIONS_PATH_PARAM = "institutions";
  private final String period;
  private final String institution;
  private final String path;

  private ReportRequest(String period, String institution, String path) {
    this.period = period;
    this.institution = institution;
    this.path = path;
  }

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

  public enum ReportRequestType {
    ALL_PERIODS,
    PERIOD,
    ALL_INSTITUTIONS,
    INSTITUTION
  }
}
