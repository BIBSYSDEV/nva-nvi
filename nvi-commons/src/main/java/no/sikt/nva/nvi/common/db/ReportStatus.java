package no.sikt.nva.nvi.common.db;

import com.fasterxml.jackson.annotation.JsonValue;

public enum ReportStatus {

    REPORTED("Reported");
    private final String value;

    ReportStatus(String value) {
        this.value = value;
    }

    @JsonValue
    public String getValue() {
        return value;
    }

}
