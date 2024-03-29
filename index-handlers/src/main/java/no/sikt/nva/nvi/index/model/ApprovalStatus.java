package no.sikt.nva.nvi.index.model;

import com.fasterxml.jackson.annotation.JsonValue;
import java.util.Arrays;
import nva.commons.core.SingletonCollector;

public enum ApprovalStatus {
    PENDING("Pending"),
    APPROVED("Approved"),
    REJECTED("Rejected");

    private final String value;

    ApprovalStatus(String value) {
        this.value = value;
    }

    @JsonValue
    public String getValue() {
        return value;
    }

    public static ApprovalStatus fromValue(String candidate) {
        return Arrays.stream(values())
                   .filter(item -> item.getValue().equalsIgnoreCase(candidate))
                   .collect(SingletonCollector.tryCollect())
                   .orElseThrow();
    }
}
