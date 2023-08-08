package no.sikt.nva.nvi.common.model;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;
import java.util.Arrays;

public enum Approval {
    APPROVED("Approved"), PENDING("Pending"), REJECTED("Rejected");

    @JsonValue
    private final String value;

    Approval(String value) {
        this.value = value;
    }

    @JsonCreator
    public static Approval parse(String value) {
        return Arrays
                   .stream(Approval.values())
                   .filter(status -> status.getValue().equalsIgnoreCase(value))
                   .findFirst()
                   .orElseThrow();
    }

    public String getValue() {
        return value;
    }
}
