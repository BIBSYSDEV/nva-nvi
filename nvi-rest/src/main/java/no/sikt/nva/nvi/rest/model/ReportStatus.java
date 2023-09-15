package no.sikt.nva.nvi.rest.model;

public enum ReportStatus {

    REPORTABLE("Reportable"), NOT_REPORTABLE("NotReportable");

    private final String value;

    ReportStatus(String value) {
        this.value = value;
    }

    public String getValue() {
        return value;
    }
}
