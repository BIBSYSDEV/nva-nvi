package no.sikt.nva.nvi.common.model;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;

public enum InstanceType implements ParsableEnum {
  NON_CANDIDATE("NonCandidateInstanceType"),
  ACADEMIC_COMMENTARY("AcademicCommentary"),
  ACADEMIC_MONOGRAPH("AcademicMonograph"),
  ACADEMIC_CHAPTER("AcademicChapter"),
  ACADEMIC_ARTICLE("AcademicArticle"),
  ACADEMIC_LITERATURE_REVIEW("AcademicLiteratureReview");

  private final String value;

  InstanceType(String value) {
    this.value = value;
  }

  @JsonValue
  @Override
  public String getValue() {
    return value;
  }

  @JsonCreator
  public static InstanceType parse(String stringValue) {
    return ParsableEnum.parse(InstanceType.class, stringValue, NON_CANDIDATE);
  }

  public boolean isValid() {
    return this != NON_CANDIDATE;
  }
}
