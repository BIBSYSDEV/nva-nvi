package no.sikt.nva.nvi.events.batch.model;

import static java.util.Collections.emptyList;
import static java.util.Objects.requireNonNullElse;

import java.util.List;

public record ReportingYearFilter(List<String> reportingYears) implements BatchJobFilter {

  public ReportingYearFilter {
    reportingYears = requireNonNullElse(reportingYears, emptyList());
  }

  public boolean includesAllYears() {
    return reportingYears.isEmpty();
  }
}
