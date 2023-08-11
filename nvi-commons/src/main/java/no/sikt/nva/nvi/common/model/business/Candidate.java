package no.sikt.nva.nvi.common.model.business;

import com.fasterxml.jackson.annotation.JsonAutoDetect;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import java.net.URI;
import java.util.List;

@JsonAutoDetect(fieldVisibility = JsonAutoDetect.Visibility.ANY)
@JsonSerialize
public record Candidate(URI publicationId,
                        Period period,
                        boolean isApplicable,
                        String instanceType,
                        Level level,
                        String publicationDate,
                        boolean isInternationalCollaboration,
                        int creatorCount,
                        List<VerifiedCreator> creators,
                        List<ApprovalStatus> approvalStatuses,
                        List<Note> notes) {

    public static final class Builder {

        private URI publicationId;
        private Period period;
        private boolean isApplicable;
        private String instanceType;
        private Level level;
        private String publicationDate;
        private boolean isInternationalCollaboration;
        private int creatorCount;
        private List<VerifiedCreator> creators;
        private List<ApprovalStatus> approvalStatuses;
        private List<Note> notes;

        public Builder() {
        }

        public Builder withPublicationId(URI publicationId) {
            this.publicationId = publicationId;
            return this;
        }

        public Builder withPeriod(Period period) {
            this.period = period;
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

        public Builder withPublicationDate(String publicationDate) {
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

        public Builder withCreators(List<VerifiedCreator> creators) {
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
            return new Candidate(publicationId, period, isApplicable, instanceType, level, publicationDate,
                                 isInternationalCollaboration, creatorCount, creators, approvalStatuses, notes);
        }
    }
}
