package no.sikt.nva.nvi.common.db.model;

import static java.util.Objects.isNull;
import static java.util.Objects.nonNull;
import java.net.URI;
import java.time.Instant;
import java.util.UUID;
import no.sikt.nva.nvi.common.db.ApprovalStatusDao;
import no.sikt.nva.nvi.common.model.UpdateApprovalRequest;
import no.sikt.nva.nvi.common.model.UpdateAssigneeRequest;
import no.sikt.nva.nvi.common.model.UpdateStatusRequest;
import no.sikt.nva.nvi.common.service.NviService;
import nva.commons.core.JacocoGenerated;
import software.amazon.awssdk.enhanced.dynamodb.mapper.annotations.DynamoDbIgnore;
import software.amazon.awssdk.enhanced.dynamodb.mapper.annotations.DynamoDbImmutable;

@DynamoDbImmutable(builder = DbApprovalStatus.Builder.class)
public record DbApprovalStatus(URI institutionId, UUID candidateIdentifier, DbStatus status, DbUsername assignee,
                               DbUsername finalizedBy, Instant finalizedDate, String reason) {

    public static final String UNKNOWN_REQUEST_TYPE_MESSAGE = "Unknown request type";

    public static Builder builder() {
        return new Builder();
    }

    @DynamoDbIgnore
    public ApprovalStatusDao toDao(UUID candidateIdentifier) {
        return new ApprovalStatusDao(candidateIdentifier, this);
    }

    @DynamoDbIgnore
    public Builder copy() {
        return builder().institutionId(institutionId)
                   .candidateIdentifier(candidateIdentifier)
                   .status(status)
                   .assignee(assignee)
                   .finalizedBy(finalizedBy)
                   .finalizedDate(finalizedDate)
                   .reason(reason);
    }

    @DynamoDbIgnore
    public boolean hasAssignee() {
        return nonNull(assignee);
    }

    @DynamoDbIgnore
    public DbApprovalStatus fetch(NviService nviService) {
        return nviService.findApprovalStatus(institutionId, candidateIdentifier);
    }

    @DynamoDbIgnore
    public DbApprovalStatus update(NviService nviService, UpdateApprovalRequest input) {
        var copy = this.copy();
        if (input instanceof UpdateAssigneeRequest request) {
            return nviService.updateApproval(candidateIdentifier, copy.assignee(request.username()).build());
        } else if (input instanceof UpdateStatusRequest request) {
            return nviService.updateApproval(this.candidateIdentifier, updateStatus(nviService, request));
        } else {
            throw new IllegalArgumentException(UNKNOWN_REQUEST_TYPE_MESSAGE);
        }
    }

    @JacocoGenerated
    @DynamoDbIgnore
    private DbApprovalStatus updateStatus(NviService nviService, UpdateStatusRequest request) {
        return switch (request.approvalStatus()) {
            case APPROVED -> finalizeApprovedStatus(nviService, request);
            case REJECTED -> finalizeRejectedStatus(nviService, request);
            case PENDING -> resetStatus(nviService);
        };
    }

    @DynamoDbIgnore
    private DbApprovalStatus resetStatus(NviService nviService) {
        return this.fetch(nviService).copy().status(DbStatus.PENDING).finalizedBy(null).finalizedDate(null).build();
    }

    @DynamoDbIgnore
    private DbApprovalStatus finalizeApprovedStatus(NviService nviService, UpdateStatusRequest request) {
        var approval = this.fetch(nviService);
        var username = DbUsername.fromString(request.username());
        return approval.copy()
                   .status(request.approvalStatus())
                   .finalizedBy(username)
                   .assignee(this.hasAssignee() ? this.assignee : username)
                   .finalizedDate(Instant.now())
                   .build();
    }

    @DynamoDbIgnore
    private DbApprovalStatus finalizeRejectedStatus(NviService nviService, UpdateStatusRequest request) {
        var approval = this.fetch(nviService);
        var username = DbUsername.fromString(request.username());
        if(isNull(request.reason())) {
            throw new UnsupportedOperationException("Cannot reject approval status without reason.");
        }
        return approval.copy()
                   .status(request.approvalStatus())
                   .finalizedBy(username)
                   .reason(request.reason())
                   .assignee(this.hasAssignee() ? this.assignee : username)
                   .finalizedDate(Instant.now())
                   .build();
    }

    public static final class Builder {

        private URI builderInstitutionId;
        private UUID builderCandidateIdentifier;
        private DbStatus builderStatus;
        private DbUsername builderAssignee;
        private DbUsername builderFinalizedBy;
        private Instant builderFinalizedDate;
        private String builderReason;

        private Builder() {
        }

        public Builder institutionId(URI institutionId) {
            this.builderInstitutionId = institutionId;
            return this;
        }

        public Builder status(DbStatus status) {
            this.builderStatus = status;
            return this;
        }

        public Builder assignee(DbUsername assignee) {
            this.builderAssignee = assignee;
            return this;
        }

        public Builder finalizedBy(DbUsername finalizedBy) {
            this.builderFinalizedBy = finalizedBy;
            return this;
        }

        public Builder finalizedDate(Instant finalizedDate) {
            this.builderFinalizedDate = finalizedDate;
            return this;
        }

        public Builder candidateIdentifier(UUID candidateIdentifier) {
            this.builderCandidateIdentifier = candidateIdentifier;
            return this;
        }

        public Builder reason(String reason){
            this.builderReason = reason;
            return this;
        }

        public DbApprovalStatus build() {
            return new DbApprovalStatus(builderInstitutionId, builderCandidateIdentifier, builderStatus,
                                        builderAssignee, builderFinalizedBy, builderFinalizedDate, builderReason);
        }
    }
}