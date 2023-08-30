package no.sikt.nva.nvi;

import com.fasterxml.jackson.annotation.JsonAutoDetect;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import java.net.URI;
import java.util.List;
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
                                List<Note> notes) {

    public static CandidateResponse fromCandidate(CandidateWithIdentifier candidate) {
        return new CandidateResponse(
            candidate.identifier(),
            candidate.candidate().publicationId(),
            mapToApprovalStatus(candidate.candidate()),
            mapToNotes(candidate.candidate())
        );
    }

    private static List<Note> mapToNotes(Candidate candidate) {
        return candidate.notes()
                   .stream()
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
}
