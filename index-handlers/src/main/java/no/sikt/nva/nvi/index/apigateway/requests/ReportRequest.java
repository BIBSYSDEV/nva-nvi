package no.sikt.nva.nvi.index.apigateway.requests;

public sealed interface ReportRequest
    permits AllPeriodsReportRequest,
        PeriodReportRequest,
        AllInstitutionsReportRequest,
        InstitutionReportRequest {}
