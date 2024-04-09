package no.sikt.nva.nvi.events.evaluator.model;

import com.fasterxml.jackson.annotation.JsonValue;

public enum Level {
    LEVEL_ONE("LevelOne"),
    LEVEL_TWO("LevelTwo");

    @JsonValue
    private final String value;

    Level(String value) {
        this.value = value;
    }

    public String getValue() {
        return value;
    }
}