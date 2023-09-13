package no.sikt.nva.nvi.common.db;

import static no.sikt.nva.nvi.common.DatabaseConstants.HASH_KEY;
import static no.sikt.nva.nvi.common.DatabaseConstants.SORT_KEY;
import static no.sikt.nva.nvi.common.DatabaseConstants.DATA_FIELD;
import no.sikt.nva.nvi.common.db.model.DbNviPeriod;
import nva.commons.core.JacocoGenerated;
import software.amazon.awssdk.enhanced.dynamodb.TableSchema;
import software.amazon.awssdk.enhanced.dynamodb.mapper.annotations.DynamoDbAttribute;
import software.amazon.awssdk.enhanced.dynamodb.mapper.annotations.DynamoDbImmutable;
import software.amazon.awssdk.enhanced.dynamodb.mapper.annotations.DynamoDbPartitionKey;
import software.amazon.awssdk.enhanced.dynamodb.mapper.annotations.DynamoDbSortKey;

@DynamoDbImmutable(builder = NviPeriodDao.Builder.class)
public record NviPeriodDao(

    String identifier,
    @DynamoDbAttribute(DATA_FIELD)
    DbNviPeriod nviPeriod
) implements DynamoEntryWithRangeKey {

    public static final TableSchema<NviPeriodDao> TABLE_SCHEMA = TableSchema.fromClass(NviPeriodDao.class);

    public static final String TYPE = "PERIOD";

    public NviPeriodDao(DbNviPeriod nviPeriod) {
        this(nviPeriod.publishingYear(), nviPeriod);
    }

    public static Builder builder() {
        return new Builder();
    }

    @Override
    @DynamoDbPartitionKey
    @DynamoDbAttribute(HASH_KEY)
    public String primaryKeyHashKey() {
        return TYPE;
    }

    @Override
    @DynamoDbSortKey
    @DynamoDbAttribute(SORT_KEY)
    public String primaryKeyRangeKey() {
        return String.join(DynamoEntryWithRangeKey.FIELD_DELIMITER, TYPE, identifier);
    }

    @Override
    @JacocoGenerated
    @DynamoDbAttribute(TYPE_FIELD)
    public String type() {
        return TYPE;
    }

    public static final class Builder {

        private String builderIdentifier;
        private DbNviPeriod builderNviPeriod;

        private Builder() {
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

        public Builder identifier(String identifier) {
            this.builderIdentifier = identifier;
            return this;
        }

        public Builder nviPeriod(DbNviPeriod nviPeriod) {
            this.builderNviPeriod = nviPeriod;
            return this;
        }

        public NviPeriodDao build() {
            return new NviPeriodDao(builderIdentifier, builderNviPeriod);
        }
    }
}
