package no.sikt.nva.nvi.index.xlsx;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.util.List;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;

public record Excel(Workbook workbook) {

    public static Excel fromJava(List<String> headers, List<List<String>> data) {
        var excel = new Excel(createWorkbookWithOneSheet());
        excel.addHeaders(headers);
        excel.addData(data);
        return excel;
    }
    public void addData(List<List<String>> data) {
        var sheet = workbook.getSheetAt(0);
        for (List<String> cells : data) {
            var nextRow = sheet.getLastRowNum() + 1;
            addCells(sheet.createRow(nextRow), cells);
        }
    }

    public byte[] toBytes() {
        var byteArrayOutputStream = new ByteArrayOutputStream();
        try {
            this.write(byteArrayOutputStream);
            return byteArrayOutputStream.toByteArray();
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
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

    private void write(OutputStream outputStream) throws IOException {
        workbook.write(outputStream);
        workbook.close();
    }

    private void addHeaders(List<String> headers) {
        var sheet = workbook.getSheetAt(0);
        var headerRow = sheet.createRow(0);
        addCells(headerRow, headers);
    }
}
