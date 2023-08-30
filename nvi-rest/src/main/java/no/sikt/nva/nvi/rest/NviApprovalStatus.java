package no.sikt.nva.nvi.rest;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;
import java.util.Arrays;
import no.sikt.nva.nvi.common.model.business.Status;

public enum NviApprovalStatus {

    APPROVED("Approved"), PENDING("Pending"), REJECTED("Rejected");

    @JsonValue
    private final String value;

    NviApprovalStatus(String value) {
        this.value = value;
    }
    @JsonCreator
    public static Status parse(String value) {
        return Arrays
                   .stream(Status.values())
                   .filter(status -> status.getValue().equalsIgnoreCase(value))
                   .findFirst()
                   .orElseThrow();
    }

    public String getValue() {
        return value;
    }
}
