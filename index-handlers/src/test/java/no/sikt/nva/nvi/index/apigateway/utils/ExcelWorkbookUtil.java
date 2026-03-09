package no.sikt.nva.nvi.index.apigateway.utils;

import static no.sikt.nva.nvi.common.utils.DecimalUtils.adjustScaleAndRoundingMode;
import static no.sikt.nva.nvi.index.model.report.InstitutionReportHeader.INSTITUTION_ID;
import static no.sikt.nva.nvi.index.model.report.InstitutionReportHeader.POINTS_FOR_AFFILIATION;
import static no.sikt.nva.nvi.index.model.report.InstitutionReportHeader.PUBLICATION_LANGUAGE;

import java.io.IOException;
import java.io.InputStream;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import no.sikt.nva.nvi.index.model.report.InstitutionReportHeader;
import no.sikt.nva.nvi.index.xlsx.ExcelWorkbookGenerator;
import org.apache.poi.ss.usermodel.CellType;
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

  public static List<String> extractLinesInInstitutionIdentifierColumn(InputStream inputStream) {
    try (var workbook = new XSSFWorkbook(inputStream)) {
      var sheet = workbook.getSheetAt(FIRST_SHEET_INDEX);
      return extractHeaderColumn(sheet, INSTITUTION_ID);
    } catch (IOException e) {
      throw new RuntimeException(e);
    }
  }

  public static List<String> extractLinesInLanguageColumn(InputStream inputStream) {
    try (var workbook = new XSSFWorkbook(inputStream)) {
      var sheet = workbook.getSheetAt(FIRST_SHEET_INDEX);
      return extractHeaderColumn(sheet, PUBLICATION_LANGUAGE);
    } catch (IOException e) {
      throw new RuntimeException(e);
    }
  }

  public static List<String> extractRowsInPointsForAffiliationColumn(InputStream inputStream) {
    try (var workbook = new XSSFWorkbook(inputStream)) {
      var sheet = workbook.getSheetAt(FIRST_SHEET_INDEX);
      return extractHeaderColumn(sheet, POINTS_FOR_AFFILIATION);
    } catch (IOException e) {
      throw new RuntimeException(e);
    }
  }

  private static List<String> extractHeaderColumn(XSSFSheet sheet, InstitutionReportHeader header) {
    var columnValues = new ArrayList<String>();
    for (var rowCounter = FIRST_DATA_ROW_INDEX; rowCounter <= sheet.getLastRowNum(); rowCounter++) {
      var row = sheet.getRow(rowCounter);
      var cell = row.getCell(header.getOrder());
      if (cell.getCellType() == CellType.NUMERIC) {
        var bigValue = BigDecimal.valueOf(cell.getNumericCellValue());
        var normalizedValue = adjustScaleAndRoundingMode(bigValue);
        columnValues.add(normalizedValue.toString());
      } else {
        columnValues.add(cell.getStringCellValue());
      }
    }
    return columnValues;
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
      var cellValue = headerRow.getCell(cellCounter);
      if (cellValue.getCellType() == CellType.NUMERIC) {
        var bigValue = BigDecimal.valueOf(cellValue.getNumericCellValue());
        var normalizedValue = adjustScaleAndRoundingMode(bigValue);
        headers.add(normalizedValue.toString());
      } else {
        headers.add(cellValue.getStringCellValue());
      }
    }
    return headers;
  }
}
