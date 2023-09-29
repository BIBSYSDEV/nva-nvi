package no.sikt.nva.nvi.common.service;

import java.time.Instant;
import java.util.List;
import java.util.UUID;
import no.sikt.nva.nvi.common.db.ApprovalStatusDao.DbApprovalStatus;
import no.sikt.nva.nvi.common.db.PeriodStatus;
import no.sikt.nva.nvi.common.db.CandidateDao.DbCandidate;
import no.sikt.nva.nvi.common.db.NoteDao.DbNote;
import no.sikt.nva.nvi.common.db.NviPeriodDao.DbNviPeriod;

public record Candidate(UUID identifier, DbCandidate candidate, List<DbApprovalStatus> approvalStatuses,
                        List<DbNote> notes, PeriodStatus periodStatus) {

    public static final String PERIOD_CLOSED_MESSAGE = "Period is closed, perform actions on candidate is forbidden!";
    public static final String PERIOD_NOT_OPENED_MESSAGE = "Period is not opened yet, perform actions on candidate is"
                                                           + " forbidden!";

    public void isEditableForPeriod(DbNviPeriod period) {
        if (period.reportingDate().isBefore(Instant.now())) {
            throw new IllegalStateException(PERIOD_CLOSED_MESSAGE);
        }
        if (period.startDate().isAfter(Instant.now())) {
            throw new IllegalStateException(PERIOD_NOT_OPENED_MESSAGE);
        }
    }

    public Builder copy() {
        return new Builder().withIdentifier(identifier)
                   .withApprovalStatuses(approvalStatuses)
                   .withNotes(notes)
                   .withCandidate(candidate)
                   .withPeriodStatus(periodStatus);
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

        public Builder withPeriodStatus(PeriodStatus periodStatus) {
            this.periodStatus = periodStatus;
            return this;
        }

        public Candidate build() {
            return new Candidate(identifier, candidate, approvalStatuses, notes, periodStatus);
        }
    }
}
