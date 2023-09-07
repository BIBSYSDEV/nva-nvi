package no.sikt.nva.nvi.common.db.model;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;
import java.util.Arrays;
import nva.commons.core.JacocoGenerated;

@JacocoGenerated
public enum DbStatus {
    APPROVED("Approved"), PENDING("Pending"), REJECTED("Rejected");

    @JsonValue
    private final String value;

    DbStatus(String value) {
        this.value = value;
    }

    @JsonCreator
    public static DbStatus parse(String value) {
        return Arrays
                   .stream(DbStatus.values())
                   .filter(status -> status.getValue().equalsIgnoreCase(value))
                   .findFirst()
                   .orElseThrow();
    }

    public String getValue() {
        return value;
    }
}
