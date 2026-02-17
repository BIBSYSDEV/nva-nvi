package no.sikt.nva.nvi.index.report.response;

import com.fasterxml.jackson.annotation.JsonSubTypes;
import com.fasterxml.jackson.annotation.JsonTypeInfo;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import java.math.BigDecimal;

@JsonSerialize
@JsonTypeInfo(use = JsonTypeInfo.Id.NAME, property = "type")
@JsonSubTypes({
  @JsonSubTypes.Type(name = "PeriodTotals", value = PeriodTotals.class),
  @JsonSubTypes.Type(name = "InstitutionTotals", value = InstitutionTotals.class),
  @JsonSubTypes.Type(name = "UnitTotals", value = UnitTotals.class)
})
public sealed interface Totals permits PeriodTotals, InstitutionTotals, UnitTotals {

  BigDecimal validPoints();

  int disputedCount();

  int undisputedProcessedCount();

  int undisputedTotalCount();
}
