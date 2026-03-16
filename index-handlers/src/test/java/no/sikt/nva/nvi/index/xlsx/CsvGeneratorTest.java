package no.sikt.nva.nvi.index.xlsx;

import static no.sikt.nva.nvi.test.TestUtils.randomBigDecimal;
import static no.unit.nva.testutils.RandomDataGenerator.randomString;
import static org.assertj.core.api.Assertions.assertThat;

import com.opencsv.CSVParserBuilder;
import com.opencsv.CSVReaderBuilder;
import com.opencsv.exceptions.CsvException;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Base64;
import java.util.Collections;
import java.util.List;
import no.sikt.nva.nvi.index.model.report.InstitutionReportHeader;
import nva.commons.core.StringUtils;
import org.junit.jupiter.api.Test;

class CsvGeneratorTest {

  private static final List<String> HEADERS = InstitutionReportHeader.getOrderedValues();

  @Test
  void shouldCreateCsvWithHeaders() {
    var rows = parseCsv(new CsvGenerator(HEADERS, List.of()));

    assertThat(rows.getFirst()).isEqualTo(HEADERS);
  }

  @Test
  void shouldCreateCsvWithValueRows() {
    var row = randomRow();
    var rows = parseCsv(new CsvGenerator(HEADERS, List.of(row)));

    assertThat(rows.get(1)).isEqualTo(row);
  }

  @Test
  void shouldReplaceNullValuesWithEmptyString() {
    var row = new ArrayList<String>(Collections.nCopies(HEADERS.size(), null));
    row.set(InstitutionReportHeader.REPORTING_YEAR.getOrder(), randomString());
    var dataColumns = parseCsv(new CsvGenerator(HEADERS, List.of(row))).get(1);

    assertThat(dataColumns).hasSize(HEADERS.size());
    assertThat(dataColumns.get(InstitutionReportHeader.REPORTING_YEAR.getOrder())).isNotEmpty();
    assertThat(dataColumns.get(InstitutionReportHeader.PUBLICATION_IDENTIFIER.getOrder()))
        .isEmpty();
  }

  @Test
  void rowMissingValuesShouldHaveTheSameLengthAsHeaders() {
    var dataColumns = parseCsv(new CsvGenerator(HEADERS, List.of(List.of(randomString())))).get(1);

    assertThat(dataColumns).hasSize(HEADERS.size());
  }

  @Test
  void shouldEscapeDoubleQuotesInValues() {
    var valueWithQuote = "value\"with\"quotes";
    var row = new ArrayList<>(Collections.nCopies(HEADERS.size(), StringUtils.EMPTY_STRING));
    row.set(InstitutionReportHeader.REPORTING_YEAR.getOrder(), valueWithQuote);
    var dataColumns = parseCsv(new CsvGenerator(HEADERS, List.of(row))).get(1);

    assertThat(dataColumns.get(InstitutionReportHeader.REPORTING_YEAR.getOrder()))
        .isEqualTo(valueWithQuote);
  }

  @Test
  void shouldIgnoreLineBreaksInValues() {
    var row = new ArrayList<>(Collections.nCopies(HEADERS.size(), StringUtils.EMPTY_STRING));
    row.set(InstitutionReportHeader.REPORTING_YEAR.getOrder(), "value\nwith\r\nbreaks");
    var dataColumns = parseCsv(new CsvGenerator(HEADERS, List.of(row))).get(1);

    assertThat(dataColumns.get(InstitutionReportHeader.REPORTING_YEAR.getOrder()))
        .isEqualTo("valuewithbreaks");
  }

  @Test
  void shouldCreateCsvWithOneLineForEachRow() {
    var rows = parseCsv(new CsvGenerator(HEADERS, List.of(randomRow(), randomRow())));

    assertThat(rows).hasSize(3);
  }

  private static List<List<String>> parseCsv(CsvGenerator generator) {
    var csvBytes = Base64.getDecoder().decode(generator.toBase64EncodedString());
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

  private static List<String> randomRow() {
    var row = new ArrayList<>(Collections.nCopies(HEADERS.size(), StringUtils.EMPTY_STRING));
    row.set(InstitutionReportHeader.REPORTING_YEAR.getOrder(), randomString());
    row.set(InstitutionReportHeader.PUBLICATION_IDENTIFIER.getOrder(), randomString());
    row.set(
        InstitutionReportHeader.PUBLICATION_CHANNEL_LEVEL_POINTS.getOrder(),
        randomBigDecimal().toString());
    row.set(
        InstitutionReportHeader.INTERNATIONAL_COLLABORATION_FACTOR.getOrder(),
        randomBigDecimal().toString());
    row.set(InstitutionReportHeader.CREATOR_SHARE_COUNT.getOrder(), randomBigDecimal().toString());
    row.set(
        InstitutionReportHeader.POINTS_FOR_AFFILIATION.getOrder(), randomBigDecimal().toString());
    return row;
  }
}
