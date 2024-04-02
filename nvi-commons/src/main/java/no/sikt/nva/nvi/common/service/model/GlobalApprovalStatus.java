package no.sikt.nva.nvi.common.service.model;

import com.fasterxml.jackson.annotation.JsonValue;

public enum GlobalApprovalStatus {

    APPROVED("Approved"), PENDING("Pending"), REJECTED("Rejected"), DISPUTE("Dispute");

    @JsonValue
    private final String value;

    GlobalApprovalStatus(String value) {
        this.value = value;
    }

    public String getValue() {
        return value;
    }
}
