package no.sikt.nva.nvi.common.service;

import static java.util.Objects.isNull;
import java.net.URI;
import java.time.Instant;
import java.util.UUID;
import no.sikt.nva.nvi.common.db.ApprovalStatusDao;
import no.sikt.nva.nvi.common.db.ApprovalStatusDao.DbApprovalStatus;
import no.sikt.nva.nvi.common.db.ApprovalStatusDao.DbStatus;
import no.sikt.nva.nvi.common.db.CandidateRepository;
import no.sikt.nva.nvi.common.db.model.Username;
import no.sikt.nva.nvi.common.model.UpdateApprovalRequest;
import no.sikt.nva.nvi.common.model.UpdateAssigneeRequest;
import no.sikt.nva.nvi.common.model.UpdateStatusRequest;
import nva.commons.core.JacocoGenerated;

public class ApprovalBO {

    private static final String UNKNOWN_REQUEST_TYPE_MESSAGE = "Unknown request type";
    private static final String ERROR_MISSING_REJECTION_REASON = "Cannot reject approval status without reason.";

    private final CandidateRepository repository;
    private final UUID identifier;
    private final ApprovalStatusDao original;

    public ApprovalBO(CandidateRepository repository, UUID identifier, ApprovalStatusDao dbApprovalStatus) {
        this.repository = repository;
        this.identifier = identifier;
        this.original = dbApprovalStatus;
    }

    public URI institutionId() {
        return original.approvalStatus().institutionId();
    }

    public ApprovalStatusDao approval() {
        return original;
    }

    public ApprovalBO update(UpdateApprovalRequest input) {
        if (input instanceof UpdateAssigneeRequest request) {
            var newDao = repository.updateApprovalStatusDao(identifier, updateAssignee(request));
            return new ApprovalBO(repository, identifier, newDao);
        } else if (input instanceof UpdateStatusRequest request) {
            var newDao = repository.updateApprovalStatusDao(identifier, updateStatus(request));
            return new ApprovalBO(repository, identifier, newDao);
        } else {
            throw new IllegalArgumentException(UNKNOWN_REQUEST_TYPE_MESSAGE);
        }
    }

    private static Username getAssignee(DbApprovalStatus approval, Username username) {
        return approval.hasAssignee() ? approval.assignee() : username;
    }

    private DbApprovalStatus updateAssignee(UpdateAssigneeRequest request) {
        return original.approvalStatus().copy().assignee(Username.fromString(request.username())).build();
    }

    @JacocoGenerated //TODO still bug with return switch not needing default
    private DbApprovalStatus updateStatus(UpdateStatusRequest request) {
        return switch (request.approvalStatus()) {
            case APPROVED -> finalizeApprovedStatus(request);
            case REJECTED -> finalizeRejectedStatus(request);
            case PENDING -> resetStatus();
        };
    }

    private DbApprovalStatus resetStatus() {
        return original.approvalStatus()
                   .copy()
                   .status(DbStatus.PENDING)
                   .finalizedBy(null)
                   .finalizedDate(null)
                   .reason(null)
                   .build();
    }

    private DbApprovalStatus finalizeApprovedStatus(UpdateStatusRequest request) {
        var username = Username.fromString(request.username());
        var approval = original.approvalStatus();
        return approval.copy()
                   .status(request.approvalStatus())
                   .assignee(getAssignee(approval, username))
                   .finalizedBy(username)
                   .finalizedDate(Instant.now())
                   .reason(null)
                   .build();
    }

    private DbApprovalStatus finalizeRejectedStatus(UpdateStatusRequest request) {
        if (isNull(request.reason())) {
            throw new UnsupportedOperationException(ERROR_MISSING_REJECTION_REASON);
        }
        var username = Username.fromString(request.username());
        var approval = original.approvalStatus();
        return approval.copy()
                   .status(request.approvalStatus())
                   .assignee(getAssignee(approval, username))
                   .finalizedBy(username)
                   .finalizedDate(Instant.now())
                   .reason(request.reason())
                   .build();
    }
}
