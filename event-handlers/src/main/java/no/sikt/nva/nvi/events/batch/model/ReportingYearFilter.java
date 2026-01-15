package no.sikt.nva.nvi.events.batch.model;

import static java.util.Collections.emptyList;
import static java.util.Objects.requireNonNullElse;
import static no.sikt.nva.nvi.common.utils.Validator.validateYear;

import java.util.List;

public record ReportingYearFilter(List<String> reportingYears) implements BatchJobFilter {

  public ReportingYearFilter {
    reportingYears = requireNonNullElse(reportingYears, emptyList());
    for (var year : reportingYears) {
      validateYear(year);
    }
  }

  public boolean includesAllYears() {
    return reportingYears.isEmpty();
  }
}
