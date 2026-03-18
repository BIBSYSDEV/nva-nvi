package no.sikt.nva.nvi.report.generators;

import static no.unit.nva.testutils.RandomDataGenerator.randomString;
import static nva.commons.core.StringUtils.EMPTY_STRING;
import static org.junit.jupiter.api.Assertions.assertEquals;

import com.opencsv.CSVParserBuilder;
import com.opencsv.CSVReaderBuilder;
import com.opencsv.exceptions.CsvException;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.List;
import no.sikt.nva.nvi.report.model.RowFixtures;
import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.Test;

class CsvGeneratorTest {

  private static final int PUBLICATION_ID_COLUMN = 1;

  @Test
  void shouldCreateCsvWithHeaders() {
    var row = RowFixtures.completeReportRow(randomString());
    var rows = parseCsv(new CsvGenerator(List.of(row)));

    Assertions.assertThat(rows.getFirst()).isEqualTo(row.headers());
  }

  @Test
  void shouldCreateCsvWithValueRows() {
    var row = RowFixtures.completeReportRow(randomString());
    var rows = parseCsv(new CsvGenerator(List.of(row)));

    Assertions.assertThat(rows.get(1)).isEqualTo(row.values());
  }

  @Test
  void shouldReplaceNullValuesWithEmptyString() {
    var row = RowFixtures.completeReportRow(null);
    var dataColumns = parseCsv(new CsvGenerator(List.of(row))).get(1);

    assertEquals(EMPTY_STRING, dataColumns.get(PUBLICATION_ID_COLUMN));
  }

  @Test
  void rowMissingValuesShouldHaveTheSameLengthAsHeaders() {
    var row = RowFixtures.completeReportRow(null);
    var dataColumns = parseCsv(new CsvGenerator(List.of(row))).get(1);

    assertEquals(row.headers().size(), dataColumns.size());
  }

  @Test
  void shouldEscapeDoubleQuotesInValues() {
    var valueWithQuote = "value\"with\"quotes";
    var row = RowFixtures.completeReportRow(valueWithQuote);
    var dataColumns = parseCsv(new CsvGenerator(List.of(row))).get(1);

    assertEquals(valueWithQuote, dataColumns.get(PUBLICATION_ID_COLUMN));
  }

  @Test
  void shouldIgnoreLineBreaksInValues() {
    var row = RowFixtures.completeReportRow("value\nwith\r\nbreaks");
    var dataColumns = parseCsv(new CsvGenerator(List.of(row))).get(1);

    assertEquals("valuewithbreaks", dataColumns.get(PUBLICATION_ID_COLUMN));
  }

  @Test
  void shouldCreateCsvWithOneLineForEachRow() {
    var rows =
        parseCsv(
            new CsvGenerator(
                List.of(
                    RowFixtures.completeReportRow(randomString()),
                    RowFixtures.completeReportRow(randomString()))));

    Assertions.assertThat(rows).hasSize(3);
  }

  private static List<List<String>> parseCsv(CsvGenerator generator) {
    var csvBytes = generator.toWorkbookByteArray();
    try (var reader =
        new CSVReaderBuilder(
                new InputStreamReader(new ByteArrayInputStream(csvBytes), StandardCharsets.UTF_8))
            .withCSVParser(new CSVParserBuilder().withSeparator(';').build())
            .build()) {
      var rows = reader.readAll();
      if (!rows.isEmpty() && rows.getFirst()[0].startsWith("\uFEFF")) {
        rows.getFirst()[0] = rows.getFirst()[0].substring(1);
      }
      return rows.stream().map(Arrays::asList).toList();
    } catch (IOException | CsvException e) {
      throw new RuntimeException(e);
    }
  }
}
