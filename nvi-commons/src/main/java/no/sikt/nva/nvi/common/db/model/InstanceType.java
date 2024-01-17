package no.sikt.nva.nvi.common.db.model;

import com.fasterxml.jackson.annotation.JsonCreator;
import java.util.Arrays;
import nva.commons.core.JacocoGenerated;

public enum InstanceType {

    ACADEMIC_MONOGRAPH("AcademicMonograph"), ACADEMIC_CHAPTER("AcademicChapter"),
    ACADEMIC_ARTICLE("AcademicArticle"), ACADEMIC_LITERATURE_REVIEW("AcademicLiteratureReview"),
    NON_CANDIDATE("NonCandidateInstanceType"), IMPORTED_CANDIDATE("ImportedCandidate");

    private final String value;

    InstanceType(String value) {
        this.value = value;
    }

    @JacocoGenerated
    public static InstanceType parse(String value) {
        return Arrays.stream(InstanceType.values())
                   .filter(type -> type.getValue().equalsIgnoreCase(value))
                   .findFirst()
                   .orElse(NON_CANDIDATE);
    }

    @JsonCreator
    @JacocoGenerated
    public String getValue() {
        return value;
    }
}
