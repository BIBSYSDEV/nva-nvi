package no.sikt.nva.nvi.index.xlsx;

import static no.sikt.nva.nvi.index.report.model.ReportHeader.ARSTALL;
import static no.sikt.nva.nvi.index.report.model.ReportHeader.PUBLISERINGSPOENG;
import static no.sikt.nva.nvi.index.report.model.RowFixtures.completeReportRow;
import static org.assertj.core.api.Assertions.assertThat;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.util.List;
import no.sikt.nva.nvi.index.report.model.Cell;
import no.sikt.nva.nvi.index.report.model.Header;
import no.sikt.nva.nvi.index.report.model.NumericCell;
import no.sikt.nva.nvi.index.report.model.Row;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.junit.jupiter.api.Test;

class FastExcelXlsxGeneratorTest {

  @Test
  void shouldWriteHeadersToFirstRow() throws IOException {
    var row = completeReportRow();
    var bytes = new FastExcelXlsxGenerator(List.of(row)).toWorkbookByteArray();

    try (var workbook = new XSSFWorkbook(new ByteArrayInputStream(bytes))) {
      var sheet = workbook.getSheetAt(0);
      var headerRow = sheet.getRow(0);
      assertThat(headerRow.getCell(0).getStringCellValue()).isEqualTo("ARSTALL");
      assertThat(headerRow.getCell(1).getStringCellValue()).isEqualTo("NVAID");
    }
  }

  @Test
  void shouldWriteStringValuesForStringCells() throws IOException {
    var row = completeReportRow();
    var year = getCell(row, ARSTALL).string();
    var bytes = new FastExcelXlsxGenerator(List.of(row)).toWorkbookByteArray();

    try (var workbook = new XSSFWorkbook(new ByteArrayInputStream(bytes))) {
      var sheet = workbook.getSheetAt(0);
      assertThat(sheet.getRow(1).getCell(0).getStringCellValue()).isEqualTo(year);
    }
  }

  private static Cell getCell(Row row, Header header) {
    return row.cells().stream()
        .filter(cell -> cell.header().equals(header))
        .findAny()
        .orElseThrow();
  }

  @Test
  void shouldWriteNumericValuesForNumericCells() throws IOException {
    var row = completeReportRow();
    var publishingPoints = ((NumericCell) getCell(row, PUBLISERINGSPOENG)).value();
    var bytes = new FastExcelXlsxGenerator(List.of(row)).toWorkbookByteArray();

    try (var workbook = new XSSFWorkbook(new ByteArrayInputStream(bytes))) {
      var sheet = workbook.getSheetAt(0);
      var sheetRow = sheet.getRow(1);
      assertThat(sheetRow.getCell(sheetRow.getLastCellNum() - 1).getNumericCellValue())
          .isEqualTo(publishingPoints.doubleValue());
    }
  }
}
