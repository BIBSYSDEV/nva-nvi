package no.sikt.nva.nvi.evaluator.model;

import nva.commons.core.JacocoGenerated;

public enum CandidateStatus {

    CANDIDATE("Candidate"),
    NON_CANDIDATE("NonCandidate");

    private final String value;

    CandidateStatus(String value) {
        this.value = value;
    }

    @JacocoGenerated
    public String getValue() {
        return value;
    }
}
