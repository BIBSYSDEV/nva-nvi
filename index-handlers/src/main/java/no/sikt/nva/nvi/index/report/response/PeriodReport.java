package no.sikt.nva.nvi.index.report.response;

import java.net.URI;
import no.sikt.nva.nvi.common.service.dto.NviPeriodDto;
import no.sikt.nva.nvi.index.report.model.PeriodAggregationResult;

public record PeriodReport(
    URI id,
    NviPeriodDto period,
    PeriodTotals totals,
    CandidatesByGlobalApprovalStatus byGlobalApprovalStatus)
    implements ReportResponse {

  public static PeriodReport from(URI queryId, PeriodAggregationResult result) {
    return new PeriodReport(
        queryId,
        result.period().toDto(),
        PeriodTotals.from(result),
        CandidatesByGlobalApprovalStatus.from(result));
  }
}
