package no.sikt.nva.nvi.index.report.model;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

public record Row(List<Cell> cells) {

  public List<String> values() {
    return cells().stream().map(Cell::string).toList();
  }

  public List<String> headers() {
    return cells().isEmpty()
        ? List.of()
        : cells().stream().map(Cell::header).map(Header::name).toList();
  }

  public static Builder builder() {
    return new Builder();
  }

  public static final class Builder {
    private final List<Cell> cells = new ArrayList<>();

    private Builder() {}

    public Builder withCell(Cell cell) {
      cells.add(cell);
      return this;
    }

    public Row build() {
      var cellsInHeaderDeclarationOrder = sortCells();
      return new Row(cellsInHeaderDeclarationOrder);
    }

    private List<Cell> sortCells() {
      return cells.stream()
          .sorted(Comparator.comparingInt(cell -> cell.header().ordinal()))
          .toList();
    }
  }
}
