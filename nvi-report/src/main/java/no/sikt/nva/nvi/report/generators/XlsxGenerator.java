package no.sikt.nva.nvi.report.generators;

import static java.util.Objects.isNull;
import static no.sikt.nva.nvi.common.utils.DecimalUtils.adjustScaleAndRoundingMode;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.List;
import java.util.stream.IntStream;
import no.sikt.nva.nvi.report.model.Cell;
import no.sikt.nva.nvi.report.model.NumericCell;
import no.sikt.nva.nvi.report.model.Row;
import no.sikt.nva.nvi.report.model.StringCell;
import org.dhatim.fastexcel.Workbook;
import org.dhatim.fastexcel.Worksheet;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class XlsxGenerator implements ReportGenerator {

  private static final Logger logger = LoggerFactory.getLogger(XlsxGenerator.class);
  private static final String NVI = "NVI";
  private static final String REPORT = "report";
  private final List<Row> rows;

  public XlsxGenerator(List<Row> rows) {
    this.rows = rows;
  }

  @Override
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

  private static void setValue(Worksheet sheet, int row, int col, NumericCell numericCell) {
    var value = numericCell.value();
    sheet.value(row, col, isNull(value) ? 0.0 : adjustScaleAndRoundingMode(value).doubleValue());
  }

  private void addHeaders(Worksheet sheet) {
    if (rows.isEmpty()) {
      return;
    }
    var cells = rows.getFirst().cells();
    IntStream.range(0, cells.size()).forEach(i -> sheet.value(0, i, cells.get(i).header().name()));
  }

  private void addData(Worksheet sheet) {
    IntStream.range(0, rows.size())
        .forEach(rowIndex -> addRow(sheet, rowIndex + 1, rows.get(rowIndex).cells()));
  }

  private void addRow(Worksheet sheet, int rowIndex, List<Cell> cells) {
    IntStream.range(0, cells.size()).forEach(i -> setCellValue(sheet, rowIndex, i, cells.get(i)));
  }

  private void setCellValue(Worksheet sheet, int row, int col, Cell cell) {
    switch (cell) {
      case NumericCell numericCell -> setValue(sheet, row, col, numericCell);
      case StringCell stringCell -> sheet.value(row, col, stringCell.string());
    }
  }
}
