package no.sikt.nva.nvi.index.apigateway.utils;

import com.opencsv.CSVReader;
import com.opencsv.exceptions.CsvException;
import java.io.IOException;
import java.io.InputStream;
import java.io.StringReader;
import java.util.ArrayList;
import java.util.List;
import no.sikt.nva.nvi.index.xlsx.ExcelWorkbookGenerator;
import org.apache.poi.xssf.usermodel.XSSFRow;
import org.apache.poi.xssf.usermodel.XSSFSheet;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;

public class ExcelWorkbookUtil {

    private static final int FIRST_SHEET_INDEX = 0;
    private static final int FIRST_ROW_INDEX = 0;
    private static final int FIRST_DATA_ROW_INDEX = 1;

    public static ExcelWorkbookGenerator fromInputStream(InputStream inputStream) {
        try (var workbook = new XSSFWorkbook(inputStream)) {
            var sheet = workbook.getSheetAt(FIRST_SHEET_INDEX);
            var headers = extractHeaders(sheet);
            var data = extractData(sheet);
            return new ExcelWorkbookGenerator(headers, data);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    public static ExcelWorkbookGenerator fromCsvString(String csvString) {
        try (CSVReader csvReader = new CSVReader(new StringReader(csvString))) {
            var headers = csvReader.readNext();
            var rows = csvReader.readAll().stream().map(List::of).toList();

            return new ExcelWorkbookGenerator(List.of(headers), rows);
        } catch (CsvException | IOException e) {
            throw new RuntimeException(e);
        }
    }

    private static List<List<String>> extractData(XSSFSheet sheet) {
        var data = new ArrayList<List<String>>();
        for (var rowCounter = FIRST_DATA_ROW_INDEX; rowCounter <= sheet.getLastRowNum(); rowCounter++) {
            var row = sheet.getRow(rowCounter);
            data.add(extractRow(row));
        }
        return data;
    }

    private static List<String> extractHeaders(XSSFSheet sheet) {
        var headerRow = sheet.getRow(FIRST_ROW_INDEX);
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
