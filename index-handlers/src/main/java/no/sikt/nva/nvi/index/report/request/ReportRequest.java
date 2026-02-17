package no.sikt.nva.nvi.index.report.request;

public sealed interface ReportRequest
    permits AllPeriodsReportRequest,
        PeriodReportRequest,
        AllInstitutionsReportRequest,
        InstitutionReportRequest {}
