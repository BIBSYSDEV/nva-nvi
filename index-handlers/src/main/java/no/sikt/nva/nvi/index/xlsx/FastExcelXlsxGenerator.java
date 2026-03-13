package no.sikt.nva.nvi.index.xlsx;

import static java.util.Objects.isNull;
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
import nva.commons.core.StringUtils;
import org.dhatim.fastexcel.Workbook;
import org.dhatim.fastexcel.Worksheet;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class FastExcelXlsxGenerator implements ReportGenerator {

  private static final Logger logger = LoggerFactory.getLogger(FastExcelXlsxGenerator.class);
  private static final String LINE_BREAK = "\n";
  private static final Encoder ENCODER = Base64.getEncoder();
  private static final String NVI = "NVI";
  private static final String REPORT = "report";
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
    for (int columnIndex = 0; columnIndex < cells.size(); columnIndex++) {
      var header = InstitutionReportHeader.getHeader(columnIndex);
      var cellValue = cells.get(columnIndex);

      if (isNull(cellValue)) {
        sheet.value(rowIndex, columnIndex, StringUtils.EMPTY_STRING);
        continue;
      }
      if (!header.getValue().equals(cellValue) && header.isNumeric()) {
        var numericValue = Double.parseDouble(cellValue);
        var normalizedValue = adjustScaleAndRoundingMode(BigDecimal.valueOf(numericValue));
        sheet.value(rowIndex, columnIndex, normalizedValue.doubleValue());
      } else {
        sheet.value(rowIndex, columnIndex, cellValue);
      }
    }
  }

  public byte[] toWorkbookByteArray() {
    var byteArrayOutputStream = new ByteArrayOutputStream();
    try (var workbook = new Workbook(byteArrayOutputStream, NVI, null)) {
      var sheet = workbook.newWorksheet(REPORT);
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
