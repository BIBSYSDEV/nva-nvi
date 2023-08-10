package no.sikt.nva.nvi.common.model;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;
import java.util.Arrays;

public enum CompletionStatus {
    COMPLETED("Completed"), IN_PROGRESS("In progress");

    @JsonValue
    private final String value;

    CompletionStatus(String value) {
        this.value = value;
    }

    @JsonCreator
    public static CompletionStatus parse(String value) {
        return Arrays
                   .stream(CompletionStatus.values())
                   .filter(level -> level.getValue().equalsIgnoreCase(value))
                   .findFirst()
                   .orElseThrow();
    }

    public String getValue() {
        return value;
    }
}
