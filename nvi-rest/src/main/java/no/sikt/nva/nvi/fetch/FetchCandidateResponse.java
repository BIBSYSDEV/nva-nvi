package no.sikt.nva.nvi.fetch;

import com.fasterxml.jackson.annotation.JsonAutoDetect;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import java.math.BigDecimal;
import java.net.URI;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import no.sikt.nva.nvi.common.model.CandidateWithIdentifier;
import no.sikt.nva.nvi.common.model.business.Candidate;

@JsonAutoDetect(fieldVisibility = JsonAutoDetect.Visibility.ANY)
@JsonSerialize
public record FetchCandidateResponse(UUID id,
                                     URI publicationId,
                                     List<ApprovalStatus> approvalStatuses,
                                     Map<URI, BigDecimal> points,
                                     List<Note> notes) {

    public static FetchCandidateResponse fromCandidate(CandidateWithIdentifier candidate) {
        return new FetchCandidateResponse(
            candidate.identifier(),
            candidate.candidate().publicationId(),
            mapToApprovalStatus(candidate.candidate()),
            candidate.candidate().points(),
            mapToNotes(candidate.candidate())
        );
    }

    private static List<Note> mapToNotes(Candidate candidate) {
        return candidate.notes()
                   .stream()
                   .map(FetchCandidateResponse::mapToNote)
                   .toList();
    }

    private static Note mapToNote(no.sikt.nva.nvi.common.model.business.Note note) {
        return new Note(note.user(), note.text(), note.createdDate());
    }

    private static List<ApprovalStatus> mapToApprovalStatus(Candidate candidate) {
        return candidate.approvalStatuses()
                   .stream()
                   .map(FetchCandidateResponse::mapToApprovalStatus)
                   .toList();
    }

    private static ApprovalStatus mapToApprovalStatus(
        no.sikt.nva.nvi.common.model.business.ApprovalStatus approvalStatus) {
        return new ApprovalStatus(approvalStatus.institutionId(), approvalStatus.status(), approvalStatus.finalizedBy(),
                                  approvalStatus.finalizedDate());
    }
}
