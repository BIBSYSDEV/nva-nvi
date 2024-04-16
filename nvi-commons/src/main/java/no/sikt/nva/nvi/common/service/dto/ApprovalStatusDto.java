package no.sikt.nva.nvi.common.service.dto;

import com.fasterxml.jackson.annotation.JsonValue;
import no.sikt.nva.nvi.common.service.model.ApprovalStatus;

public enum ApprovalStatusDto {

    NEW("New"), PENDING("Pending"), APPROVED("Approved"), REJECTED("Rejected");

    @JsonValue
    private final String value;

    ApprovalStatusDto(String value) {
        this.value = value;
    }

    public static ApprovalStatusDto from(ApprovalStatus status, boolean approvalHasAssignee) {
        return switch (status) {
            case PENDING -> approvalHasAssignee ? PENDING : NEW;
            case APPROVED -> APPROVED;
            case REJECTED -> REJECTED;
        };
    }

    public String getValue() {
        return value;
    }
}
