package no.sikt.nva.nvi.common.service.dto;

import com.fasterxml.jackson.annotation.JsonAutoDetect;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import java.net.URI;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@JsonAutoDetect(fieldVisibility = JsonAutoDetect.Visibility.ANY)
@JsonSerialize
public record CandidateDTO(
    URI id,
    UUID identifier,
    URI publicationId,
    List<ApprovalStatus> approvalStatuses,
    List<Note> notes,
    PeriodStatus periodStatus) {

    public static Builder builder() {
        return new Builder();
    }

    public static final class Builder {

        private URI id;
        private UUID identifier;
        private URI publicationId;
        private List<ApprovalStatus> approvalStatuses = new ArrayList<>();
        private List<Note> notes = new ArrayList<>();
        private PeriodStatus periodStatus;

        private Builder() {
        }

        public Builder withId(URI id) {
            this.id = id;
            return this;
        }

        public Builder withIdentifier(UUID identifier) {
            this.identifier = identifier;
            return this;
        }

        public Builder withPublicationId(URI publicationId) {
            this.publicationId = publicationId;
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

        public Builder withPeriodStatus(PeriodStatus periodStatus) {
            this.periodStatus = periodStatus;
            return this;
        }

        public CandidateDTO build() {
            return new CandidateDTO(id, identifier, publicationId, approvalStatuses, notes, periodStatus);
        }
    }
}
