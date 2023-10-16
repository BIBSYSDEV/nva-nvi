package no.sikt.nva.nvi.common.db;

import static java.util.Objects.nonNull;
import static no.sikt.nva.nvi.common.DatabaseConstants.DATA_FIELD;
import static no.sikt.nva.nvi.common.DatabaseConstants.HASH_KEY;
import static no.sikt.nva.nvi.common.DatabaseConstants.SECONDARY_INDEX_1_HASH_KEY;
import static no.sikt.nva.nvi.common.DatabaseConstants.SECONDARY_INDEX_1_RANGE_KEY;
import static no.sikt.nva.nvi.common.DatabaseConstants.SECONDARY_INDEX_PUBLICATION_ID;
import static no.sikt.nva.nvi.common.DatabaseConstants.SORT_KEY;
import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;
import java.math.BigDecimal;
import java.net.URI;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;
import no.sikt.nva.nvi.common.db.model.ChannelType;
import no.sikt.nva.nvi.common.db.model.InstanceType;
import nva.commons.core.JacocoGenerated;
import software.amazon.awssdk.enhanced.dynamodb.mapper.annotations.DynamoDbAttribute;
import software.amazon.awssdk.enhanced.dynamodb.mapper.annotations.DynamoDbIgnore;
import software.amazon.awssdk.enhanced.dynamodb.mapper.annotations.DynamoDbImmutable;
import software.amazon.awssdk.enhanced.dynamodb.mapper.annotations.DynamoDbPartitionKey;
import software.amazon.awssdk.enhanced.dynamodb.mapper.annotations.DynamoDbSecondaryPartitionKey;
import software.amazon.awssdk.enhanced.dynamodb.mapper.annotations.DynamoDbSecondarySortKey;
import software.amazon.awssdk.enhanced.dynamodb.mapper.annotations.DynamoDbSortKey;

@DynamoDbImmutable(builder = CandidateDao.Builder.class)
public record CandidateDao(
    UUID identifier,
    @DynamoDbAttribute(DATA_FIELD) DbCandidate candidate,
    String version
) implements DynamoEntryWithRangeKey {

    public static final String TYPE = "CANDIDATE";

    @DynamoDbIgnore
    public static String createPartitionKey(String identifier) {
        return String.join(FIELD_DELIMITER, TYPE, identifier);
    }

    public static Builder builder() {
        return new Builder();
    }

    @Override
    @DynamoDbPartitionKey
    @DynamoDbAttribute(HASH_KEY)
    public String primaryKeyHashKey() {
        return createPartitionKey(identifier.toString());
    }

    @Override
    @DynamoDbSortKey
    @DynamoDbAttribute(SORT_KEY)
    public String primaryKeyRangeKey() {
        return primaryKeyHashKey();
    }

    @Override
    @JacocoGenerated
    @DynamoDbAttribute(TYPE_FIELD)
    public String type() {
        return TYPE;
    }

    @JacocoGenerated
    @DynamoDbSecondaryPartitionKey(indexNames = {SECONDARY_INDEX_PUBLICATION_ID})
    @DynamoDbAttribute(SECONDARY_INDEX_1_HASH_KEY)
    public String searchByPublicationIdHashKey() {
        return nonNull(candidate.publicationId()) ? candidate.publicationId().toString() : null;
    }

    @JacocoGenerated
    @DynamoDbSecondarySortKey(indexNames = {SECONDARY_INDEX_PUBLICATION_ID})
    @DynamoDbAttribute(SECONDARY_INDEX_1_RANGE_KEY)
    public String searchByPublicationIdSortKey() {
        return nonNull(candidate.publicationId()) ? candidate.publicationId().toString() : null;
    }

    @DynamoDbIgnore
    public CandidateDao.Builder copy() {
        return builder()
                   .identifier(identifier)
                   .candidate(candidate);
    }

    public enum DbLevel {
        UNASSIGNED("Unassigned"), LEVEL_ONE("1"), LEVEL_TWO("2"), NON_CANDIDATE("NonCandidateLevel");

        @JsonValue
        private final String value;

        DbLevel(String value) {

            this.value = value;
        }

        @JsonCreator
        public static DbLevel parse(String value) {
            return Arrays
                       .stream(DbLevel.values())
                       .filter(level -> level.getValue().equalsIgnoreCase(value))
                       .findFirst()
                       .orElse(NON_CANDIDATE);
        }

        public String getValue() {
            return value;
        }
    }

    public static final class Builder {

        private UUID builderIdentifier;
        private DbCandidate builderCandidate;
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

        public Builder searchByPublicationIdHashKey(String noop) {
            // Used by @DynamoDbImmutable for building the object
            return this;
        }

        public Builder searchByPublicationIdSortKey(String noop) {
            // Used by @DynamoDbImmutable for building the object
            return this;
        }

        public Builder identifier(UUID identifier) {
            this.builderIdentifier = identifier;
            return this;
        }

        public Builder candidate(DbCandidate candidate) {
            this.builderCandidate = candidate;
            return this;
        }

        public Builder version(String version) {
            this.builderVersion = version;
            return this;
        }

        public CandidateDao build() {
            return new CandidateDao(builderIdentifier, builderCandidate, builderVersion);
        }
    }

    @DynamoDbImmutable(builder = DbCandidate.Builder.class)
    public record DbCandidate(
        URI publicationId,
        URI publicationBucketUri,
        boolean applicable,
        InstanceType instanceType,
        ChannelType channelType,
        URI channelId,
        DbLevel level,
        DbPublicationDate publicationDate,
        boolean internationalCollaboration,
        BigDecimal collaborationFactor,
        int creatorCount,
        int creatorShareCount,
        List<DbCreator> creators,
        BigDecimal basePoints,
        List<DbInstitutionPoints> points,
        BigDecimal totalPoints
    ) {

        public static Builder builder() {
            return new Builder();
        }

        @DynamoDbIgnore
        public Builder copy() {
            return builder()
                       .publicationId(publicationId)
                       .publicationBucketUri(publicationBucketUri)
                       .applicable(applicable)
                       .instanceType(instanceType)
                       .channelType(channelType)
                       .channelId(channelId)
                       .level(level)
                       .publicationDate(publicationDate.copy())
                       .internationalCollaboration(internationalCollaboration)
                       .collaborationFactor(collaborationFactor)
                       .creatorCount(creatorCount)
                       .creatorShareCount(creatorShareCount)
                       .creators(creators.stream().map(DbCreator::copy).toList())
                       .basePoints(basePoints)
                       .points(points.stream().map(DbInstitutionPoints::copy).toList())
                       .totalPoints(totalPoints);
        }

        public static final class Builder {

            private URI builderPublicationId;
            private URI builderPublicationBucketUri;
            private boolean builderApplicable;
            private InstanceType builderInstanceType;
            private ChannelType builderChannelType;
            private URI builderChannelId;
            private DbLevel builderLevel;
            private DbPublicationDate builderPublicationDate;
            private boolean builderInternationalCollaboration;
            private BigDecimal builderCollaborationFactor;
            private int builderCreatorCount;
            private int builderCreatorShareCount;
            private List<DbCreator> builderCreators;
            private BigDecimal builderBasePoints;
            private List<DbInstitutionPoints> builderPoints;
            private BigDecimal builderTotalPoints;

            private Builder() {
            }

            public Builder publicationId(URI publicationId) {
                this.builderPublicationId = publicationId;
                return this;
            }

            public Builder publicationBucketUri(URI publicationBucketUri) {
                this.builderPublicationBucketUri = publicationBucketUri;
                return this;
            }

            public Builder applicable(boolean applicable) {
                this.builderApplicable = applicable;
                return this;
            }

            public Builder instanceType(InstanceType instanceType) {
                this.builderInstanceType = instanceType;
                return this;
            }

            public Builder channelType(ChannelType channelType) {
                this.builderChannelType = channelType;
                return this;
            }

            public Builder channelId(URI channelId) {
                this.builderChannelId = channelId;
                return this;
            }

            public Builder level(DbLevel level) {
                this.builderLevel = level;
                return this;
            }

            public Builder publicationDate(DbPublicationDate publicationDate) {
                this.builderPublicationDate = publicationDate;
                return this;
            }

            public Builder internationalCollaboration(boolean internationalCollaboration) {
                this.builderInternationalCollaboration = internationalCollaboration;
                return this;
            }

            public Builder collaborationFactor(BigDecimal collaborationFactor) {
                this.builderCollaborationFactor = collaborationFactor;
                return this;
            }

            public Builder creatorCount(int creatorCount) {
                this.builderCreatorCount = creatorCount;
                return this;
            }

            public Builder creatorShareCount(int creatorShareCount) {
                this.builderCreatorShareCount = creatorShareCount;
                return this;
            }

            public Builder creators(List<DbCreator> creators) {
                this.builderCreators = creators;
                return this;
            }

            public Builder basePoints(BigDecimal basePoints) {
                this.builderBasePoints = basePoints;
                return this;
            }

            public Builder points(List<DbInstitutionPoints> points) {
                this.builderPoints = points;
                return this;
            }

            public Builder totalPoints(BigDecimal totalPoints) {
                this.builderTotalPoints = totalPoints;
                return this;
            }

            public DbCandidate build() {
                return new DbCandidate(builderPublicationId, builderPublicationBucketUri, builderApplicable,
                                       builderInstanceType, builderChannelType,
                                       builderChannelId, builderLevel, builderPublicationDate,
                                       builderInternationalCollaboration,
                                       builderCollaborationFactor,
                                       builderCreatorCount, builderCreatorShareCount, builderCreators,
                                       builderBasePoints, builderPoints, builderTotalPoints);
            }
        }
    }

    @DynamoDbImmutable(builder = DbPublicationDate.Builder.class)
    public record DbPublicationDate(String year, String month, String day) {

        public static Builder builder() {
            return new Builder();
        }

        @DynamoDbIgnore
        public DbPublicationDate copy() {
            return new DbPublicationDate(year, month, day);
        }

        public static final class Builder {

            private String builderYear;
            private String builderMonth;
            private String builderDay;

            private Builder() {
            }

            public Builder year(String year) {
                this.builderYear = year;
                return this;
            }

            public Builder month(String month) {
                this.builderMonth = month;
                return this;
            }

            public Builder day(String day) {
                this.builderDay = day;
                return this;
            }

            public DbPublicationDate build() {
                return new DbPublicationDate(builderYear, builderMonth, builderDay);
            }
        }
    }

    @DynamoDbImmutable(builder = DbCreator.Builder.class)
    public record DbCreator(URI creatorId, List<URI> affiliations) {

        public static Builder builder() {
            return new Builder();
        }

        @DynamoDbIgnore
        public DbCreator copy() {
            return builder()
                       .creatorId(creatorId)
                       .affiliations(new ArrayList<>(affiliations))
                       .build();
        }

        public static final class Builder {

            private URI builderCreatorId;
            private List<URI> builderAffiliations;

            private Builder() {
            }

            public Builder creatorId(URI creatorId) {
                this.builderCreatorId = creatorId;
                return this;
            }

            public Builder affiliations(List<URI> affiliations) {
                this.builderAffiliations = affiliations;
                return this;
            }

            public DbCreator build() {
                return new DbCreator(builderCreatorId, builderAffiliations);
            }
        }
    }

    @DynamoDbImmutable(builder = DbInstitutionPoints.Builder.class)
    public record DbInstitutionPoints(URI institutionId, BigDecimal points) {

        public static Builder builder() {
            return new Builder();
        }

        @DynamoDbIgnore
        public DbInstitutionPoints copy() {
            return builder()
                       .institutionId(institutionId)
                       .points(points)
                       .build();
        }

        public static final class Builder {

            private URI builderInstitutionId;
            private BigDecimal builderPoints;

            private Builder() {
            }

            public Builder institutionId(URI institutionId) {
                this.builderInstitutionId = institutionId;
                return this;
            }

            public Builder points(BigDecimal points) {
                this.builderPoints = points;
                return this;
            }

            public DbInstitutionPoints build() {
                return new DbInstitutionPoints(builderInstitutionId, builderPoints);
            }
        }
    }
}
