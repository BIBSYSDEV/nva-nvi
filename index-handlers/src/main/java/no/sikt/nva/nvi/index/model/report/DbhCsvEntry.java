package no.sikt.nva.nvi.index.model.report;

import com.opencsv.bean.CsvBindByName;

public class DbhCsvEntry {

  @CsvBindByName(column = "Institusjonskode")
  private String institution;

  @CsvBindByName(column = "Fakultetskode")
  private String faculty;

  @CsvBindByName(column = "Avdelingskode")
  private String department;

  @CsvBindByName(column = "NVA_INSTITUTION")
  private String nvaTopLevelIdentifier;

  @CsvBindByName(column = "NVA_ID")
  private String fullNvaIdentifier;

  public DbhCsvEntry() {}

  public String institution() {
    return institution;
  }

  public String faculty() {
    return faculty;
  }

  public String department() {
    return department;
  }

  public String nvaTopLevelIdentifier() {
    return nvaTopLevelIdentifier;
  }

  public String fullNvaIdentifier() {
    return fullNvaIdentifier;
  }
}
