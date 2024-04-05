package no.sikt.nva.nvi.common.db;

import static java.util.Objects.isNull;
import static java.util.Objects.nonNull;
import static no.sikt.nva.nvi.common.DatabaseConstants.DATA_FIELD;
import static no.sikt.nva.nvi.common.DatabaseConstants.HASH_KEY;
import static no.sikt.nva.nvi.common.DatabaseConstants.IDENTIFIER_FIELD;
import static no.sikt.nva.nvi.common.DatabaseConstants.SECONDARY_INDEX_1_HASH_KEY;
import static no.sikt.nva.nvi.common.DatabaseConstants.SECONDARY_INDEX_1_RANGE_KEY;
import static no.sikt.nva.nvi.common.DatabaseConstants.SECONDARY_INDEX_PUBLICATION_ID;
import static no.sikt.nva.nvi.common.DatabaseConstants.SECONDARY_INDEX_YEAR;
import static no.sikt.nva.nvi.common.DatabaseConstants.SECONDARY_INDEX_YEAR_HASH_KEY;
import static no.sikt.nva.nvi.common.DatabaseConstants.SECONDARY_INDEX_YEAR_RANGE_KEY;
import static no.sikt.nva.nvi.common.DatabaseConstants.SORT_KEY;
import static no.sikt.nva.nvi.common.DatabaseConstants.VERSION_FIELD;
import static nva.commons.core.attempt.Try.attempt;
import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.math.BigDecimal;
import java.net.URI;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
import no.sikt.nva.nvi.common.db.CandidateDao.Builder;
import no.sikt.nva.nvi.common.db.model.ChannelType;
import no.sikt.nva.nvi.common.db.model.InstanceType;
import no.unit.nva.commons.json.JsonUtils;
import nva.commons.core.JacocoGenerated;
import org.slf4j.Logger;
import software.amazon.awssdk.enhanced.dynamodb.mapper.annotations.DynamoDbAttribute;
import software.amazon.awssdk.enhanced.dynamodb.mapper.annotations.DynamoDbIgnore;
import software.amazon.awssdk.enhanced.dynamodb.mapper.annotations.DynamoDbImmutable;
import software.amazon.awssdk.enhanced.dynamodb.mapper.annotations.DynamoDbPartitionKey;
import software.amazon.awssdk.enhanced.dynamodb.mapper.annotations.DynamoDbSecondaryPartitionKey;
import software.amazon.awssdk.enhanced.dynamodb.mapper.annotations.DynamoDbSecondarySortKey;
import software.amazon.awssdk.enhanced.dynamodb.mapper.annotations.DynamoDbSortKey;

@DynamoDbImmutable(builder = Builder.class)
public final class CandidateDao extends Dao {

    public static final String TYPE = "CANDIDATE";
    public static final String PERIOD_YEAR_FIELD = "periodYear";
    private static final Logger logger = org.slf4j.LoggerFactory.getLogger(CandidateDao.class);
    @JsonProperty(IDENTIFIER_FIELD)
    private final UUID identifier;
    @JsonProperty(DATA_FIELD)
    private final DbCandidate candidate;
    @JsonProperty(VERSION_FIELD)
    private final String version;
    @JsonProperty(PERIOD_YEAR_FIELD)
    private final String periodYear;

    @JsonCreator
    public CandidateDao(
        @JsonProperty(IDENTIFIER_FIELD) UUID identifier,
        @JsonProperty(DATA_FIELD) DbCandidate candidate,
        @JsonProperty(VERSION_FIELD) String version,
        @JsonProperty(PERIOD_YEAR_FIELD) String periodYear
    ) {
        super();
        this.identifier = identifier;
        this.candidate = candidate;
        this.version = version;
        this.periodYear = periodYear;
    }

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
    @JsonProperty(HASH_KEY)
    public String primaryKeyHashKey() {
        return createPartitionKey(identifier.toString());
    }

    @Override
    @DynamoDbSortKey
    @DynamoDbAttribute(SORT_KEY)
    @JsonProperty(SORT_KEY)
    public String primaryKeyRangeKey() {
        return primaryKeyHashKey();
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

    @JacocoGenerated
    @DynamoDbSecondaryPartitionKey(indexNames = {SECONDARY_INDEX_PUBLICATION_ID})
    @DynamoDbAttribute(SECONDARY_INDEX_1_HASH_KEY)
    @JsonProperty(SECONDARY_INDEX_1_HASH_KEY)
    public String searchByPublicationIdHashKey() {
        return nonNull(candidate.publicationId()) ? candidate.publicationId().toString() : null;
    }

    @JacocoGenerated
    @DynamoDbSecondarySortKey(indexNames = {SECONDARY_INDEX_PUBLICATION_ID})
    @DynamoDbAttribute(SECONDARY_INDEX_1_RANGE_KEY)
    @JsonProperty(SECONDARY_INDEX_1_RANGE_KEY)
    public String searchByPublicationIdSortKey() {
        return nonNull(candidate.publicationId()) ? candidate.publicationId().toString() : null;
    }

    @JacocoGenerated
    @DynamoDbSecondaryPartitionKey(indexNames = {SECONDARY_INDEX_YEAR})
    @DynamoDbAttribute(SECONDARY_INDEX_YEAR_HASH_KEY)
    @JsonProperty(SECONDARY_INDEX_YEAR_HASH_KEY)
    public String searchByYearHashKey() {
        return periodYear;
    }

    @JacocoGenerated
    @DynamoDbSecondarySortKey(indexNames = {SECONDARY_INDEX_YEAR})
    @DynamoDbAttribute(SECONDARY_INDEX_YEAR_RANGE_KEY)
    @JsonProperty(SECONDARY_INDEX_YEAR_RANGE_KEY)
    public String searchByYearSortKey() {
        return nonNull(identifier) ? identifier.toString() : null;
    }

    @DynamoDbIgnore
    public Builder copy() {
        return builder()
                   .identifier(identifier)
                   .candidate(candidate)
                   .periodYear(periodYear)
                   .version(version);
    }

    public UUID identifier() {
        return identifier;
    }

    @DynamoDbAttribute(DATA_FIELD)
    public DbCandidate candidate() {
        return candidate;
    }

    @Override
    @JacocoGenerated
    public int hashCode() {
        return Objects.hash(identifier, candidate, version, periodYear);
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
        var that = (CandidateDao) obj;
        return Objects.equals(this.identifier, that.identifier)
               && Objects.equals(this.candidate, that.candidate)
               && Objects.equals(this.version, that.version)
               && Objects.equals(this.periodYear, that.periodYear);
    }

    @Override
    @JacocoGenerated
    public String toString() {
        return attempt(() -> JsonUtils.dtoObjectMapper.writeValueAsString(this)).orElseThrow();
    }

    @DynamoDbAttribute(PERIOD_YEAR_FIELD)
    public String periodYear() {
        var applicableAndMissingPeriodYear = isApplicableAndMissingPeriodYear();
        logger.info("Candidate identifier: {}. Applicable and missing period year: {}",
                    identifier, applicableAndMissingPeriodYear);
        if (applicableAndMissingPeriodYear) {
            return migratePeriodYear();
        } else {
            logger.info("Returning periodYear for candidate with identifier: {}. periodYear: {}",
                        identifier, periodYear);
            return periodYear;
        }
    }

    @Deprecated
    private String migratePeriodYear() {
        var periodYear = candidate.publicationDate().year();
        logger.info("Migrating period year for candidate with identifier: {}. New periodYear: {}", identifier,
                    periodYear);
        return periodYear;
    }

    private boolean isApplicableAndMissingPeriodYear() {
        return isNull(periodYear) && candidate.applicable();
    }

    public enum DbLevel {
        LEVEL_ONE(List.of("1", "LevelOne")), LEVEL_TWO(List.of("2", "LevelTwo")), NON_CANDIDATE(
            List.of("NonCandidateLevel"));

        private final List<String> values;

        DbLevel(List<String> values) {

            this.values = values;
        }

        public static DbLevel parse(String string) {
            return Arrays.stream(DbLevel.values())
                       .filter(level -> level.getValues().contains(string))
                       .findFirst()
                       .orElse(NON_CANDIDATE);
        }

        @Deprecated
        public String getVersionOneValue() {
            return values.get(0);
        }

        public List<String> getValues() {
            return values;
        }
    }

    public static final class Builder {

        private UUID builderIdentifier;
        private DbCandidate builderCandidate;
        private String builderVersion;
        private String builderPeriodYear;

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

        public Builder searchByYearHashKey(String noop) {
            // Used by @DynamoDbImmutable for building the object
            return this;
        }

        public Builder searchByYearSortKey(String noop) {
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

        public Builder periodYear(String periodYear) {
            var applicableAndMissingPeriodYear = isApplicableAndMissingPeriodYear();
            logger.info("Candidate identifier: {}. Applicable and missing period year: {}",
                        builderIdentifier, applicableAndMissingPeriodYear);
            if (applicableAndMissingPeriodYear) {
                this.builderPeriodYear = migratePeriodYear();
            } else {
                logger.info("Returning periodYear for candidate with identifier: {}. periodYear: {}",
                            builderIdentifier, periodYear);
                this.builderPeriodYear = periodYear;
            }
            return this;
        }

        public CandidateDao build() {
            return new CandidateDao(builderIdentifier, builderCandidate, builderVersion, builderPeriodYear);
        }

        private boolean isApplicableAndMissingPeriodYear() {
            return isNull(builderPeriodYear) && builderCandidate.applicable();
        }

        private String migratePeriodYear() {
            var periodYear = builderCandidate.publicationDate().year();
            logger.info("Migrating period year for candidate with identifier: {}. New periodYear: {}",
                        builderIdentifier,
                        periodYear);
            return periodYear;
        }
    }

    @DynamoDbImmutable(builder = DbCandidate.Builder.class)
    public record DbCandidate(URI publicationId,
                              URI publicationBucketUri,
                              boolean applicable,
                              String instanceType,
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
                              BigDecimal totalPoints,
                              Instant createdDate,
                              Instant modifiedDate,
                              ReportStatus reportStatus
    ) {

        public static Builder builder() {
            return new Builder();
        }

        @Deprecated
        //TODO: Should be removed once we have migrated instanceType to String
        public String instanceType() {
            var enums = Arrays.stream(InstanceType.values()).toList();
            var instanceTypeEnum = enums.stream()
                                       .filter(value -> value.toString().equals(instanceType))
                                       .findFirst();
            if (instanceTypeEnum.isPresent()) {
                return instanceTypeEnum.get().getValue();
            } else {
                return instanceType;
            }
        }

        //TODO: Remove after migration
        public Instant modifiedDate() {
            return migrateDate(modifiedDate);
        }

        //TODO: Remove after migration
        public Instant createdDate() {
            return migrateDate(createdDate);
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
                       .totalPoints(totalPoints)
                       .createdDate(createdDate)
                       .modifiedDate(modifiedDate)
                       .reportStatus(reportStatus);
        }

        @Override
        @DynamoDbIgnore
        @JacocoGenerated
        public boolean equals(Object o) {
            if (this == o) {
                return true;
            }
            if (o == null || getClass() != o.getClass()) {
                return false;
            }
            DbCandidate that = (DbCandidate) o;
            return applicable == that.applicable
                   && internationalCollaboration == that.internationalCollaboration
                   && creatorCount == that.creatorCount
                   && creatorShareCount == that.creatorShareCount
                   && Objects.equals(publicationId, that.publicationId)
                   && Objects.equals(publicationBucketUri, that.publicationBucketUri)
                   && Objects.equals(instanceType, that.instanceType)
                   && channelType == that.channelType
                   && Objects.equals(channelId, that.channelId)
                   && level == that.level
                   && Objects.equals(publicationDate, that.publicationDate)
                   && Objects.equals(collaborationFactor, that.collaborationFactor)
                   && Objects.equals(creators, that.creators)
                   && Objects.equals(basePoints, that.basePoints)
                   && Objects.equals(points, that.points)
                   && Objects.equals(totalPoints, that.totalPoints)
                   && Objects.equals(createdDate, that.createdDate)
                   && Objects.equals(reportStatus, that.reportStatus);
        }

        @Override
        @DynamoDbIgnore
        @JacocoGenerated
        public int hashCode() {
            return Objects.hash(publicationId, publicationBucketUri, applicable, instanceType, channelType, channelId,
                                level,
                                publicationDate, internationalCollaboration, collaborationFactor, creatorCount,
                                creatorShareCount, creators, basePoints, points, totalPoints, createdDate,
                                reportStatus);
        }

        @Deprecated
        private static Instant migrateDate(Instant instant) {
            if (isNull(instant)) {
                return Instant.now();
            } else {
                return instant;
            }
        }

        public static final class Builder {

            private URI builderPublicationId;
            private URI builderPublicationBucketUri;
            private boolean builderApplicable;
            private String builderInstanceType;
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
            private Instant builderCreatedDate;
            private Instant builderModifiedDate;
            private ReportStatus builderReportStatus;

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

            //TODO: Should be removed once we have migrated instanceType to String
            public Builder instanceType(String instanceType) {
                var enums = Arrays.stream(InstanceType.values()).toList();
                var instanceTypeEnum = enums.stream()
                                           .filter(value -> value.toString().equals(instanceType))
                                           .findFirst();
                if (instanceTypeEnum.isPresent()) {
                    this.builderInstanceType = instanceTypeEnum.get().getValue();
                } else {
                    this.builderInstanceType = instanceType;
                }
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

            public Builder createdDate(Instant createdDate) {
                this.builderCreatedDate = migrateDate(createdDate);
                return this;
            }

            public Builder modifiedDate(Instant modifiedDate) {
                this.builderModifiedDate = migrateDate(modifiedDate);
                return this;
            }

            public Builder reportStatus(ReportStatus reportStatus) {
                this.builderReportStatus = reportStatus;
                return this;
            }

            public DbCandidate build() {
                return new DbCandidate(builderPublicationId, builderPublicationBucketUri, builderApplicable,
                                       builderInstanceType, builderChannelType, builderChannelId, builderLevel,
                                       builderPublicationDate, builderInternationalCollaboration,
                                       builderCollaborationFactor,
                                       builderCreatorCount, builderCreatorShareCount, builderCreators,
                                       builderBasePoints,
                                       builderPoints, builderTotalPoints, builderCreatedDate, builderModifiedDate,
                                       builderReportStatus);
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
