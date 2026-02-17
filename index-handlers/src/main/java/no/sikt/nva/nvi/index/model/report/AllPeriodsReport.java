package no.sikt.nva.nvi.index.model.report;

import java.net.URI;
import java.util.List;

public record AllPeriodsReport(URI id, List<PeriodReport> periods) implements ReportResponse {}
