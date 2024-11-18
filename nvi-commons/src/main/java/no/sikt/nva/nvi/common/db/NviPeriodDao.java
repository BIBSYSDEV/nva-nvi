package no.sikt.nva.nvi.common.db;

import static no.sikt.nva.nvi.common.DatabaseConstants.DATA_FIELD;
import static no.sikt.nva.nvi.common.DatabaseConstants.HASH_KEY;
import static no.sikt.nva.nvi.common.DatabaseConstants.IDENTIFIER_FIELD;
import static no.sikt.nva.nvi.common.DatabaseConstants.SORT_KEY;
import static no.sikt.nva.nvi.common.DatabaseConstants.VERSION_FIELD;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.time.Instant;
import no.sikt.nva.nvi.common.db.NviPeriodDao.Builder;
import no.sikt.nva.nvi.common.db.model.Username;
import java.net.URI;
import nva.commons.core.JacocoGenerated;
import software.amazon.awssdk.enhanced.dynamodb.TableSchema;
import software.amazon.awssdk.enhanced.dynamodb.mapper.annotations.DynamoDbAttribute;
import software.amazon.awssdk.enhanced.dynamodb.mapper.annotations.DynamoDbIgnore;
import software.amazon.awssdk.enhanced.dynamodb.mapper.annotations.DynamoDbImmutable;
import software.amazon.awssdk.enhanced.dynamodb.mapper.annotations.DynamoDbPartitionKey;
import software.amazon.awssdk.enhanced.dynamodb.mapper.annotations.DynamoDbSortKey;

@DynamoDbImmutable(builder = Builder.class)
public final class NviPeriodDao extends Dao {

    public static final TableSchema<NviPeriodDao> TABLE_SCHEMA = TableSchema.fromClass(NviPeriodDao.class);

    public static final String TYPE = "PERIOD";
    @JsonProperty(IDENTIFIER_FIELD)
    private final String identifier;
    @JsonProperty(DATA_FIELD)
    private final DbNviPeriod nviPeriod;
    @JsonProperty(VERSION_FIELD)
    private final String version;

    public NviPeriodDao(@JsonProperty(IDENTIFIER_FIELD) String identifier,
                        @JsonProperty(DATA_FIELD) DbNviPeriod nviPeriod,
                        @JsonProperty(VERSION_FIELD) String version) {
        super();
        this.identifier = identifier;
        this.nviPeriod = nviPeriod;
        this.version = version;
    }

    public static Builder builder() {
        return new Builder();
    }

    @Override
    @DynamoDbPartitionKey
    @DynamoDbAttribute(HASH_KEY)
    @JsonProperty(HASH_KEY)
    public String primaryKeyHashKey() {
        return TYPE;
    }

    @Override
    @DynamoDbSortKey
    @DynamoDbAttribute(SORT_KEY)
    @JsonProperty(SORT_KEY)
    public String primaryKeyRangeKey() {
        return String.join(FIELD_DELIMITER, TYPE, identifier);
    }

    @Override
    public String version() {
        return version;
    }

    @Override
    @JacocoGenerated
    @DynamoDbAttribute(TYPE_FIELD)
    public String type() {
        return TYPE;
    }

    public String identifier() {
        return identifier;
    }

    @DynamoDbAttribute(DATA_FIELD)
    public DbNviPeriod nviPeriod() {
        return nviPeriod;
    }

    public static final class Builder {

        private String builderIdentifier;
        private DbNviPeriod builderNviPeriod;
        private String builderVersion;

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

        public Builder version(String version) {
            this.builderVersion = version;
            return this;
        }

        public NviPeriodDao build() {
            return new NviPeriodDao(builderIdentifier, builderNviPeriod, builderVersion);
        }
    }

    @DynamoDbImmutable(builder = DbNviPeriod.Builder.class)
    public record DbNviPeriod(URI id,
                              String publishingYear,
                              Instant startDate,
                              Instant reportingDate,
                              Username createdBy,
                              Username modifiedBy) {

        public static Builder builder() {
            return new Builder();
        }

        @DynamoDbIgnore
        public Builder copy() {
            return builder()
                       .id(id)
                       .publishingYear(publishingYear)
                       .startDate(startDate)
                       .reportingDate(reportingDate)
                       .createdBy(createdBy)
                       .modifiedBy(modifiedBy);
        }

        public static final class Builder {

            private URI builderId;
            private String builderPublishingYear;
            private Instant builderStartDate;
            private Instant builderReportingDate;
            private Username builderCreatedBy;
            private Username builderModifiedBy;

            private Builder() {
            }

            @SuppressWarnings("PMD.ShortMethodName")
            public Builder id(URI id) {
                this.builderId = id;
                return this;
            }

            public Builder publishingYear(String publishingYear) {
                this.builderPublishingYear = publishingYear;
                return this;
            }

            public Builder startDate(Instant startDate) {
                this.builderStartDate = startDate;
                return this;
            }

            public Builder reportingDate(Instant reportingDate) {
                this.builderReportingDate = reportingDate;
                return this;
            }

            public Builder createdBy(Username createdBy) {
                this.builderCreatedBy = createdBy;
                return this;
            }

            public Builder modifiedBy(Username modifiedBy) {
                this.builderModifiedBy = modifiedBy;
                return this;
            }

            public DbNviPeriod build() {
                return new DbNviPeriod(builderId, builderPublishingYear, builderStartDate, builderReportingDate,
                                       builderCreatedBy,
                                       builderModifiedBy);
            }

        }
    }
}
