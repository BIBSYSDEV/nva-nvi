package no.sikt.nva.nvi.events.batch.model;

import static java.util.Collections.emptyList;
import static java.util.Collections.emptyMap;
import static java.util.Objects.requireNonNullElse;

import java.util.List;
import java.util.Map;

public record YearQueryState(
    List<String> remainingYears, Map<String, String> lastEvaluatedKey, int itemsEnqueued)
    implements PaginationState {

  public YearQueryState {
    remainingYears = List.copyOf(requireNonNullElse(remainingYears, emptyList()));
    lastEvaluatedKey = Map.copyOf(requireNonNullElse(lastEvaluatedKey, emptyMap()));
  }

  public static YearQueryState forYears(List<String> years) {
    return new YearQueryState(years, null, 0);
  }

  public String currentYear() {
    return remainingYears.getFirst();
  }

  public boolean hasMoreYears() {
    return remainingYears.size() > 1;
  }

  @Override
  public YearQueryState withNextPage(Map<String, String> newLastEvaluatedKey, int additionalItems) {
    return new YearQueryState(remainingYears, newLastEvaluatedKey, itemsEnqueued + additionalItems);
  }

  public YearQueryState withNextYear() {
    return new YearQueryState(
        remainingYears.subList(1, remainingYears.size()), null, itemsEnqueued);
  }
}
