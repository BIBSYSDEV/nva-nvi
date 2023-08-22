package no.sikt.nva.nvi.common.model.business;

import com.fasterxml.jackson.annotation.JsonAutoDetect;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import java.net.URI;
import java.util.List;
import no.sikt.nva.nvi.common.db.dto.CandidateDb;
import nva.commons.core.JacocoGenerated;

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
                        List<ApprovalStatus> approvalStatuses,
                        List<Note> notes) {

    public CandidateDb toDb() {

        return new CandidateDb.Builder()
                   .withPublicationId(publicationId)
                   .withPublicationBucketUri(publicationBucketUri)
                   .withIsApplicable(isApplicable)
                   .withInstanceType(instanceType)
                   .withLevel(level == null ? null : String.valueOf(level))
                   .withPublicationDate(publicationDate == null ? null : publicationDate.toDb())
                   .withIsInternationalCollaboration(isInternationalCollaboration)
                   .withCreatorCount(creatorCount)
                   .withCreators(creators == null ? null : creators.stream().map(Creator::toDb).toList())
                   .withApprovalStatuses(approvalStatuses == null ? null :
                                                                             approvalStatuses.stream()
                                                                                 .map(ApprovalStatus::toDb)
                                                                                 .toList()
                   )
                   .withNotes(notes == null ? null : notes.stream().map(Note::toDb).toList())
                   .build();
    }

    public static Candidate fromDb(CandidateDb db) {
        return new Candidate.Builder().withPublicationId(db.getPublicationId())
                   .withPublicationBucketUri(db.getPublicationBucketUri())
                   .withIsApplicable(db.isApplicable())
                   .withInstanceType(db.getInstanceType())
                   .withLevel(db.getLevel() == null ? null : Level.valueOf(db.getLevel()))
                   .withPublicationDate(
                       db.getPublicationDate() == null ? null : PublicationDate.fromDb(db.getPublicationDate()))
                   .withIsInternationalCollaboration(db.isInternationalCollaboration())
                   .withCreatorCount(db.getCreatorCount())
                   .withCreators(
                       db.getCreators() == null ? null : db.getCreators().stream().map(Creator::fromDb).toList())
                   .withApprovalStatuses(db.getApprovalStatuses() == null ? null
                                             : db.getApprovalStatuses().stream().map(ApprovalStatus::fromDb).toList())
                   .withNotes(db.getNotes() == null ? null : db.getNotes().stream().map(Note::fromDb).toList())
                   .build();
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
        private List<ApprovalStatus> approvalStatuses;
        private List<Note> notes;

        public Builder() {
        }

        public Builder withPublicationId(URI publicationId) {
            this.publicationId = publicationId;
            return this;
        }

        @JacocoGenerated
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
                                 publicationDate, isInternationalCollaboration, creatorCount, creators,
                                 approvalStatuses, notes);
        }
    }
}
