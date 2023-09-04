package no.sikt.nva.nvi;

import com.fasterxml.jackson.annotation.JsonAutoDetect;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import java.math.BigDecimal;
import java.net.URI;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import no.sikt.nva.nvi.common.model.CandidateWithIdentifier;
import no.sikt.nva.nvi.common.model.business.Candidate;
import no.sikt.nva.nvi.fetch.ApprovalStatus;
import no.sikt.nva.nvi.fetch.Note;

@JsonAutoDetect(fieldVisibility = JsonAutoDetect.Visibility.ANY)
@JsonSerialize
public record CandidateResponse(UUID id,
                                URI publicationId,
                                List<ApprovalStatus> approvalStatuses,
                                Map<URI, BigDecimal> points,
                                List<Note> notes) {

    public static CandidateResponse fromCandidate(CandidateWithIdentifier candidate) {
        return CandidateResponse.builder()
                   .withId(candidate.identifier())
                   .withPublicationId(candidate.candidate().publicationId())
                   .withApprovalStatuses(mapToApprovalStatus(candidate.candidate()))
                   .withPoints(candidate.candidate().points())
                   .withNotes(mapToNotes(candidate.candidate()))
                   .build();
    }

    public static Builder builder() {
        return new Builder();
    }

    private static List<Note> mapToNotes(Candidate candidate) {
        var notes = candidate.notes();
        return notes == null
                   ? Collections.emptyList()
                   : notes.stream()
                         .map(CandidateResponse::mapToNote)
                         .toList();
    }

    private static Note mapToNote(no.sikt.nva.nvi.common.model.business.Note note) {
        return new Note(note.user(), note.text(), note.createdDate());
    }

    private static List<ApprovalStatus> mapToApprovalStatus(Candidate candidate) {
        return candidate.approvalStatuses()
                   .stream()
                   .map(CandidateResponse::mapToApprovalStatus)
                   .toList();
    }

    private static ApprovalStatus mapToApprovalStatus(
        no.sikt.nva.nvi.common.model.business.ApprovalStatus approvalStatus) {
        return new ApprovalStatus(approvalStatus.institutionId(), approvalStatus.status(), approvalStatus.finalizedBy(),
                                  approvalStatus.finalizedDate());
    }

    public static final class Builder {

        private UUID id;
        private URI publicationId;
        private List<ApprovalStatus> approvalStatuses = new ArrayList<>();
        private Map<URI, BigDecimal> points = new HashMap<>();
        private List<Note> notes = new ArrayList<>();

        private Builder() {
        }

        public Builder withId(UUID id) {
            this.id = id;
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

        public Builder withPoints(Map<URI, BigDecimal> points) {
            this.points = points;
            return this;
        }

        public Builder withNotes(List<Note> notes) {
            this.notes = notes;
            return this;
        }

        public CandidateResponse build() {
            return new CandidateResponse(id, publicationId, approvalStatuses, points, notes);
        }
    }
}
