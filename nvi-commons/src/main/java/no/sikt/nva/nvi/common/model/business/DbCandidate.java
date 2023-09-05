package no.sikt.nva.nvi.common.model.business;

import java.net.URI;
import java.util.List;
import software.amazon.awssdk.enhanced.dynamodb.mapper.annotations.DynamoDbImmutable;

@DynamoDbImmutable(builder = DbCandidate.Builder.class)
public record DbCandidate(
    URI publicationId,
    URI publicationBucketUri,
    boolean isApplicable,
    String instanceType,
    Level level,
    PublicationDate publicationDate,
    boolean isInternationalCollaboration,
    int creatorCount,
    List<Creator> creators,
    List<InstitutionPoints> points
) {

    private DbCandidate(Builder b) {
        this(b.publicationId, b.publicationBucketUri, b.isApplicable, b.instanceType, b.level, b.publicationDate,
             b.isInternationalCollaboration, b.creatorCount, b.creators, b.points);
    }

    public static Builder builder() {
        return new Builder();
    }

    public Builder copy() {
        return builder()
                   .withPublicationId(publicationId)
                   .withPublicationBucketUri(publicationBucketUri)
                   .withIsApplicable(isApplicable)
                   .withInstanceType(instanceType)
                   .withLevel(level)
                   .withPublicationDate(publicationDate)
                   .withIsInternationalCollaboration(isInternationalCollaboration)
                   .withCreatorCount(creatorCount)
                   .withCreators(creators)
                   .withPoints(points)
            ;
    }

    public static final class Builder {

        private URI publicationId;
        private URI publicationBucketUri;
        private boolean isApplicable;
        private String instanceType;
        private Level level;
        private PublicationDate publicationDate;
        private boolean isInternationalCollaboration;
        private int creatorCount;
        private List<Creator> creators;
        private List<InstitutionPoints> points;

        private Builder() {
        }

        public Builder withPublicationId(URI publicationId) {
            this.publicationId = publicationId;
            return this;
        }

        public Builder withPublicationBucketUri(URI publicationBucketUri) {
            this.publicationBucketUri = publicationBucketUri;
            return this;
        }

        public Builder withIsApplicable(boolean isApplicable) {
            this.isApplicable = isApplicable;
            return this;
        }

        public Builder withInstanceType(String instanceType) {
            this.instanceType = instanceType;
            return this;
        }

        public Builder withLevel(Level level) {
            this.level = level;
            return this;
        }

        public Builder withPublicationDate(PublicationDate publicationDate) {
            this.publicationDate = publicationDate;
            return this;
        }

        public Builder withIsInternationalCollaboration(boolean isInternationalCollaboration) {
            this.isInternationalCollaboration = isInternationalCollaboration;
            return this;
        }

        public Builder withCreatorCount(int creatorCount) {
            this.creatorCount = creatorCount;
            return this;
        }

        public Builder withCreators(List<Creator> creators) {
            this.creators = creators;
            return this;
        }

        public Builder withPoints(List<InstitutionPoints> points) {
            this.points = points;
            return this;
        }

        public DbCandidate build() {
            return new DbCandidate(publicationId, publicationBucketUri, isApplicable, instanceType, level,
                                   publicationDate, isInternationalCollaboration, creatorCount, creators, points
            );
        }
    }
}
