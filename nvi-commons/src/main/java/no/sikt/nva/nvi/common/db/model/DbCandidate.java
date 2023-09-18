package no.sikt.nva.nvi.common.db.model;

import java.net.URI;
import java.util.List;
import software.amazon.awssdk.enhanced.dynamodb.mapper.annotations.DynamoDbIgnore;
import software.amazon.awssdk.enhanced.dynamodb.mapper.annotations.DynamoDbImmutable;

@DynamoDbImmutable(builder = DbCandidate.Builder.class)
public record DbCandidate(
    URI publicationId,
    URI publicationBucketUri,
    boolean applicable,
    InstanceType instanceType,
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
                   .points(points);
    }

    public static final class Builder {

        private URI builderPublicationId;
        private URI builderPublicationBucketUri;
        private boolean builderApplicable;
        private InstanceType builderInstanceType;
        private DbLevel builderLevel;
        private DbPublicationDate builderPublicationDate;
        private boolean builderInternationalCollaboration;
        private int builderCreatorCount;
        private List<DbCreator> builderCreators;
        private List<DbInstitutionPoints> builderPoints;

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

        public Builder creatorCount(int creatorCount) {
            this.builderCreatorCount = creatorCount;
            return this;
        }

        public Builder creators(List<DbCreator> creators) {
            this.builderCreators = creators;
            return this;
        }

        public Builder points(List<DbInstitutionPoints> points) {
            this.builderPoints = points;
            return this;
        }

        public DbCandidate build() {
            return new DbCandidate(builderPublicationId, builderPublicationBucketUri, builderApplicable,
                                   builderInstanceType, builderLevel,
                                   builderPublicationDate, builderInternationalCollaboration, builderCreatorCount,
                                   builderCreators, builderPoints);
        }
    }
}
