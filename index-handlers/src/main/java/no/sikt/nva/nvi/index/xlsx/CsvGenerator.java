package no.sikt.nva.nvi.index.xlsx;

import com.opencsv.CSVWriter;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.OutputStreamWriter;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import no.sikt.nva.nvi.index.report.model.Row;
import nva.commons.core.JacocoGenerated;

public class CsvGenerator implements ReportGenerator {

  private static final char SEPARATOR = ';';
  private static final String UTF8_BOM = "\uFEFF";
  private final List<String> headers;
  private final List<List<String>> data;

  public CsvGenerator(List<String> headers, List<List<String>> data) {
    this.headers = headers;
    this.data = data;
  }

  public CsvGenerator(List<Row> rows) {
    this.headers = rows.isEmpty() ? Collections.emptyList() : rows.getFirst().headers();
    this.data = rows.stream().map(Row::values).toList();
  }

  @JacocoGenerated
  @Override
  public int hashCode() {
    return Objects.hash(headers, data);
  }

  @JacocoGenerated
  @Override
  public boolean equals(Object o) {
    if (!(o instanceof CsvGenerator that)) {
      return false;
    }
    return Objects.equals(headers, that.headers) && Objects.equals(data, that.data);
  }

  @Override
  public byte[] toWorkbookByteArray() {
    try (var byteStream = new ByteArrayOutputStream();
        var outputStreamWriter = new OutputStreamWriter(byteStream, StandardCharsets.UTF_8);
        var writer =
            new CSVWriter(
                outputStreamWriter,
                SEPARATOR,
                CSVWriter.DEFAULT_QUOTE_CHARACTER,
                CSVWriter.DEFAULT_ESCAPE_CHARACTER,
                CSVWriter.DEFAULT_LINE_END)) {
      outputStreamWriter.write(UTF8_BOM);
      writer.writeNext(headers.toArray(String[]::new));
      for (List<String> row : data) {
        writer.writeNext(toAlignedRow(row));
      }
      writer.flush();
      return byteStream.toByteArray();
    } catch (IOException e) {
      throw new IllegalStateException("Failed to write CSV uri", e);
    }
  }

  private String[] toAlignedRow(List<String> row) {
    var aligned = new ArrayList<>(Collections.nCopies(headers.size(), ""));
    for (int i = 0; i < Math.min(row.size(), headers.size()); i++) {
      var value = row.get(i);
      if (value != null) {
        aligned.set(i, value.replaceAll("\\R", ""));
      }
    }
    return aligned.toArray(String[]::new);
  }
}
