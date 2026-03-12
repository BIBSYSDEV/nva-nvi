package no.sikt.nva.nvi.index.report;

import java.util.Arrays;
import java.util.List;
import no.sikt.nva.nvi.index.xlsx.FastExcelXlsxGenerator;

public class ReportGenerator<T extends ReportRow> {

  private final Class<T> rowClass;

  public ReportGenerator(Class<T> rowClass) {
    this.rowClass = rowClass;
  }

  public String generate(List<T> rows) {
    var headers = headersFor(rowClass);
    var data = rows.stream().map(ReportRow::toRow).toList();
    return new FastExcelXlsxGenerator(headers, data).toBase64EncodedString();
  }

  private static <T extends ReportRow> List<String> headersFor(Class<T> clazz) {
    return Arrays.stream(clazz.getRecordComponents())
        .filter(record -> record.isAnnotationPresent(Column.class))
        .map(record -> record.getAnnotation(Column.class).header())
        .toList();
  }
}
