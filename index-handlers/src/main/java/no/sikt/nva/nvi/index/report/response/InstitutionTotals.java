package no.sikt.nva.nvi.index.report.response;

import java.math.BigDecimal;
import no.sikt.nva.nvi.index.report.model.InstitutionAggregationResult;

public record InstitutionTotals(
    BigDecimal validPoints,
    int disputedCount,
    int undisputedProcessedCount,
    int undisputedTotalCount)
    implements Totals {

  public static InstitutionTotals from(InstitutionAggregationResult result) {
    return new InstitutionTotals(
        result.validPoints(),
        result.disputedCount(),
        result.undisputedProcessedCount(),
        result.undisputedTotalCount());
  }
}
