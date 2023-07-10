package no.sikt.nva.nvi.index.model;

import nva.commons.core.JacocoGenerated;

public enum ApprovalStatus {
    PENDING("Pending"),
    APPROVED("Approved"),
    REJECTED("Rejected");

    private final String value;

    ApprovalStatus(String value) {
        this.value = value;
    }

    @JacocoGenerated
    public String getValue() {
        return value;
    }
}
