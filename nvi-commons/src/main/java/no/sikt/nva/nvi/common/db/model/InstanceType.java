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

    @JsonCreator
    @JacocoGenerated
    public String getValue() {
        return value;
    }
}
