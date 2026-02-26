package no.sikt.nva.nvi.index.report.response;

import nva.commons.core.JacocoGenerated;

import java.math.BigDecimal;

// TODO: Implemented later (NP-50858)
@JacocoGenerated
public record UnitTotals(
    BigDecimal validPoints,
    int disputedCount,
    int undisputedProcessedCount,
    int undisputedTotalCount)
    implements Totals {}
