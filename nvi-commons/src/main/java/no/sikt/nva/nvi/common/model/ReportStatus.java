package no.sikt.nva.nvi.common.model;

import nva.commons.core.JacocoGenerated;

public enum ReportStatus {

    REPORTABLE("Reportable"), NOT_REPORTABLE("NotReportable");

    private final String value;

    ReportStatus(String value) {
        this.value = value;
    }

    @JacocoGenerated
    public String getValue() {
        return value;
    }
}
