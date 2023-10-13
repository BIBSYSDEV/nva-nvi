package no.sikt.nva.nvi.common.model.business;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;
import java.util.Arrays;

public enum Level {
    UNASSIGNED("Unassigned"), LEVEL_ONE("1"), LEVEL_TWO("2"), NON_CANDIDATE("NonCandidateLevel");

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
                   .orElse(NON_CANDIDATE);
    }

    public String getValue() {
        return value;
    }
}
