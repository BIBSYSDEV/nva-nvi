package no.sikt.nva.nvi.index.report;

import java.util.List;
import no.sikt.nva.nvi.index.xlsx.ExcelWorkbookGenerator;

public class ReportGenerator<T extends ReportRow> {

  private final List<String> headers;

  public ReportGenerator(List<String> headers) {
    this.headers = headers;
  }

  public String generate(List<T> rows) {
    var data = rows.stream().map(ReportRow::toRow).toList();
    return new ExcelWorkbookGenerator(headers, data).toBase64EncodedString();
  }
}
