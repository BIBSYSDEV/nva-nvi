package no.sikt.nva.nvi.index.apigateway.utils;

import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;
import no.sikt.nva.nvi.index.xlsx.TwoDimensionalTable;
import org.apache.poi.xssf.usermodel.XSSFRow;
import org.apache.poi.xssf.usermodel.XSSFSheet;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;

public class TwoDimensionalTableUtil {

    public static TwoDimensionalTable inputStreamToTwoDimensionalTable(InputStream inputStream) {
        try (var workbook = new XSSFWorkbook(inputStream)) {
            var sheet = workbook.getSheetAt(0);
            var headers = extractHeaders(sheet);
            var data = extractData(sheet);
            return new TwoDimensionalTable(headers, data);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    private static List<List<String>> extractData(XSSFSheet sheet) {
        var data = new ArrayList<List<String>>();
        for (var rowCounter = 1; rowCounter <= sheet.getLastRowNum(); rowCounter++) {
            var row = sheet.getRow(rowCounter);
            data.add(extractRow(row));
        }
        return data;
    }

    private static List<String> extractHeaders(XSSFSheet sheet) {
        var headerRow = sheet.getRow(0);
        return extractRow(headerRow);
    }

    private static List<String> extractRow(XSSFRow headerRow) {
        var headers = new ArrayList<String>();
        for (var cellCounter = 0; cellCounter < headerRow.getLastCellNum(); cellCounter++) {
            headers.add(headerRow.getCell(cellCounter).getStringCellValue());
        }
        return headers;
    }
}
