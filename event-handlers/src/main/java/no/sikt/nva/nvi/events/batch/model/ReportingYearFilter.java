package no.sikt.nva.nvi.events.batch.model;

import static java.util.Collections.emptyList;
import static no.sikt.nva.nvi.common.utils.CollectionUtils.copyOfNullable;
import static no.sikt.nva.nvi.common.utils.Validator.validateYear;

import com.fasterxml.jackson.annotation.JsonIgnore;
import java.util.List;

public record ReportingYearFilter(List<String> reportingYears) implements BatchJobFilter {

  public ReportingYearFilter {
    reportingYears = copyOfNullable(reportingYears);
    for (var year : reportingYears) {
      validateYear(year);
    }
  }

  public ReportingYearFilter(String... years) {
    this(List.of(years));
  }

  @JsonIgnore
  public boolean allowsYear(String year) {
    return reportingYears.contains(year);
  }

  @JsonIgnore
  public boolean hasMoreYears() {
    return reportingYears.size() > 1;
  }

  @JsonIgnore
  public ReportingYearFilter getIncrementedFilter() {
    return new ReportingYearFilter(getIncrementedYears());
  }

  private List<String> getIncrementedYears() {
    return reportingYears.size() > 1
        ? reportingYears.subList(1, reportingYears.size())
        : emptyList();
  }
}
