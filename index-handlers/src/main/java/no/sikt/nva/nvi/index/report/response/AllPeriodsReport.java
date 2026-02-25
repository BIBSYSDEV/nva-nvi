package no.sikt.nva.nvi.index.report.response;

import java.net.URI;
import java.util.List;

public record AllPeriodsReport(URI id, List<PeriodReport> periods) implements ReportResponse {}
