package no.sikt.nva.nvi.common.model;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;
import java.util.Arrays;
import java.util.function.Predicate;

public enum InstanceType {
  ACADEMIC_COMMENTARY("AcademicCommentary"),
  ACADEMIC_MONOGRAPH("AcademicMonograph"),
  ACADEMIC_CHAPTER("AcademicChapter"),
  ACADEMIC_ARTICLE("AcademicArticle"),
  ACADEMIC_LITERATURE_REVIEW("AcademicLiteratureReview");

  private final String value;

  InstanceType(String value) {
    this.value = value;
  }

  /**
   * Parses a string value to an enum, ignoring case and allowing for both enum name and enum value
   * to be used as input. This is because existing data can be in either format.
   */
  @JsonCreator
  public static InstanceType parse(String stringValue) {
    return Arrays.stream(values()).filter(matchesEnumValue(stringValue)).findFirst().orElseThrow();
  }

  @JsonValue
  public String getValue() {
    return value;
  }

  private static Predicate<InstanceType> matchesEnumValue(String stringValue) {
    return value ->
        value.name().equalsIgnoreCase(stringValue)
            || value.getValue().equalsIgnoreCase(stringValue);
  }
}
