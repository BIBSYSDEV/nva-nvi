package no.sikt.nva.nvi.events;

import nva.commons.core.JacocoGenerated;

@JacocoGenerated
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
