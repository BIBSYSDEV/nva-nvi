package no.sikt.nva.nvi.common.model;

import com.fasterxml.jackson.annotation.JsonCreator;
import java.util.Arrays;
import java.util.function.Predicate;

public enum InstanceType {
  ACADEMIC_COMMENTARY("AcademicCommentary"),
  ACADEMIC_MONOGRAPH("AcademicMonograph"),
  ACADEMIC_CHAPTER("AcademicChapter"),
  ACADEMIC_ARTICLE("AcademicArticle"),
  ACADEMIC_LITERATURE_REVIEW("AcademicLiteratureReview");

  // FIXME: Add the JsonValue annotation back, and migrate DB field to just a string?
  // Or find some other way to deal with the fact that DDB and Jackson disagree on how to
  // serialize enums...
  private final String value;

  InstanceType(String value) {
    this.value = value;
  }

  @JsonCreator
  public static InstanceType parse(String stringValue) {
    return Arrays.stream(values()).filter(matchesEnumValue(stringValue)).findFirst().orElseThrow();
  }

  public String getValue() {
    return value;
  }

  private static Predicate<InstanceType> matchesEnumValue(String stringValue) {
    return value ->
        value.name().equalsIgnoreCase(stringValue)
            || value.getValue().equalsIgnoreCase(stringValue);
  }
}
