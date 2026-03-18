package no.sikt.nva.nvi.index.report.model;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

public abstract sealed class RowBuilder permits ReportRowBuilder {

  private final List<Cell> cells;

  protected RowBuilder() {
    this.cells = new ArrayList<>();
  }

  protected abstract void validate();

  protected List<Cell> cells() {
    return cells;
  }

  public Row build() {
    var sortedCells =
        cells.stream().sorted(Comparator.comparingInt(cell -> cell.header().ordinal())).toList();
    Row row = () -> sortedCells;
    validate();
    return row;
  }

  protected void withCell(Cell cell) {
    cells.add(cell);
  }
}
