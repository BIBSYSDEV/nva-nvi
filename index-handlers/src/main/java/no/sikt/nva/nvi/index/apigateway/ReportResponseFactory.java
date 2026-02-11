package no.sikt.nva.nvi.index.apigateway;

import static java.util.Collections.emptyList;
import static java.util.Objects.requireNonNull;

import java.math.BigDecimal;
import java.net.URI;
import java.util.EnumMap;
import java.util.Map;
import no.sikt.nva.nvi.common.service.model.GlobalApprovalStatus;
import no.sikt.nva.nvi.index.model.document.ApprovalStatus;
import no.sikt.nva.nvi.index.model.report.AllInstitutionsReport;
import no.sikt.nva.nvi.index.model.report.AllPeriodsReport;
import no.sikt.nva.nvi.index.model.report.InstitutionReport;
import no.sikt.nva.nvi.index.model.report.PeriodReport;
import no.sikt.nva.nvi.index.model.report.ReportResponse;
import no.sikt.nva.nvi.index.model.report.TopLevelAggregation;
import nva.commons.core.Environment;
import nva.commons.core.paths.UriWrapper;

public final class ReportResponseFactory {
  private static final String API_HOST_KEY = "API_HOST";
  private static final String BASE_PATH_KEY = "CUSTOM_DOMAIN_BASE_PATH";

  private ReportResponseFactory() {}

  public static ReportResponse getResponse(ReportRequest reportRequest, Environment environment) {
    return switch (reportRequest.type()) {
      case ALL_PERIODS -> placeholderAllPeriodsReport(reportRequest, environment);
      case PERIOD -> placeholderPeriodReport(reportRequest, environment);
      case ALL_INSTITUTIONS -> placeholderAllInstitutionsReport(reportRequest, environment);
      case INSTITUTION -> placeholderInstitutionReport(reportRequest, environment);
    };
  }

  private static URI getBaseUri(Environment environment) {
    return UriWrapper.fromHost(environment.readEnv(API_HOST_KEY))
        .addChild(environment.readEnv(BASE_PATH_KEY))
        .getUri();
  }

  // FIXME: Temporary placeholder
  private static URI getInstitutionId(ReportRequest reportRequest, Environment environment) {
    requireNonNull(reportRequest.institution());
    return UriWrapper.fromUri(getBaseUri(environment))
        .addChild("cristin")
        .addChild("organization")
        .addChild(reportRequest.institution())
        .getUri();
  }

  // FIXME: Temporary placeholder
  private static AllPeriodsReport placeholderAllPeriodsReport(
      ReportRequest reportRequest, Environment environment) {
    var queryId = reportRequest.getQueryId(getBaseUri(environment));
    return new AllPeriodsReport(queryId);
  }

  // FIXME: Temporary placeholder
  private static PeriodReport placeholderPeriodReport(
      ReportRequest reportRequest, Environment environment) {
    var queryId = reportRequest.getQueryId(getBaseUri(environment));
    return new PeriodReport(queryId);
  }

  // FIXME: Temporary placeholder
  private static AllInstitutionsReport placeholderAllInstitutionsReport(
      ReportRequest reportRequest, Environment environment) {
    var queryId = reportRequest.getQueryId(getBaseUri(environment));
    return new AllInstitutionsReport(queryId, reportRequest.period(), emptyList());
  }

  // FIXME: Temporary placeholder
  private static InstitutionReport placeholderInstitutionReport(
      ReportRequest reportRequest, Environment environment) {
    var queryId = reportRequest.getQueryId(getBaseUri(environment));
    var institutionId = getInstitutionId(reportRequest, environment);
    var summary = getEmptyTopLevelAggregation();
    return new InstitutionReport(
        queryId, reportRequest.period(), institutionId, summary, emptyList());
  }

  private static TopLevelAggregation getEmptyTopLevelAggregation() {
    return new TopLevelAggregation(
        0, BigDecimal.ZERO, getEmptyGlobalApprovalStatusMap(), getEmptyApprovalStatusMap());
  }

  private static Map<ApprovalStatus, Integer> getEmptyApprovalStatusMap() {
    var map = new EnumMap<ApprovalStatus, Integer>(ApprovalStatus.class);
    for (var status : ApprovalStatus.values()) {
      map.put(status, 0);
    }
    return map;
  }

  private static Map<GlobalApprovalStatus, Integer> getEmptyGlobalApprovalStatusMap() {
    var map = new EnumMap<GlobalApprovalStatus, Integer>(GlobalApprovalStatus.class);
    for (var status : GlobalApprovalStatus.values()) {
      map.put(status, 0);
    }
    return map;
  }
}
