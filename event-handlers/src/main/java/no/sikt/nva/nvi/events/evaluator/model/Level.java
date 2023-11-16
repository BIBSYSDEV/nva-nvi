package no.sikt.nva.nvi.events.evaluator.model;

import com.fasterxml.jackson.annotation.JsonValue;

public enum Level {
    LEVEL_ONE("1"),
    LEVEL_ONE_V2("LevelOne"),
    LEVEL_TWO("2"),
    LEVEL_TWO_V2("LevelTwo");

    @JsonValue
    private final String value;

    Level(String value) {
        this.value = value;
    }

    public String getValue() {
        return value;
    }
}