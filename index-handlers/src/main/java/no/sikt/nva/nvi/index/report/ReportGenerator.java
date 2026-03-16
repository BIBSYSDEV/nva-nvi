package no.sikt.nva.nvi.index.report;

import java.util.List;
import no.sikt.nva.nvi.index.xlsx.FastExcelXlsxGenerator;

public class ReportGenerator<T extends ReportRow> {

  private final Class<T> rowClass;

  public ReportGenerator(Class<T> rowClass) {
    this.rowClass = rowClass;
  }

  public byte[] generate(List<T> rows) {
    return new FastExcelXlsxGenerator<>(rowClass, rows).toWorkbookByteArray();
  }
}
