package no.sikt.nva.nvi.common.db;

import static no.sikt.nva.nvi.common.DatabaseConstants.HASH_KEY;
import static no.sikt.nva.nvi.common.DatabaseConstants.SORT_KEY;
import nva.commons.core.JacocoGenerated;
import software.amazon.awssdk.enhanced.dynamodb.mapper.annotations.DynamoDbAttribute;
import software.amazon.awssdk.enhanced.dynamodb.mapper.annotations.DynamoDbIgnore;
import software.amazon.awssdk.enhanced.dynamodb.mapper.annotations.DynamoDbImmutable;
import software.amazon.awssdk.enhanced.dynamodb.mapper.annotations.DynamoDbPartitionKey;
import software.amazon.awssdk.enhanced.dynamodb.mapper.annotations.DynamoDbSortKey;

@DynamoDbImmutable(builder = CandidateUniquenessEntryDao.Builder.class)
public record CandidateUniquenessEntryDao(
    String partitionKey,
    String sortKey,
    String version
) implements DynamoEntryWithRangeKey {

    public static final String TYPE = "CandidateUniquenessEntry";

    public CandidateUniquenessEntryDao(String identifier) {
        this(pk0(identifier), pk0(identifier), null);
    }

    @JacocoGenerated
    public static Builder builder() {
        return new Builder();
    }

    @JacocoGenerated
    @Override
    @DynamoDbPartitionKey
    @DynamoDbAttribute(HASH_KEY)
    public String primaryKeyHashKey() {
        return partitionKey;
    }

    @JacocoGenerated
    @Override
    @DynamoDbSortKey
    @DynamoDbAttribute(SORT_KEY)
    public String primaryKeyRangeKey() {
        return sortKey;
    }

    @JacocoGenerated
    @Override
    @DynamoDbAttribute(TYPE_FIELD)
    public String type() {
        return TYPE;
    }

    @DynamoDbIgnore
    private static String pk0(String identifier) {
        return TYPE + FIELD_DELIMITER + identifier;
    }

    @JacocoGenerated
    public static final class Builder {

        private String builderPartitionKey;
        private String builderSortKey;
        private String version;

        private Builder() {
        }

        @JacocoGenerated
        public Builder primaryKeyHashKey(String noop) {
            // Used by @DynamoDbImmutable for building the object
            return this;
        }

        @JacocoGenerated
        public Builder primaryKeyRangeKey(String noop) {
            // Used by @DynamoDbImmutable for building the object
            return this;
        }

        @JacocoGenerated
        public Builder type(String noop) {
            // Used by @DynamoDbImmutable for building the object
            return this;
        }

        public Builder partitionKey(String partitionKey) {
            this.builderPartitionKey = partitionKey;
            return this;
        }

        public Builder sortKey(String sortKey) {
            this.builderSortKey = sortKey;
            return this;
        }

        public Builder version(String version) {
            this.version = version;
            return this;
        }

        public CandidateUniquenessEntryDao build() {
            return new CandidateUniquenessEntryDao(builderPartitionKey, builderSortKey, version);
        }
    }
}
