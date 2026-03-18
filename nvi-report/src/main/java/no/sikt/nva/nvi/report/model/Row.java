package no.sikt.nva.nvi.report.model;

import java.util.Collections;
import java.util.List;

@FunctionalInterface
public interface Row {

  default List<String> values() {
    return cells().stream().map(Cell::string).toList();
  }

  default List<String> headers() {
    return cells().isEmpty()
        ? Collections.emptyList()
        : cells().stream().map(Cell::header).map(Header::name).toList();
  }

  List<Cell> cells();
}
