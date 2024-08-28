package no.sikt.nva.nvi.index.xlsx;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.Base64;
import java.util.Base64.Encoder;
import java.util.List;
import java.util.Objects;
import nva.commons.core.JacocoGenerated;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.xssf.usermodel.XSSFSheet;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;

public final class ExcelWorkbookGenerator {

    private static final Encoder ENCODER = Base64.getEncoder();
    private static final int FIRST_SHEET_INDEX = 0;
    private static final int FIRST_ROW_INDEX = 0;
    private final List<String> headers;
    private final List<List<String>> data;

    public ExcelWorkbookGenerator(List<String> headers, List<List<String>> data) {
        this.headers = headers;
        this.data = data;
    }

    public String toBase64EncodedString() {
        return ENCODER.encodeToString(this.toXSSFWorkbookByteArray());
    }

    @Override
    @JacocoGenerated
    public int hashCode() {
        return Objects.hash(headers, data);
    }

    @Override
    @JacocoGenerated
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        ExcelWorkbookGenerator that = (ExcelWorkbookGenerator) o;
        return Objects.equals(headers, that.headers) && Objects.equals(data, that.data);
    }

    private static void addCells(Row row, List<String> cells) {
        for (var subCounter = 0; subCounter < cells.size(); subCounter++) {
            var currentCell = row.createCell(subCounter);
            currentCell.setCellValue(cells.get(subCounter));
        }
    }

    private static XSSFWorkbook createWorkbookWithOneSheet() {
        var workbook = new XSSFWorkbook();
        workbook.createSheet();
        return workbook;
    }

    private byte[] toXSSFWorkbookByteArray() {
        var byteArrayOutputStream = new ByteArrayOutputStream();
        try (var workbook = createWorkbookWithOneSheet()) {
            createSheetWithHeadersAndData(workbook);
            workbook.write(byteArrayOutputStream);
            workbook.close();
            return byteArrayOutputStream.toByteArray();
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    private void createSheetWithHeadersAndData(XSSFWorkbook workbook) {
        var sheet = workbook.getSheetAt(FIRST_SHEET_INDEX);
        addHeaders(sheet);
        addData(sheet);
    }

    private void addData(XSSFSheet sheet) {
        for (List<String> cells : data) {
            var nextRow = sheet.getLastRowNum() + 1;
            addCells(sheet.createRow(nextRow), cells);
        }
    }

    private void addHeaders(XSSFSheet sheet) {
        var headerRow = sheet.createRow(FIRST_ROW_INDEX);
        addCells(headerRow, headers);
    }
}
