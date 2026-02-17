package no.sikt.nva.nvi.index.report.response;

import java.net.URI;
import no.sikt.nva.nvi.common.service.dto.NviPeriodDto;

public record PeriodReport(
    URI id,
    NviPeriodDto period,
    PeriodTotals totals,
    CandidatesByGlobalApprovalStatus byGlobalApprovalStatus)
    implements ReportResponse {}
