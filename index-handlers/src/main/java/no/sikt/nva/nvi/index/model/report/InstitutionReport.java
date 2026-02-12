package no.sikt.nva.nvi.index.model.report;

import java.net.URI;
import java.util.List;

public record InstitutionReport(
    URI id, String period, InstitutionSummary institution, List<UnitSummary> units)
    implements ReportResponse {}
