package no.sikt.nva.nvi.report.generators.utils;

import com.opencsv.CSVParserBuilder;
import com.opencsv.CSVReader;
import com.opencsv.CSVReaderBuilder;
import com.opencsv.exceptions.CsvException;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Optional;
import java.util.stream.IntStream;
import no.sikt.nva.nvi.report.model.Cell;
import no.sikt.nva.nvi.report.model.Header;
import no.sikt.nva.nvi.report.model.Row;

public final class CsvReader {

  private static final char CSV_SEPARATOR = ';';

  private CsvReader() {}

  public static <T extends Enum<T> & Header> List<Row> parseCsvToRows(byte[] csv, Class<T> header) {
    try (var reader = csvReader(csv)) {
      var rows = reader.readAll();
      var headers = List.of(header.getEnumConstants());
      return rows.stream().map(values -> toRow(List.of(values), headers)).toList();
    } catch (IOException | CsvException e) {
      throw new RuntimeException(e);
    }
  }

  private static CSVReader csvReader(byte[] csv) {
    return new CSVReaderBuilder(
            new InputStreamReader(new ByteArrayInputStream(csv), StandardCharsets.UTF_8))
        .withCSVParser(new CSVParserBuilder().withSeparator(CSV_SEPARATOR).build())
        .build();
  }

  private static <T extends Header> Row toRow(List<String> values, List<T> headers) {
    return () ->
        IntStream.range(0, values.size())
            .mapToObj(i -> Cell.of(headers.get(i), values.get(i)))
            .toList();
  }

  public static Optional<Cell> getCell(Row row, Header header) {
    return row.cells().stream().filter(cell -> header.equals(cell.header())).findFirst();
  }
}
