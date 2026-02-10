package no.sikt.nva.nvi.index.model.report;

import java.net.URI;
import java.util.List;

public record InstitutionReport(
    URI id,
    String period,
    URI institution,
    TopLevelAggregation summary,
    List<UnitSummary> units)
    implements ReportResponse {}
