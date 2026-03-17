package no.sikt.nva.nvi.index.xlsx;

import static no.unit.nva.testutils.RandomDataGenerator.randomString;
import static org.assertj.core.api.Assertions.assertThat;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.math.BigDecimal;
import java.util.List;
import no.sikt.nva.nvi.index.report.model.Cell;
import no.sikt.nva.nvi.index.report.model.Header;
import no.sikt.nva.nvi.index.report.model.Row;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.junit.jupiter.api.Test;

class FastExcelXlsxGeneratorTest {

  @Test
  void shouldWriteHeadersToFirstRow() throws IOException {
    var row =
        Row.builder()
            .withCell(Cell.of(Header.ARSTALL, "2024"))
            .withCell(Cell.of(Header.VEKTINGSTALL, BigDecimal.ONE))
            .build();
    var bytes = new FastExcelXlsxGenerator(List.of(row)).toWorkbookByteArray();

    try (var workbook = new XSSFWorkbook(new ByteArrayInputStream(bytes))) {
      var sheet = workbook.getSheetAt(0);
      var headerRow = sheet.getRow(0);
      assertThat(headerRow.getCell(0).getStringCellValue()).isEqualTo("ARSTALL");
      assertThat(headerRow.getCell(1).getStringCellValue()).isEqualTo("VEKTINGSTALL");
    }
  }

  @Test
  void shouldWriteStringValuesForStringCells() throws IOException {
    var name = randomString();
    var row = Row.builder().withCell(Cell.of(Header.TITTEL, name)).build();
    var bytes = new FastExcelXlsxGenerator(List.of(row)).toWorkbookByteArray();

    try (var workbook = new XSSFWorkbook(new ByteArrayInputStream(bytes))) {
      var sheet = workbook.getSheetAt(0);
      assertThat(sheet.getRow(1).getCell(0).getStringCellValue()).isEqualTo(name);
    }
  }

  @Test
  void shouldWriteNumericValuesForNumericCells() throws IOException {
    var row = Row.builder().withCell(Cell.of(Header.VEKTINGSTALL, new BigDecimal("3.14"))).build();
    var bytes = new FastExcelXlsxGenerator(List.of(row)).toWorkbookByteArray();

    try (var workbook = new XSSFWorkbook(new ByteArrayInputStream(bytes))) {
      var sheet = workbook.getSheetAt(0);
      assertThat(sheet.getRow(1).getCell(0).getNumericCellValue()).isEqualTo(3.14);
    }
  }
}
