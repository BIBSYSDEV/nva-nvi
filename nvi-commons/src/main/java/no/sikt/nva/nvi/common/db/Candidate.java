package no.sikt.nva.nvi.common.db;

import java.util.List;
import java.util.UUID;
import no.sikt.nva.nvi.common.db.model.DbApprovalStatus;
import no.sikt.nva.nvi.common.db.model.DbCandidate;
import no.sikt.nva.nvi.common.db.model.DbNote;
import no.sikt.nva.nvi.common.model.PeriodStatus;

public record Candidate(
    UUID identifier,
    DbCandidate candidate,
    List<DbApprovalStatus> approvalStatuses,
    List<DbNote> notes,
    PeriodStatus periodStatus
) {

    public Builder copy() {
        return new Builder()
                   .withNotes(notes)
                   .withCandidate(candidate)
                   .withIdentifier(identifier)
                   .withApprovalStatuses(approvalStatuses)
                   .withReportStatus(periodStatus);
    }

    public static final class Builder {

        private UUID identifier;
        private DbCandidate candidate;
        private List<DbApprovalStatus> approvalStatuses;
        private List<DbNote> notes;
        private PeriodStatus periodStatus;

        public Builder() {
        }

        public Builder withIdentifier(UUID identifier) {
            this.identifier = identifier;
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

        public Builder withReportStatus(PeriodStatus periodStatus) {
            this.periodStatus = periodStatus;
            return this;
        }

        public Candidate build() {
            return new Candidate(identifier, candidate, approvalStatuses, notes, periodStatus);
        }
    }
}
