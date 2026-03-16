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
import java.util.stream.IntStream;
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
        .filter(component -> component.isAnnotationPresent(Column.class))
        .toList();
  }

  private void addHeaders(Worksheet sheet, List<RecordComponent> columns) {
    IntStream.range(0, columns.size())
        .forEach(i -> sheet.value(0, i, columns.get(i).getAnnotation(Column.class).header()));
  }

  private void addData(Worksheet sheet, List<RecordComponent> columns) {
    IntStream.range(0, rows.size())
        .forEach(rowIndex -> addRow(sheet, rowIndex + 1, rows.get(rowIndex), columns));
  }

  private void addRow(Worksheet sheet, int rowIndex, T row, List<RecordComponent> columns) {
    IntStream.range(0, columns.size())
        .forEach(
            i -> {
              var column = columns.get(i);
              var value = attempt(() -> (String) column.getAccessor().invoke(row)).orElseThrow();
              setCellValue(sheet, rowIndex, i, column.getAnnotation(Column.class).numeric(), value);
            });
  }

private void setCellValue(Worksheet sheet, int row, int col, String value) {
    var cellValue = resolveValue(value);
    sheet.value(row, col, cellValue);
}

private Object resolveValue(String value) {
    var coalesced = isNull(value) ? StringUtils.EMPTY_STRING : value;
    return parseNumeric(coalesced).map(BigDecimal::doubleValue).orElse(coalesced);
}

private Optional<BigDecimal> parseNumeric(String value) {
    try {
        return Optional.of(adjustScaleAndRoundingMode(new BigDecimal(value)));
    } catch (NumberFormatException e) {
        return Optional.empty();
    }
}
}
