package no.sikt.nva.nvi.common.db.model;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;
import java.util.Arrays;

public enum DbLevel {
    UNASSIGNED("Unassigned"), LEVEL_ONE("1"), LEVEL_TWO("2");

    @JsonValue
    private final String value;

    DbLevel(String value) {

        this.value = value;
    }

    @JsonCreator
    public static DbLevel parse(String value) {
        return Arrays
                   .stream(DbLevel.values())
                   .filter(level -> level.getValue().equalsIgnoreCase(value))
                   .findFirst()
                   .orElseThrow();
    }

    public String getValue() {
        return value;
    }
}
