package no.sikt.nva.nvi.index.xlsx;

import static no.sikt.nva.nvi.common.utils.DecimalUtils.adjustScaleAndRoundingMode;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.math.BigDecimal;
import java.util.Base64;
import java.util.Base64.Encoder;
import java.util.List;
import java.util.Objects;
import no.sikt.nva.nvi.index.model.report.InstitutionReportHeader;
import nva.commons.core.JacocoGenerated;
import org.dhatim.fastexcel.Workbook;
import org.dhatim.fastexcel.Worksheet;
import org.slf4j.Logger;

public class FastExcelXlsxGenerator implements XlsxGenerator {

  private static final Logger logger =
      org.slf4j.LoggerFactory.getLogger(FastExcelXlsxGenerator.class);
  private static final String LINE_BREAK = "\n";
  private static final Encoder ENCODER = Base64.getEncoder();
  private final List<String> headers;
  private final List<List<String>> data;

  public FastExcelXlsxGenerator(List<String> headers, List<List<String>> data) {
    this.headers = headers;
    this.data = data;
  }

  @Override
  public String toBase64EncodedString() {
    return ENCODER.encodeToString(this.toWorkbookByteArray());
  }

  @JacocoGenerated
  @Override
  public int hashCode() {
    return Objects.hash(headers, data);
  }

  @JacocoGenerated
  @Override
  public boolean equals(Object o) {
    if (!(o instanceof FastExcelXlsxGenerator that)) {
      return false;
    }
    return Objects.equals(headers, that.headers) && Objects.equals(data, that.data);
  }

  @JacocoGenerated
  @Override
  public String toString() {
    var builder = new StringBuilder();
    builder.append(headers).append(LINE_BREAK);
    for (List<String> row : data) {
      builder.append(row).append(LINE_BREAK);
    }
    return builder.toString();
  }

  private static void addCells(Worksheet sheet, int rowIndex, List<String> cells) {
    for (int colIndex = 0; colIndex < cells.size(); colIndex++) {
      var header = InstitutionReportHeader.getHeader(colIndex);
      var cellValue = cells.get(colIndex);

      if (cellValue == null) {
        sheet.value(rowIndex, colIndex, "");
        continue;
      }

      var isHeaderCell = header.getValue().equals(cellValue);
      if (!isHeaderCell && header.isNumeric()) {
        var numericValue = Double.parseDouble(cellValue);
        var normalizedValue = adjustScaleAndRoundingMode(BigDecimal.valueOf(numericValue));
        sheet.value(rowIndex, colIndex, normalizedValue.doubleValue());
      } else {
        sheet.value(rowIndex, colIndex, cellValue);
      }
    }
  }

  private byte[] toWorkbookByteArray() {
    var byteArrayOutputStream = new ByteArrayOutputStream();
    try (var workbook = new Workbook(byteArrayOutputStream, "NVI", "1.0")) {
      var sheet = workbook.newWorksheet("Sheet1");
      addHeaders(sheet);
      addData(sheet);
    } catch (IOException e) {
      logger.error("Something went wrong creating Excel workbook ", e);
      throw new RuntimeException(e);
    }
    return byteArrayOutputStream.toByteArray();
  }

  private void addHeaders(Worksheet sheet) {
    addCells(sheet, 0, headers);
  }

  private void addData(Worksheet sheet) {
    for (int rowIndex = 0; rowIndex < data.size(); rowIndex++) {
      addCells(sheet, rowIndex + 1, data.get(rowIndex));
    }
  }
}
