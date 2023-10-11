package no.sikt.nva.nvi.common.db;

import static java.util.Objects.nonNull;
import static java.util.UUID.randomUUID;
import static no.sikt.nva.nvi.common.DatabaseConstants.DATA_FIELD;
import static no.sikt.nva.nvi.common.DatabaseConstants.HASH_KEY;
import static no.sikt.nva.nvi.common.DatabaseConstants.SORT_KEY;
import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;
import java.net.URI;
import java.time.Instant;
import java.util.Arrays;
import java.util.UUID;
import no.sikt.nva.nvi.common.db.model.Username;
import nva.commons.core.JacocoGenerated;
import software.amazon.awssdk.enhanced.dynamodb.mapper.annotations.DynamoDbAttribute;
import software.amazon.awssdk.enhanced.dynamodb.mapper.annotations.DynamoDbIgnore;
import software.amazon.awssdk.enhanced.dynamodb.mapper.annotations.DynamoDbImmutable;
import software.amazon.awssdk.enhanced.dynamodb.mapper.annotations.DynamoDbPartitionKey;
import software.amazon.awssdk.enhanced.dynamodb.mapper.annotations.DynamoDbSortKey;

@DynamoDbImmutable(builder = ApprovalStatusDao.Builder.class)
public record ApprovalStatusDao(UUID identifier,
                                @DynamoDbAttribute(DATA_FIELD) DbApprovalStatus approvalStatus,
                                String version
) implements DynamoEntryWithRangeKey {

    public static final String TYPE = "APPROVAL_STATUS";

    public static String createSortKey(String institutionUri) {
        return String.join(FIELD_DELIMITER, TYPE, institutionUri);
    }

    public static Builder builder() {
        return new Builder();
    }

    @Override
    @DynamoDbPartitionKey
    @DynamoDbAttribute(HASH_KEY)
    public String primaryKeyHashKey() {
        return CandidateDao.createPartitionKey(identifier.toString());
    }

    @Override
    @DynamoDbSortKey
    @DynamoDbAttribute(SORT_KEY)
    public String primaryKeyRangeKey() {
        return createSortKey(approvalStatus.institutionId().toString());
    }

    @Override
    @JacocoGenerated
    @DynamoDbAttribute(TYPE_FIELD)
    public String type() {
        return TYPE;
    }

    @DynamoDbIgnore
    @JacocoGenerated
    public ApprovalStatusDao.Builder copy() {
        return builder()
                   .identifier(identifier)
                   .version(randomUUID().toString())
                   .approvalStatus(approvalStatus.copy().build());
    }

    @JacocoGenerated
    public enum DbStatus {
        APPROVED("Approved"), PENDING("Pending"), REJECTED("Rejected");

        @JsonValue
        private final String value;

        DbStatus(String value) {
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

    public static final class Builder {

        private UUID builderIdentifier;
        private DbApprovalStatus builderApprovalStatus;
        private String builderVersion;

        private Builder() {
        }

        public Builder identifier(UUID identifier) {
            this.builderIdentifier = identifier;
            return this;
        }

        public Builder type(String noop) {
            // Used by @DynamoDbImmutable for building the object
            return this;
        }

        public Builder primaryKeyHashKey(String noop) {
            // Used by @DynamoDbImmutable for building the object
            return this;
        }

        public Builder primaryKeyRangeKey(String noop) {
            // Used by @DynamoDbImmutable for building the object
            return this;
        }

        public Builder approvalStatus(DbApprovalStatus approvalStatus) {
            this.builderApprovalStatus = approvalStatus;
            return this;
        }

        public Builder version(String version) {
            this.builderVersion = version;
            return this;
        }

        public ApprovalStatusDao build() {
            return new ApprovalStatusDao(this.builderIdentifier, this.builderApprovalStatus, this.builderVersion);
        }
    }

    @DynamoDbImmutable(builder = DbApprovalStatus.Builder.class)
    public record DbApprovalStatus(URI institutionId, DbStatus status, Username assignee,
                                   Username finalizedBy, Instant finalizedDate, String reason) {

        public static Builder builder() {
            return new Builder();
        }

        @DynamoDbIgnore
        public ApprovalStatusDao toDao(UUID candidateIdentifier) {
            return ApprovalStatusDao.builder().identifier(candidateIdentifier).approvalStatus(this).version(randomUUID().toString()).build();
        }

        @DynamoDbIgnore
        public Builder copy() {
            return builder().institutionId(institutionId)
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

        public static final class Builder {

            private URI builderInstitutionId;
            private DbStatus builderStatus;
            private Username builderAssignee;
            private Username builderFinalizedBy;
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

            public Builder assignee(Username assignee) {
                this.builderAssignee = assignee;
                return this;
            }

            public Builder finalizedBy(Username finalizedBy) {
                this.builderFinalizedBy = finalizedBy;
                return this;
            }

            public Builder finalizedDate(Instant finalizedDate) {
                this.builderFinalizedDate = finalizedDate;
                return this;
            }

            public Builder reason(String reason) {
                this.builderReason = reason;
                return this;
            }

            public DbApprovalStatus build() {
                return new DbApprovalStatus(builderInstitutionId, builderStatus,
                                            builderAssignee, builderFinalizedBy, builderFinalizedDate, builderReason);
            }
        }
    }
}
