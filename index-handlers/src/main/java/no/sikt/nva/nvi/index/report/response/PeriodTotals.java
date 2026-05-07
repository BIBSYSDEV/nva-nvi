package no.sikt.nva.nvi.index.report.response;

import java.math.BigDecimal;
import no.sikt.nva.nvi.index.report.model.PeriodAggregationResult;

public record PeriodTotals(
    BigDecimal validPoints,
    int disputedCount,
    int undisputedProcessedCount,
    int undisputedTotalCount)
    implements Totals {

  public static PeriodTotals from(PeriodAggregationResult result) {
    return new PeriodTotals(
        result.validPoints(),
        result.disputedCount(),
        result.undisputedProcessedCount(),
        result.undisputedTotalCount());
  }
}
