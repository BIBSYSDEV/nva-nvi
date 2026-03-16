package no.sikt.nva.nvi.index.xlsx;

import static java.util.Objects.isNull;
import static no.sikt.nva.nvi.common.utils.DecimalUtils.adjustScaleAndRoundingMode;
import static nva.commons.core.attempt.Try.attempt;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.lang.reflect.RecordComponent;
import java.math.BigDecimal;
import java.util.Arrays;
import java.util.List;
import no.sikt.nva.nvi.index.report.Column;
import no.sikt.nva.nvi.index.report.ReportRow;
import nva.commons.core.StringUtils;
import org.dhatim.fastexcel.Workbook;
import org.dhatim.fastexcel.Worksheet;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class FastExcelXlsxGenerator<T extends ReportRow> implements ReportGenerator {

  private static final Logger logger = LoggerFactory.getLogger(FastExcelXlsxGenerator.class);
  private static final String NVI = "NVI";
  private static final String REPORT = "report";
  private final Class<T> rowClass;
  private final List<T> rows;

  public FastExcelXlsxGenerator(Class<T> rowClass, List<T> rows) {
    this.rowClass = rowClass;
    this.rows = rows;
  }

  @Override
  public byte[] toWorkbookByteArray() {
    var byteArrayOutputStream = new ByteArrayOutputStream();
    try (var workbook = new Workbook(byteArrayOutputStream, NVI, null)) {
      var sheet = workbook.newWorksheet(REPORT);
      var columns = annotatedColumns();
      addHeaders(sheet, columns);
      addData(sheet, columns);
    } catch (IOException e) {
      logger.error("Something went wrong creating Excel workbook ", e);
      throw new RuntimeException(e);
    }
    return byteArrayOutputStream.toByteArray();
  }

  private List<RecordComponent> annotatedColumns() {
    return Arrays.stream(rowClass.getRecordComponents())
        .filter(c -> c.isAnnotationPresent(Column.class))
        .toList();
  }

  private void addHeaders(Worksheet sheet, List<RecordComponent> columns) {
    for (int i = 0; i < columns.size(); i++) {
      sheet.value(0, i, columns.get(i).getAnnotation(Column.class).header());
    }
  }

  private void addData(Worksheet sheet, List<RecordComponent> columns) {
    for (int rowIndex = 0; rowIndex < rows.size(); rowIndex++) {
      addRow(sheet, rowIndex + 1, rows.get(rowIndex), columns);
    }
  }

  private void addRow(Worksheet sheet, int rowIndex, T row, List<RecordComponent> columns) {
    for (int columnIndex = 0; columnIndex < columns.size(); columnIndex++) {
      var column = columns.get(columnIndex);
      var cellValue = attempt(() -> (String) column.getAccessor().invoke(row)).orElseThrow();
      if (isNull(cellValue)) {
        sheet.value(rowIndex, columnIndex, StringUtils.EMPTY_STRING);
      } else if (column.getAnnotation(Column.class).numeric()) {
        var numericValue = Double.parseDouble(cellValue);
        var normalizedValue = adjustScaleAndRoundingMode(BigDecimal.valueOf(numericValue));
        sheet.value(rowIndex, columnIndex, normalizedValue.doubleValue());
      } else {
        sheet.value(rowIndex, columnIndex, cellValue);
      }
    }
  }
}
