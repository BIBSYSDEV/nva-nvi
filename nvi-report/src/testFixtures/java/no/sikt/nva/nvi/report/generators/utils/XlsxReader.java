package no.sikt.nva.nvi.report.generators.utils;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.util.List;
import java.util.stream.IntStream;
import org.dhatim.fastexcel.reader.ReadableWorkbook;

public final class XlsxReader {

  private XlsxReader() {}

  public static List<List<String>> toRows(byte[] bytes) throws IOException {
    try (var workbook = new ReadableWorkbook(new ByteArrayInputStream(bytes));
        var rows = workbook.getFirstSheet().openStream()) {
      return rows.map(
              row -> IntStream.range(0, row.getCellCount()).mapToObj(row::getCellText).toList())
          .toList();
    }
  }
}
