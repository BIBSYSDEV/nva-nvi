package no.sikt.nva.nvi.index.model.report;

import com.opencsv.bean.CsvBindByName;

public class DbhCsvEntry {

  private static final String TOP_LEVEL = "top_level";

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

  @CsvBindByName(column = "Match")
  private String match;

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

  public String match() {
    return match;
  }

  public boolean isTopLevel() {
    return TOP_LEVEL.equals(match());
  }
}
