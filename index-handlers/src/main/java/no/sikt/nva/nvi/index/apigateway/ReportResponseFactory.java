package no.sikt.nva.nvi.index.apigateway;

import no.sikt.nva.nvi.index.model.report.AllInstitutionsReport;
import no.sikt.nva.nvi.index.model.report.AllPeriodsReport;
import no.sikt.nva.nvi.index.model.report.InstitutionReport;
import no.sikt.nva.nvi.index.model.report.PeriodReport;
import no.sikt.nva.nvi.index.model.report.ReportResponse;

public final class ReportResponseFactory {

  private ReportResponseFactory() {}

  public static ReportResponse getResponse(ReportRequest requestType) {
    return switch (requestType.type()) {
      case ALL_PERIODS -> new AllPeriodsReport();
      case PERIOD -> new PeriodReport();
      case ALL_INSTITUTIONS -> new AllInstitutionsReport();
      case INSTITUTION -> new InstitutionReport();
    };
  }
}
