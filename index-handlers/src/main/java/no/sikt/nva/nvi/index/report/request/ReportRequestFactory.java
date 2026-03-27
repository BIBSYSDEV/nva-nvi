package no.sikt.nva.nvi.index.report.request;

import static java.util.Objects.isNull;
import static java.util.Objects.nonNull;
import static no.sikt.nva.nvi.index.report.ReportConstants.INSTITUTIONS_PATH_SEGMENT;
import static no.sikt.nva.nvi.index.report.ReportConstants.INSTITUTION_PATH_PARAM;
import static no.sikt.nva.nvi.index.report.ReportConstants.PERIOD_PATH_PARAM;
import static no.sikt.nva.nvi.index.report.request.ReportType.JSON;
import static no.sikt.nva.nvi.index.report.request.ReportType.create;
import static nva.commons.core.attempt.Try.attempt;
import static org.apache.http.HttpHeaders.ACCEPT;

import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import nva.commons.apigateway.MediaType;
import nva.commons.apigateway.RequestInfo;
import nva.commons.core.Environment;
import nva.commons.core.StringUtils;
import nva.commons.core.paths.UriWrapper;

public final class ReportRequestFactory {

  private static final String HEADER_DELIMITER = ";";

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
    var mediaType = MediaType.parse(value);
    var profile = extractProfile(value);

    return create(mediaType, profile.orElse(null));
  }

  private static Optional<String> extractProfile(String value) {
    return attempt(() -> value.split(HEADER_DELIMITER))
        .map(Arrays::asList)
        .map(List::getLast)
        .map(String::trim)
        .map(string -> string.replace("profile=", StringUtils.EMPTY_STRING))
        .map(String::trim)
        .map(UriWrapper::fromUri)
        .map(UriWrapper::getLastPathElement)
        .toOptional();
  }
}
