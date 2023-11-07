package no.sikt.nva.nvi.rest.model;

import static nva.commons.core.attempt.Try.attempt;
import java.io.IOException;
import java.io.OutputStream;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import java.lang.reflect.RecordComponent;
import java.util.List;
import java.util.function.Consumer;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.xssf.usermodel.XSSFCell;
import org.apache.poi.xssf.usermodel.XSSFRow;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;

public final class Excel implements AutoCloseable {

    private final Workbook workbook;

    private Excel(Workbook workbook) {
        this.workbook = workbook;
    }

    public static <T> Excel fromRecord(List<T> records) {
        var workbook = new XSSFWorkbook();
        var className = records.get(0).getClass();
        if (className.isAnnotationPresent(MyWorkbook.class) && className.isRecord()) {
            var sheet = workbook.createSheet();
            var recordComponents = className.getRecordComponents();
            var headerRow = sheet.createRow(0);
            for (var columnIndex = 0; columnIndex < recordComponents.length; columnIndex++) {
                addHeaderRow(recordComponents, headerRow, columnIndex);
            }
            for (var rowIndex = 0; rowIndex < records.size(); rowIndex++) {
                var record = records.get(rowIndex);
                var row = sheet.createRow(rowIndex + 1);
                for (var columnIndex = 0; columnIndex < recordComponents.length; columnIndex++) {
                    addSheetComponent(recordComponents, record, row, columnIndex);
                }
            }
        }
        return new Excel(workbook);
    }

    private static <T> void addSheetComponent(RecordComponent[] recordComponents, T record, XSSFRow row, int columnIndex) {
        var recordComponent = recordComponents[columnIndex];
        var cell = row.createCell(columnIndex);
        addValueToCell(recordComponent, record, cell);
    }

    private static void addHeaderRow(RecordComponent[] recordComponents, XSSFRow headerRow, int colIndex) {
        headerRow.createCell(colIndex).setCellValue(getHeaderValue(recordComponents[colIndex]));
    }

    public void write(OutputStream outputStream) throws IOException {
        workbook.write(outputStream);
        workbook.close();
    }

    @Override
    public void close() throws Exception {
        workbook.close();
    }

    private static String getHeaderValue(RecordComponent recordComponent) {
        if (recordComponent.isAnnotationPresent(ColumnName.class)) {
            return recordComponent.getAnnotation(ColumnName.class).value();
        }
        return recordComponent.getName();
    }

    private static <T> void addValueToCell(RecordComponent recordComponent, T record, XSSFCell cell) {
        var value = attempt(() -> recordComponent.getAccessor().invoke(record)).orElseThrow();
        switch (recordComponent.getType().getName()) {
            case "java.lang.Integer", "int" -> castInt(value, cell::setCellValue);
            case "java.lang.Double", "double" -> castDouble(value, cell::setCellValue);
            default -> castString(value, cell::setCellValue);
        }
    }

    private static void castString(Object value, Consumer<String> consumer) {
        if (value != null) {
            consumer.accept(String.valueOf(value));
        }
    }

    private static void castDouble(Object value, Consumer<Double> consumer) {
        if (value != null) {
            consumer.accept(attempt(() -> (Double) value).orElseThrow());
        }
    }

    private static void castInt(Object value, Consumer<Integer> consumer) {
        if (value != null) {
            consumer.accept(attempt(() -> (Integer) value).orElseThrow());
        }
    }

    @Retention(RetentionPolicy.RUNTIME)
    @Target(ElementType.TYPE)
    public @interface MyWorkbook {

    }

    @Retention(RetentionPolicy.RUNTIME)
    @Target({ElementType.RECORD_COMPONENT})
    public @interface ColumnName {

        String value();
    }
}
