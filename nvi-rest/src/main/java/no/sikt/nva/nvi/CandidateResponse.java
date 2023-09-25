package no.sikt.nva.nvi;

import com.fasterxml.jackson.annotation.JsonAutoDetect;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import java.net.URI;
import java.util.ArrayList;
import java.util.List;
import no.sikt.nva.nvi.rest.fetch.ApprovalStatus;
import no.sikt.nva.nvi.rest.fetch.Note;

@JsonAutoDetect(fieldVisibility = JsonAutoDetect.Visibility.ANY)
@JsonSerialize
public record CandidateResponse(URI identifier,
                                URI publicationId,
                                List<ApprovalStatus> approvalStatuses,
                                List<Note> notes) {

    public static Builder builder() {
        return new Builder();
    }

    public static final class Builder {

        private URI identifier;
        private URI publicationId;
        private List<ApprovalStatus> approvalStatuses = new ArrayList<>();
        private List<Note> notes = new ArrayList<>();

        private Builder() {
        }

        public Builder withIdentifier(URI identifier) {
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

        public CandidateResponse build() {
            return new CandidateResponse(identifier, publicationId, approvalStatuses, notes);
        }
    }
}
