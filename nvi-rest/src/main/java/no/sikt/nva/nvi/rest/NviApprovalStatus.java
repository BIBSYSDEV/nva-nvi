package no.sikt.nva.nvi.rest;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;
import java.util.Arrays;
import no.sikt.nva.nvi.common.model.business.DbStatus;
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
    public static DbStatus parse(String value) {
        return Arrays
                   .stream(DbStatus.values())
                   .filter(status -> status.getValue().equalsIgnoreCase(value))
                   .findFirst()
                   .orElseThrow();
    }

    public String getValue() {
        return value;
    }
}
