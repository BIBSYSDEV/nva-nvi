package no.sikt.nva.nvi.rest.upsert;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;
import java.util.Arrays;
import nva.commons.core.JacocoGenerated;

@JacocoGenerated
public enum NviApprovalStatus {

    APPROVED("Approved"), PENDING("Pending"), REJECTED("Rejected");

    @JsonValue
    private final String value;

    NviApprovalStatus(String value) {
        this.value = value;
    }

    @JsonCreator
    public static NviApprovalStatus parse(String value) {
        return Arrays.stream(NviApprovalStatus.values())
                   .filter(status -> status.getValue().equalsIgnoreCase(value))
                   .findFirst()
                   .orElseThrow();
    }

    public String getValue() {
        return value;
    }
}
