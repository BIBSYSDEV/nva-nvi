package no.sikt.nva.nvi.report.generators;

import static no.sikt.nva.nvi.report.model.authorshares.ReportHeader.ARSTALL;
import static no.sikt.nva.nvi.report.model.authorshares.ReportHeader.PUBLISERINGSPOENG;
import static no.unit.nva.testutils.RandomDataGenerator.randomString;

import java.io.IOException;
import java.util.List;
import no.sikt.nva.nvi.report.generators.utils.XlsxReader;
import no.sikt.nva.nvi.report.model.Cell;
import no.sikt.nva.nvi.report.model.Header;
import no.sikt.nva.nvi.report.model.NumericCell;
import no.sikt.nva.nvi.report.model.Row;
import no.sikt.nva.nvi.report.model.RowFixtures;
import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.Test;

class XlsxGeneratorTest {

  @Test
  void shouldWriteHeadersToFirstRow() throws IOException {
    var row = RowFixtures.completeReportRow(randomString());
    var rows = XlsxReader.toRows(new XlsxGenerator(List.of(row)).toWorkbookByteArray());

    Assertions.assertThat(rows.getFirst()).containsSequence("ARSTALL", "NVAID");
  }

  @Test
  void shouldWriteStringValuesForStringCells() throws IOException {
    var row = RowFixtures.completeReportRow(randomString());
    var year = getCell(row, ARSTALL).string();
    var rows = XlsxReader.toRows(new XlsxGenerator(List.of(row)).toWorkbookByteArray());

    Assertions.assertThat(rows.get(1).getFirst()).isEqualTo(year);
  }

  @Test
  void shouldWriteNumericValuesForNumericCells() throws IOException {
    var row = RowFixtures.completeReportRow(randomString());
    var publishingPoints = ((NumericCell) getCell(row, PUBLISERINGSPOENG)).value();
    var rows = XlsxReader.toRows(new XlsxGenerator(List.of(row)).toWorkbookByteArray());

    Assertions.assertThat(Double.parseDouble(rows.get(1).getLast()))
        .isEqualTo(publishingPoints.doubleValue());
  }

  private static Cell getCell(Row row, Header header) {
    return row.cells().stream()
        .filter(cell -> cell.header().equals(header))
        .findAny()
        .orElseThrow();
  }
}
