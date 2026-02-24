package no.sikt.nva.nvi.index.report.response;

import java.math.BigDecimal;

public record PeriodTotals(
    BigDecimal validPoints,
    int disputedCount,
    int undisputedProcessedCount,
    int undisputedTotalCount)
    implements Totals {}
