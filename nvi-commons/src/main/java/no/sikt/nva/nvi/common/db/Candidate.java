package no.sikt.nva.nvi.common.db;

import java.util.List;
import java.util.UUID;
import no.sikt.nva.nvi.common.db.model.DbApprovalStatus;
import no.sikt.nva.nvi.common.db.model.DbCandidate;
import no.sikt.nva.nvi.common.db.model.DbNote;

public record Candidate(
    UUID identifier,
    UUID version,
    DbCandidate candidate,
    List<DbApprovalStatus> approvalStatuses,
    List<DbNote> notes
) {

    public static final class Builder {

        private UUID identifier;
        private DbCandidate candidate;
        private List<DbApprovalStatus> approvalStatuses;
        private List<DbNote> notes;
        private UUID version;

        public Builder() {
        }

        public Builder withIdentifier(UUID identifier) {
            this.identifier = identifier;
            return this;
        }

        public Builder withVersion(UUID version) {
            this.version = version;
            return this;
        }

        public Builder withCandidate(DbCandidate candidate) {
            this.candidate = candidate;
            return this;
        }

        public Builder withApprovalStatuses(List<DbApprovalStatus> approvalStatuses) {
            this.approvalStatuses = approvalStatuses;
            return this;
        }

        public Builder withNotes(List<DbNote> notes) {
            this.notes = notes;
            return this;
        }

        public Candidate build() {
            return new Candidate(identifier, version, candidate, approvalStatuses, notes);
        }
    }
}
