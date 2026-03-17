package no.sikt.nva.nvi.index.report.model;

import java.util.List;

public record ReportRow(List<Cell> cells) implements Row {

  public static ReportRowBuilder builder() {
    return new ReportRowBuilder(DefaultValidator.create());
  }

  @Override
  public List<String> values() {
    return cells().stream().map(Cell::string).toList();
  }

  @Override
  public List<String> headers() {
    return cells().isEmpty()
        ? List.of()
        : cells().stream().map(Cell::header).map(Header::name).toList();
  }
}
