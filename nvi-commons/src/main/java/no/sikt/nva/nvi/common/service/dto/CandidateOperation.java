package no.sikt.nva.nvi.common.service.dto;

import com.fasterxml.jackson.annotation.JsonValue;

public enum CandidateOperation {
    APPROVAL_REJECT("approval/reject-candidate"),
    APPROVAL_APPROVE("approval/approve-candidate"),
    APPROVAL_PENDING("approval/reset-approval");

    CandidateOperation(String value) {
        this.value = value;
    }

    private String value;
    public static final String DELIMITER = ", ";

    @JsonValue
    public String getValue() {
        return value;
    }

    public void setValue(String value) {
        this.value = value;
    }
}
