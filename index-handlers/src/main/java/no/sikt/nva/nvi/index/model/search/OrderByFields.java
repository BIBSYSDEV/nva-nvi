package no.sikt.nva.nvi.index.model.search;

import java.util.Arrays;
import java.util.List;

public enum OrderByFields {
  CREATED_DATE("createdDate");

  private final String value;

  OrderByFields(String value) {
    this.value = value;
  }

  public static OrderByFields parse(String value) {
    return Arrays.stream(values())
        .filter(field -> field.getValue().equalsIgnoreCase(value))
        .findFirst()
        .orElseThrow(
            () ->
                new IllegalArgumentException(
                    "Invalid orderBy field. Valid values are: " + validValues()));
  }

  public static List<String> validValues() {
    return Arrays.stream(values()).map(OrderByFields::getValue).toList();
  }

  public String getValue() {
    return value;
  }
}
