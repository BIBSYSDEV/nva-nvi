package no.sikt.nva.nvi.common.db;

import static no.sikt.nva.nvi.common.DatabaseConstants.HASH_KEY;
import static no.sikt.nva.nvi.common.DatabaseConstants.SORT_KEY;
import static no.sikt.nva.nvi.common.DatabaseConstants.DATA_FIELD;
import java.util.UUID;
import no.sikt.nva.nvi.common.db.model.DbApprovalStatus;
import nva.commons.core.JacocoGenerated;
import software.amazon.awssdk.enhanced.dynamodb.mapper.annotations.DynamoDbAttribute;
import software.amazon.awssdk.enhanced.dynamodb.mapper.annotations.DynamoDbImmutable;
import software.amazon.awssdk.enhanced.dynamodb.mapper.annotations.DynamoDbPartitionKey;
import software.amazon.awssdk.enhanced.dynamodb.mapper.annotations.DynamoDbSortKey;

@DynamoDbImmutable(builder = ApprovalStatusDao.Builder.class)
public record ApprovalStatusDao(

    UUID identifier,
    @DynamoDbAttribute(DATA_FIELD) DbApprovalStatus approvalStatus
) implements DynamoEntryWithRangeKey {

    public static final String TYPE = "APPROVAL_STATUS";

    public static String sk0(String institutionUri) {
        return String.join(FIELD_DELIMITER, TYPE, institutionUri);
    }

    public static Builder builder() {
        return new Builder();
    }

    @Override
    @DynamoDbPartitionKey
    @DynamoDbAttribute(HASH_KEY)
    public String primaryKeyHashKey() {
        return CandidateDao.pk0(identifier.toString());
    }

    @Override
    @DynamoDbSortKey
    @DynamoDbAttribute(SORT_KEY)
    public String primaryKeyRangeKey() {
        return sk0(approvalStatus.institutionId().toString());
    }

    @Override
    @JacocoGenerated
    @DynamoDbAttribute(TYPE_FIELD)
    public String type() {
        return TYPE;
    }

    public static final class Builder {

        private UUID identifier;
        private DbApprovalStatus approvalStatus;

        private Builder() {
        }

        public Builder identifier(UUID identifier) {
            this.identifier = identifier;
            return this;
        }

        public Builder type(String noop) {
            return this;
        }

        public Builder primaryKeyHashKey(String noop) {
            return this;
        }

        public Builder primaryKeyRangeKey(String noop) {
            return this;
        }

        public Builder approvalStatus(DbApprovalStatus approvalStatus) {
            this.approvalStatus = approvalStatus;
            return this;
        }

        public ApprovalStatusDao build() {
            return new ApprovalStatusDao(this.identifier, this.approvalStatus);
        }
    }
}
