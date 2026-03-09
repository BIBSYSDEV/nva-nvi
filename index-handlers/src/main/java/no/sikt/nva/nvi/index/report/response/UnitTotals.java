package no.sikt.nva.nvi.index.report.response;

import java.math.BigDecimal;
import nva.commons.core.JacocoGenerated;

// TODO: Implemented later (NP-50858)
@JacocoGenerated
public record UnitTotals(
    BigDecimal validPoints,
    int disputedCount,
    int undisputedProcessedCount,
    int undisputedTotalCount)
    implements Totals {}
