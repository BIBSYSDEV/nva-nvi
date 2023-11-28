package no.sikt.nva.nvi.common.service.dto;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;
import java.util.Arrays;
import nva.commons.core.JacocoGenerated;

@JacocoGenerated
public enum ApprovalStatus {

    APPROVED("Approved"), PENDING("Pending"), REJECTED("Rejected");

    @JsonValue
    private final String value;

    ApprovalStatus(String value) {
        this.value = value;
    }

    @JsonCreator
    public static ApprovalStatus parse(String value) {
        return Arrays.stream(ApprovalStatus.values())
                   .filter(status -> status.getValue().equalsIgnoreCase(value))
                   .findFirst()
                   .orElseThrow();
    }

    public String getValue() {
        return value;
    }
}
