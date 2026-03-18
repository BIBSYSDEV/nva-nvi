package no.sikt.nva.nvi.index.report.model;

import java.util.List;

@FunctionalInterface
public interface Row {

  default List<String> values() {
    return cells().stream().map(Cell::string).toList();
  }

  default List<String> headers() {
    return cells().isEmpty()
        ? List.of()
        : cells().stream().map(Cell::header).map(Header::name).toList();
  }

  List<Cell> cells();
}
