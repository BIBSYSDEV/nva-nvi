package no.sikt.nva.nvi.report.generators;

import static no.sikt.nva.nvi.report.generators.utils.CsvReader.getCell;
import static no.sikt.nva.nvi.report.generators.utils.CsvReader.parseCsvToRows;
import static no.sikt.nva.nvi.report.model.institutionreport.ReportHeader.NVAID;
import static no.unit.nva.testutils.RandomDataGenerator.randomString;
import static nva.commons.core.StringUtils.EMPTY_STRING;
import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.List;
import no.sikt.nva.nvi.report.model.RowFixtures;
import no.sikt.nva.nvi.report.model.institutionreport.ReportHeader;
import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.Test;

class CsvGeneratorTest {

  @Test
  void shouldCreateCsvWithHeaders() {
    var row = RowFixtures.completeReportRow(randomString());
    var rows =
        parseCsvToRows(new CsvGenerator(List.of(row)).toWorkbookByteArray(), ReportHeader.class);

    Assertions.assertThat(rows.getFirst().headers()).isEqualTo(row.headers());
  }

  @Test
  void shouldCreateCsvWithValueRows() {
    var row = RowFixtures.completeReportRow(randomString());
    var report = new CsvGenerator(List.of(row)).toWorkbookByteArray();
    var rowsFromReport = parseCsvToRows(report, ReportHeader.class);

    Assertions.assertThat(rowsFromReport.get(1).values()).isEqualTo(row.values());
  }

  @Test
  void shouldReplaceNullValuesWithEmptyString() {
    var row = RowFixtures.completeReportRow(null);
    var report = new CsvGenerator(List.of(row)).toWorkbookByteArray();
    var rowFromReport = parseCsvToRows(report, ReportHeader.class).get(1);

    assertEquals(EMPTY_STRING, getCell(rowFromReport, NVAID).orElseThrow().string());
  }

  @Test
  void rowMissingValuesShouldHaveTheSameLengthAsHeaders() {
    var row = RowFixtures.completeReportRow(null);
    var report = new CsvGenerator(List.of(row)).toWorkbookByteArray();
    var rowFromReport = parseCsvToRows(report, ReportHeader.class).get(1);

    assertEquals(row.headers().size(), rowFromReport.cells().size());
  }

  @Test
  void shouldEscapeDoubleQuotesInValues() {
    var valueWithQuote = "value\"with\"quotes";
    var row = RowFixtures.completeReportRow(valueWithQuote);
    var report = new CsvGenerator(List.of(row)).toWorkbookByteArray();
    var rowFromReport = parseCsvToRows(report, ReportHeader.class).get(1);

    assertEquals(valueWithQuote, getCell(rowFromReport, NVAID).orElseThrow().string());
  }

  @Test
  void shouldIgnoreLineBreaksInValues() {
    var row = RowFixtures.completeReportRow("value\nwith\r\nbreaks");
    var report = new CsvGenerator(List.of(row)).toWorkbookByteArray();
    var rowFromReport = parseCsvToRows(report, ReportHeader.class).get(1);

    assertEquals("valuewithbreaks", getCell(rowFromReport, NVAID).orElseThrow().string());
  }

  @Test
  void shouldCreateCsvWithOneLineForEachRow() {
    var rows =
        parseCsvToRows(
            new CsvGenerator(
                    List.of(
                        RowFixtures.completeReportRow(randomString()),
                        RowFixtures.completeReportRow(randomString())))
                .toWorkbookByteArray(),
            ReportHeader.class);

    Assertions.assertThat(rows).hasSize(3);
  }
}
