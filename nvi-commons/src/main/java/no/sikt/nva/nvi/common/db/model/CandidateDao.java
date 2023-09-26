package no.sikt.nva.nvi.common.db.model;

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
import java.util.Arrays;
import java.util.List;
import java.util.UUID;
import no.sikt.nva.nvi.common.db.DynamoEntryWithRangeKey;
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
    @DynamoDbAttribute(DATA_FIELD) CandidateData candidate
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

    public enum ChannelLevel {
        UNASSIGNED("Unassigned"), LEVEL_ONE("1"), LEVEL_TWO("2"), NON_CANDIDATE("NonCandidateLevel");

        @JsonValue
        private final String value;

        ChannelLevel(String value) {

            this.value = value;
        }

        @JsonCreator
        public static ChannelLevel parse(String value) {
            return Arrays
                       .stream(ChannelLevel.values())
                       .filter(level -> level.getValue().equalsIgnoreCase(value))
                       .findFirst()
                       .orElse(NON_CANDIDATE);
        }

        public String getValue() {
            return value;
        }
    }

    public enum InstanceType {

        ACADEMIC_MONOGRAPH("AcademicMonograph"), ACADEMIC_CHAPTER("AcademicChapter"),
        ACADEMIC_ARTICLE("AcademicArticle"), ACADEMIC_LITERATURE_REVIEW("AcademicLiteratureReview"),
        NON_CANDIDATE("NonCandidateInstanceType");

        private final String value;

        InstanceType(String value) {
            this.value = value;
        }

        @JacocoGenerated
        public static InstanceType parse(String value) {
            return Arrays.stream(InstanceType.values())
                       .filter(type -> type.getValue().equalsIgnoreCase(value))
                       .findFirst()
                       .orElse(NON_CANDIDATE);
        }

        @JsonCreator
        @JacocoGenerated
        public String getValue() {
            return value;
        }
    }

    public static final class Builder {

        private UUID builderIdentifier;
        private CandidateData builderCandidate;

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

        public Builder candidate(CandidateData candidate) {
            this.builderCandidate = candidate;
            return this;
        }

        public CandidateDao build() {
            return new CandidateDao(builderIdentifier, builderCandidate);
        }
    }

    @DynamoDbImmutable(builder = CandidateData.Builder.class)
    public record CandidateData(
        URI publicationId,
        URI publicationBucketUri,
        boolean applicable,
        InstanceType instanceType,
        ChannelLevel level,
        PublicationDate publicationDate,
        boolean internationalCollaboration,
        int creatorCount,
        List<Creator> creators,
        List<InstitutionPoints> points
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
                       .level(level)
                       .publicationDate(publicationDate)
                       .internationalCollaboration(internationalCollaboration)
                       .creatorCount(creatorCount)
                       .creators(creators)
                       .points(points);
        }

        public static final class Builder {

            private URI builderPublicationId;
            private URI builderPublicationBucketUri;
            private boolean builderApplicable;
            private InstanceType builderInstanceType;
            private ChannelLevel builderLevel;
            private PublicationDate builderPublicationDate;
            private boolean builderInternationalCollaboration;
            private int builderCreatorCount;
            private List<Creator> builderCreators;
            private List<InstitutionPoints> builderPoints;

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

            public Builder level(ChannelLevel level) {
                this.builderLevel = level;
                return this;
            }

            public Builder publicationDate(PublicationDate publicationDate) {
                this.builderPublicationDate = publicationDate;
                return this;
            }

            public Builder internationalCollaboration(boolean internationalCollaboration) {
                this.builderInternationalCollaboration = internationalCollaboration;
                return this;
            }

            public Builder creatorCount(int creatorCount) {
                this.builderCreatorCount = creatorCount;
                return this;
            }

            public Builder creators(List<Creator> creators) {
                this.builderCreators = creators;
                return this;
            }

            public Builder points(List<InstitutionPoints> points) {
                this.builderPoints = points;
                return this;
            }

            public CandidateData build() {
                return new CandidateData(builderPublicationId, builderPublicationBucketUri, builderApplicable,
                                         builderInstanceType, builderLevel,
                                         builderPublicationDate, builderInternationalCollaboration, builderCreatorCount,
                                         builderCreators, builderPoints);
            }
        }
    }

    @DynamoDbImmutable(builder = PublicationDate.Builder.class)
    public static record PublicationDate(String year, String month, String day) {

        public static Builder builder() {
            return new Builder();
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

            public PublicationDate build() {
                return new PublicationDate(builderYear, builderMonth, builderDay);
            }
        }
    }

    @DynamoDbImmutable(builder = Creator.Builder.class)
    public static record Creator(URI creatorId, List<URI> affiliations) {

        public static Builder builder() {
            return new Builder();
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

            public Creator build() {
                return new Creator(builderCreatorId, builderAffiliations);
            }
        }
    }

    @DynamoDbImmutable(builder = InstitutionPoints.Builder.class)
    public static record InstitutionPoints(URI institutionId, BigDecimal points) {

        public static Builder builder() {
            return new Builder();
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

            public InstitutionPoints build() {
                return new InstitutionPoints(builderInstitutionId, builderPoints);
            }
        }
    }
}
