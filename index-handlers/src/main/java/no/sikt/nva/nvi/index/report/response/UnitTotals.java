package no.sikt.nva.nvi.index.report.response;

import java.math.BigDecimal;

public record UnitTotals(
    BigDecimal validPoints,
    int disputedCount,
    int undisputedProcessedCount,
    int undisputedTotalCount)
    implements Totals {}
