package no.sikt.nva.nvi.common.db;


public enum ReportStatus {

    REPORTED("Reported");
    private final String value;

    ReportStatus(String value) {
        this.value = value;
    }

    public String getValue() {
        return value;
    }

}
