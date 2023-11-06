package no.sikt.nva.nvi.rest.model;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;
import static org.junit.jupiter.api.Assertions.assertEquals;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.List;
import no.sikt.nva.nvi.rest.model.Excel;
import no.sikt.nva.nvi.rest.model.Excel.ColumnName;
import no.sikt.nva.nvi.rest.model.Excel.MyWorkbook;
import org.apache.poi.openxml4j.exceptions.InvalidFormatException;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.CellType;
import org.apache.poi.xssf.usermodel.XSSFSheet;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.junit.jupiter.api.Test;

class ExcelTest {

    @Test
    void shouldMapAllFieldsCorrectly() throws Exception {
        var rowTest = new RowTest("a string", 1, 2, 3.4, 5.6);
        var excel = Excel.fromRecord(List.of(rowTest));
        var tmp = Files.createTempFile("tmp", ".xlsx");
        var fileOutputStream = new FileOutputStream(tmp.toFile());
        excel.write(fileOutputStream);

        var workbook = new XSSFWorkbook(tmp.toFile());
        var lists = getLists(workbook.getSheetAt(0));
        assertThat(lists.get(0).size(), is(RowTest.class.getRecordComponents().length));
        assertEquals(lists.get(1), rowTest.toList());
    }

    @Test
    void shouldReturnEmptyIfRecordIsNotAnnotated() throws IOException, InvalidFormatException {
        var excel = Excel.fromRecord(List.of(new EmptyRecord()));
        var tmp = Files.createTempFile("tmp", ".xlsx");
        var fileOutputStream = new FileOutputStream(tmp.toFile());
        excel.write(fileOutputStream);

        var workbook = new XSSFWorkbook(tmp.toFile());
        assertThat(workbook.getNumberOfSheets(), is(0));
    }

    @Test
    void shouldReturnEmptyWhenListOfClasses() throws IOException, InvalidFormatException {
        var excel = Excel.fromRecord(List.of(new EmptyClass()));
        var tmp = Files.createTempFile("tmp", ".xlsx");
        var fileOutputStream = new FileOutputStream(tmp.toFile());
        excel.write(fileOutputStream);

        var workbook = new XSSFWorkbook(tmp.toFile());
        assertThat(workbook.getNumberOfSheets(), is(0));
    }

    private static ArrayList<List<String>> getLists(XSSFSheet sheet) {
        var data = new ArrayList<List<String>>();
        for (var row : sheet) {
            var currentRow = new ArrayList<String>();
            for (var cell : row) {
                currentRow.add(getCellValue(cell));
            }
            data.add(currentRow);
        }
        return data;
    }

    private static String getCellValue(Cell cell) {
        if (cell.getCellType() == CellType.NUMERIC) {
            return String.valueOf(cell.getNumericCellValue());
        } else {
            return cell.getStringCellValue();
        }
    }

    private record EmptyRecord() {

    }

    @MyWorkbook
    record RowTest(
        String string,
        int inter,
        Integer integer,
        @ColumnName("dobby") double doubler,
        Double aDouble
    ) {

        public List<String> toList() {
            return List.of(
                string,
                Double.toString(inter),
                Double.toString(integer),
                String.valueOf(doubler),
                String.valueOf(aDouble)
            );
        }
    }

    private static class EmptyClass {

    }
}