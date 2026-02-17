package no.sikt.nva.nvi.index.model.report;

import java.net.URI;
import java.util.List;
import no.sikt.nva.nvi.common.client.model.Organization;
import no.sikt.nva.nvi.common.service.dto.NviPeriodDto;

public record InstitutionReport(
    URI id,
    NviPeriodDto period,
    String sector,
    Organization institution,
    InstitutionSummary institutionSummary,
    List<UnitSummary> units)
    implements ReportResponse {}
