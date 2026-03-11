package no.sikt.nva.nvi.index.report;

import static nva.commons.core.attempt.Try.attempt;

import java.util.Arrays;
import java.util.List;

public interface ReportRow {

  default List<String> toRow() {
    return Arrays.stream(getClass().getRecordComponents())
        .filter(c -> c.isAnnotationPresent(Column.class))
        .map(c -> attempt(() -> (String) c.getAccessor().invoke(this)).orElseThrow())
        .toList();
  }
}
