package no.sikt.nva.nvi.index.model;

public enum ApprovalStatus {
    PENDING("Pending");

    private final String value;

    ApprovalStatus(String value) {
        this.value = value;
    }

    public String getValue() {
        return value;
    }
}
