package no.sikt.nva.nvi.index.report.request;

import java.net.URI;

public sealed interface ReportRequest
    permits AllPeriodsReportRequest,
        PeriodReportRequest,
        AllInstitutionsReportRequest,
        InstitutionReportRequest {

  URI queryId();
}
