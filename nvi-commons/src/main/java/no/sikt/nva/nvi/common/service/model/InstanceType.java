package no.sikt.nva.nvi.common.service.model;

import com.fasterxml.jackson.annotation.JsonValue;
import java.util.Arrays;

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

    public static InstanceType parse(String value) {
        return Arrays.stream(InstanceType.values())
                   .filter(instanceType -> instanceType.getValue().equalsIgnoreCase(value))
                   .findFirst()
                   .orElseThrow();
    }

    @JsonValue
    public String getInstanceType() {
        return value;
    }

    public String getValue() {
        return value;
    }
}
