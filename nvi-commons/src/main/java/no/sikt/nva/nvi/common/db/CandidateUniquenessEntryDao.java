package no.sikt.nva.nvi.common.db;

import static no.sikt.nva.nvi.common.DatabaseConstants.HASH_KEY;
import static no.sikt.nva.nvi.common.DatabaseConstants.SORT_KEY;
import java.util.Objects;
import no.sikt.nva.nvi.common.db.CandidateUniquenessEntryDao.Builder;
import nva.commons.core.JacocoGenerated;
import software.amazon.awssdk.enhanced.dynamodb.mapper.annotations.DynamoDbAttribute;
import software.amazon.awssdk.enhanced.dynamodb.mapper.annotations.DynamoDbIgnore;
import software.amazon.awssdk.enhanced.dynamodb.mapper.annotations.DynamoDbImmutable;
import software.amazon.awssdk.enhanced.dynamodb.mapper.annotations.DynamoDbPartitionKey;
import software.amazon.awssdk.enhanced.dynamodb.mapper.annotations.DynamoDbSortKey;

@DynamoDbImmutable(builder = Builder.class)
public final class CandidateUniquenessEntryDao extends Dao {

    public static final String TYPE = "CandidateUniquenessEntry";
    private final String partitionKey;
    private final String sortKey;
    private final String version;

    public CandidateUniquenessEntryDao(
        String partitionKey,
        String sortKey,
        String version
    ) {
        super();
        this.partitionKey = partitionKey;
        this.sortKey = sortKey;
        this.version = version;
    }

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

    @Override
    public String version() {
        return version;
    }

    @JacocoGenerated
    @Override
    @DynamoDbAttribute(TYPE_FIELD)
    public String type() {
        return TYPE;
    }

    public String partitionKey() {
        return partitionKey;
    }

    public String sortKey() {
        return sortKey;
    }

    @Override
    @JacocoGenerated
    public int hashCode() {
        return Objects.hash(partitionKey, sortKey, version);
    }

    @Override
    @JacocoGenerated
    public boolean equals(Object obj) {
        if (obj == this) {
            return true;
        }
        if (obj == null || obj.getClass() != this.getClass()) {
            return false;
        }
        var that = (CandidateUniquenessEntryDao) obj;
        return Objects.equals(this.partitionKey, that.partitionKey)
               && Objects.equals(this.sortKey, that.sortKey)
               && Objects.equals(this.version, that.version);
    }

    @Override
    @JacocoGenerated
    public String toString() {
        return "CandidateUniquenessEntryDao["
               + "partitionKey=" + partitionKey + ", "
               + "sortKey=" + sortKey + ", "
               + "version=" + version + ']';
    }

    @DynamoDbIgnore
    private static String pk0(String identifier) {
        return TYPE + FIELD_DELIMITER + identifier;
    }

    @JacocoGenerated
    public static final class Builder {

        private String builderPartitionKey;
        private String builderSortKey;
        private String builderVersion;

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
            this.builderVersion = version;
            return this;
        }

        public CandidateUniquenessEntryDao build() {
            return new CandidateUniquenessEntryDao(builderPartitionKey, builderSortKey, builderVersion);
        }
    }
}
