package no.sikt.nva.nvi.index.model.report;

import java.net.URI;
import java.util.List;
import no.sikt.nva.nvi.common.service.dto.NviPeriodDto;

public record AllInstitutionsReport(
    URI id, NviPeriodDto period, List<InstitutionReport> institutions) implements ReportResponse {}
