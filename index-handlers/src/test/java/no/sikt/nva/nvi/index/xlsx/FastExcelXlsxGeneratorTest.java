package no.sikt.nva.nvi.index.xlsx;

import static no.unit.nva.testutils.RandomDataGenerator.randomString;
import static org.assertj.core.api.Assertions.assertThat;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.util.List;
import no.sikt.nva.nvi.index.report.Column;
import no.sikt.nva.nvi.index.report.ReportRow;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.junit.jupiter.api.Test;

class FastExcelXlsxGeneratorTest {

  record TestRow(
      @Column(header = "NAME") String name, @Column(header = "SCORE", numeric = true) String score)
      implements ReportRow {}

  @Test
  void shouldWriteHeadersToFirstRow() throws IOException {
    var row = new TestRow(randomString(), "1.5");
    var bytes = new FastExcelXlsxGenerator<>(TestRow.class, List.of(row)).toWorkbookByteArray();

    try (var workbook = new XSSFWorkbook(new ByteArrayInputStream(bytes))) {
      var sheet = workbook.getSheetAt(0);
      var headerRow = sheet.getRow(0);
      assertThat(headerRow.getCell(0).getStringCellValue()).isEqualTo("NAME");
      assertThat(headerRow.getCell(1).getStringCellValue()).isEqualTo("SCORE");
    }
  }

  @Test
  void shouldWriteStringValuesForNonNumericColumns() throws IOException {
    var name = randomString();
    var row = new TestRow(name, "2.0");
    var bytes = new FastExcelXlsxGenerator<>(TestRow.class, List.of(row)).toWorkbookByteArray();

    try (var workbook = new XSSFWorkbook(new ByteArrayInputStream(bytes))) {
      var sheet = workbook.getSheetAt(0);
      var dataRow = sheet.getRow(1);
      assertThat(dataRow.getCell(0).getStringCellValue()).isEqualTo(name);
    }
  }

  @Test
  void shouldWriteNumericValuesForNumericColumns() throws IOException {
    var row = new TestRow(randomString(), "3.14");
    var bytes = new FastExcelXlsxGenerator<>(TestRow.class, List.of(row)).toWorkbookByteArray();

    try (var workbook = new XSSFWorkbook(new ByteArrayInputStream(bytes))) {
      var sheet = workbook.getSheetAt(0);
      var dataRow = sheet.getRow(1);
      assertThat(dataRow.getCell(1).getNumericCellValue()).isEqualTo(3.14);
    }
  }
}
