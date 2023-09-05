package no.sikt.nva.nvi.common.model.business;

import java.net.URI;
import java.util.List;
import software.amazon.awssdk.enhanced.dynamodb.mapper.annotations.DynamoDbIgnore;
import software.amazon.awssdk.enhanced.dynamodb.mapper.annotations.DynamoDbImmutable;

@DynamoDbImmutable(builder = DbCandidate.Builder.class)
public record DbCandidate(
    URI publicationId,
    URI publicationBucketUri,
    boolean applicable,
    String instanceType,
    DbLevel level,
    DbPublicationDate publicationDate,
    boolean internationalCollaboration,
    int creatorCount,
    List<DbCreator> creators,
    List<DbInstitutionPoints> points
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
                   .points(points)
            ;
    }

    public static final class Builder {

        private URI publicationId;
        private URI publicationBucketUri;
        private boolean applicable;
        private String instanceType;
        private DbLevel level;
        private DbPublicationDate publicationDate;
        private boolean internationalCollaboration;
        private int creatorCount;
        private List<DbCreator> creators;
        private List<DbInstitutionPoints> points;

        private Builder() {
        }

        public Builder publicationId(URI publicationId) {
            this.publicationId = publicationId;
            return this;
        }

        public Builder publicationBucketUri(URI publicationBucketUri) {
            this.publicationBucketUri = publicationBucketUri;
            return this;
        }

        public Builder applicable(boolean applicable) {
            this.applicable = applicable;
            return this;
        }

        public Builder instanceType(String instanceType) {
            this.instanceType = instanceType;
            return this;
        }

        public Builder level(DbLevel level) {
            this.level = level;
            return this;
        }

        public Builder publicationDate(DbPublicationDate publicationDate) {
            this.publicationDate = publicationDate;
            return this;
        }

        public Builder internationalCollaboration(boolean internationalCollaboration) {
            this.internationalCollaboration = internationalCollaboration;
            return this;
        }

        public Builder creatorCount(int creatorCount) {
            this.creatorCount = creatorCount;
            return this;
        }

        public Builder creators(List<DbCreator> creators) {
            this.creators = creators;
            return this;
        }

        public Builder points(List<DbInstitutionPoints> points) {
            this.points = points;
            return this;
        }

        public DbCandidate build() {
            return new DbCandidate(publicationId, publicationBucketUri, applicable, instanceType, level,
                                   publicationDate, internationalCollaboration, creatorCount, creators, points);
        }
    }
}
