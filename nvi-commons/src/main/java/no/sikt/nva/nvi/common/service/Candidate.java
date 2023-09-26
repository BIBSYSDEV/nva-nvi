package no.sikt.nva.nvi.common.service;

import java.time.Instant;
import java.util.List;
import java.util.UUID;
import no.sikt.nva.nvi.common.db.model.ApprovalStatusDao.ApprovalStatusData;
import no.sikt.nva.nvi.common.db.model.CandidateDao.CandidateData;
import no.sikt.nva.nvi.common.db.model.NoteDao.NoteData;
import no.sikt.nva.nvi.common.db.model.PeriodDao.PeriodData;

public record Candidate(UUID identifier, CandidateData candidate, List<ApprovalStatusData> approvalStatuses,
                        List<NoteData> notes, PeriodStatus periodStatus) {

    public static final String PERIOD_CLOSED_MESSAGE = "Period is closed, perform actions on candidate is forbidden!";

    public void isEditableForPeriod(PeriodData period) {
        if (period.reportingDate().isBefore(Instant.now())) {
            throw new IllegalStateException(PERIOD_CLOSED_MESSAGE);
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
        private CandidateData candidate;
        private List<ApprovalStatusData> approvalStatuses;
        private List<NoteData> notes;
        private PeriodStatus periodStatus;

        public Builder() {
        }

        public Builder withIdentifier(UUID identifier) {
            this.identifier = identifier;
            return this;
        }

        public Builder withCandidate(CandidateData candidate) {
            this.candidate = candidate;
            return this;
        }

        public Builder withApprovalStatuses(List<ApprovalStatusData> approvalStatuses) {
            this.approvalStatuses = approvalStatuses;
            return this;
        }

        public Builder withNotes(List<NoteData> notes) {
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
