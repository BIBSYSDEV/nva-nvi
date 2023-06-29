package no.sikt.nva.nvi.common.model;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;
import java.util.Arrays;

public enum Level {
    SOME_LEVEL("Some level");

    @JsonValue
    private final String value;

    Level(String value) {

        this.value = value;
    }

    @JsonCreator
    public static Level parse(String value) {
        return Arrays.stream(Level.values())
                   .filter(level -> level.getValue().equalsIgnoreCase(value))
                   .findFirst()
                   .orElseThrow();
    }

    public String getValue() {
        return value;
    }
}
