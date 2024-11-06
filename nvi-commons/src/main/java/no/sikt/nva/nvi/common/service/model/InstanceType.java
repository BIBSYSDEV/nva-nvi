package no.sikt.nva.nvi.common.service.model;

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

    public String getValue() {
        return value;
    }
}
