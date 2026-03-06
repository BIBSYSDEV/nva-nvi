package no.sikt.nva.nvi.index.report.response;

import java.math.BigDecimal;
import no.sikt.nva.nvi.common.service.model.GlobalApprovalStatus;
import no.sikt.nva.nvi.index.report.model.InstitutionAggregationResult;

public record InstitutionTotals(
    BigDecimal validPoints,
    int disputedCount,
    int globalApprovedCount,
    int globalRejectedCount,
    int undisputedProcessedCount,
    int undisputedTotalCount)
    implements Totals {

  public static InstitutionTotals from(InstitutionAggregationResult result) {
    return new InstitutionTotals(
        result.validPoints(),
        result.globalStatusCount(GlobalApprovalStatus.DISPUTE),
        result.globalStatusCount(GlobalApprovalStatus.APPROVED),
        result.globalStatusCount(GlobalApprovalStatus.REJECTED),
        result.undisputedProcessedCount(),
        result.undisputedTotalCount());
  }
}
