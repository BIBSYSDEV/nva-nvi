package no.sikt.nva.nvi.index.model.report;

import java.net.URI;
import java.util.List;

public record AllInstitutionsReport(URI id, String period, List<InstitutionSummary> institutions)
    implements ReportResponse {}
