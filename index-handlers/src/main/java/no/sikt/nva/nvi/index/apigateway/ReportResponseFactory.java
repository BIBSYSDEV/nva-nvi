package no.sikt.nva.nvi.index.apigateway;

import static java.util.Collections.emptyList;

import java.math.BigDecimal;
import java.util.EnumMap;
import java.util.Map;
import no.sikt.nva.nvi.common.model.Sector;
import no.sikt.nva.nvi.common.service.model.GlobalApprovalStatus;
import no.sikt.nva.nvi.index.apigateway.requests.AllInstitutionsReportRequest;
import no.sikt.nva.nvi.index.apigateway.requests.AllPeriodsReportRequest;
import no.sikt.nva.nvi.index.apigateway.requests.InstitutionReportRequest;
import no.sikt.nva.nvi.index.apigateway.requests.PeriodReportRequest;
import no.sikt.nva.nvi.index.apigateway.requests.ReportRequest;
import no.sikt.nva.nvi.index.model.document.ApprovalStatus;
import no.sikt.nva.nvi.index.model.report.AllInstitutionsReport;
import no.sikt.nva.nvi.index.model.report.AllPeriodsReport;
import no.sikt.nva.nvi.index.model.report.InstitutionReport;
import no.sikt.nva.nvi.index.model.report.InstitutionSummary;
import no.sikt.nva.nvi.index.model.report.PeriodReport;
import no.sikt.nva.nvi.index.model.report.ReportResponse;
import no.sikt.nva.nvi.index.model.report.TopLevelAggregation;

public final class ReportResponseFactory {

  private ReportResponseFactory() {}

  public static ReportResponse getResponse(ReportRequest reportRequest) {
    return switch (reportRequest) {
      case AllPeriodsReportRequest request -> placeholderAllPeriodsReport(request);
      case PeriodReportRequest request -> placeholderPeriodReport(request);
      case AllInstitutionsReportRequest request -> placeholderAllInstitutionsReport(request);
      case InstitutionReportRequest request -> placeholderInstitutionReport(request);
    };
  }

  // FIXME: Temporary placeholder
  private static AllPeriodsReport placeholderAllPeriodsReport(AllPeriodsReportRequest request) {
    return new AllPeriodsReport(request.queryId());
  }

  // FIXME: Temporary placeholder
  private static PeriodReport placeholderPeriodReport(PeriodReportRequest request) {
    return new PeriodReport(request.queryId());
  }

  // FIXME: Temporary placeholder
  private static AllInstitutionsReport placeholderAllInstitutionsReport(
      AllInstitutionsReportRequest request) {
    return new AllInstitutionsReport(request.queryId(), request.period(), emptyList());
  }

  // FIXME: Temporary placeholder
  private static InstitutionReport placeholderInstitutionReport(InstitutionReportRequest request) {
    var institutionSummary =
        new InstitutionSummary(
            request.institutionId(), Sector.UNKNOWN.toString(), getEmptyTopLevelAggregation());
    return new InstitutionReport(
        request.queryId(), request.period(), institutionSummary, emptyList());
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
