package no.sikt.nva.nvi.evaluator.model;

import com.fasterxml.jackson.annotation.JsonValue;

public enum Level {
    LEVEL_ONE("1"),
    LEVEL_TWO("2");

    @JsonValue
    private final String value;

    Level(String value) {
        this.value = value;
    }

    public String getValue() {
        return value;
    }
}