package no.sikt.nva.nvi.common.db.model;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;
import java.util.Arrays;

public enum DbCompletionStatus {
    COMPLETED("Completed"), IN_PROGRESS("In progress");

    @JsonValue
    private final String value;

    DbCompletionStatus(String value) {
        this.value = value;
    }

    @JsonCreator
    public static DbCompletionStatus parse(String value) {
        return Arrays
                   .stream(DbCompletionStatus.values())
                   .filter(level -> level.getValue().equalsIgnoreCase(value))
                   .findFirst()
                   .orElseThrow();
    }

    public String getValue() {
        return value;
    }
}
