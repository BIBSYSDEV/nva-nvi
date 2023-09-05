package no.sikt.nva.nvi.common.model.business;

import com.fasterxml.jackson.annotation.JsonAutoDetect;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import java.net.URI;
import java.util.List;
import java.util.Map;

@JsonAutoDetect(fieldVisibility = JsonAutoDetect.Visibility.ANY)
@JsonSerialize
public record Candidate(URI publicationId,
                        URI publicationBucketUri,
                        boolean isApplicable,
                        String instanceType,
                        Level level,
                        PublicationDate publicationDate,
                        boolean isInternationalCollaboration,
                        int creatorCount,
                        List<Creator> creators,
                        List<InstitutionPoints> points,
                        List<ApprovalStatus> approvalStatuses,
                        List<Note> notes) implements DynamoDbModel<Candidate> {

    public Candidate {
    }

    public static Builder builder() {
        return new Builder();
    }

    public Builder copy() {
        return new Builder()
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
                   .withApprovalStatuses(approvalStatuses)
                   .withNotes(notes);
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
        private List<ApprovalStatus> approvalStatuses;
        private List<Note> notes;

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

        public Builder withApprovalStatuses(List<ApprovalStatus> approvalStatuses) {
            this.approvalStatuses = approvalStatuses;
            return this;
        }

        public Builder withNotes(List<Note> notes) {
            this.notes = notes;
            return this;
        }

        public Candidate build() {
            return new Candidate(publicationId, publicationBucketUri, isApplicable, instanceType, level,
                                 publicationDate, isInternationalCollaboration, creatorCount, creators, points,
                                 approvalStatuses, notes);
        }
    }
}
