package no.sikt.nva.nvi.rest.fetch;

import com.fasterxml.jackson.annotation.JsonAutoDetect;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import java.net.URI;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
import no.sikt.nva.nvi.common.model.CandidateWithIdentifier;
import no.sikt.nva.nvi.common.model.business.Candidate;

@JsonAutoDetect(fieldVisibility = JsonAutoDetect.Visibility.ANY)
@JsonSerialize
public record FetchCandidateResponse(UUID id,
                                     URI publicationId,
                                     List<ApprovalStatus> approvalStatuses,
                                     List<InstitutionPoints> points,
                                     List<Note> notes) {

    public static FetchCandidateResponse fromCandidate(CandidateWithIdentifier candidateWithIdentifier) {
        var candidate = candidateWithIdentifier.candidate();
        return new FetchCandidateResponse(candidateWithIdentifier.identifier(),
                                          candidate.publicationId(),
                                          mapToApprovalStatus(candidate),
                                          mapToInstitutionPoints(candidate),
                                          mapToNotes(candidate)
        );
    }

    private static List<InstitutionPoints> mapToInstitutionPoints(Candidate candidate) {
        return candidate.points()
                   .stream()
                   .map(FetchCandidateResponse::mapToInstitutionPoint)
                   .toList();
    }

    private static List<Note> mapToNotes(Candidate candidate) {
        return Objects.nonNull(candidate.notes())
                   ? candidate.notes()
                         .stream()
                         .map(FetchCandidateResponse::mapToNote)
                         .toList()
                   : Collections.emptyList();
    }

    private static Note mapToNote(no.sikt.nva.nvi.common.model.business.Note note) {
        return new Note(note.user(), note.text(), note.createdDate());
    }

    private static InstitutionPoints mapToInstitutionPoint(
        no.sikt.nva.nvi.common.model.business.InstitutionPoints institutionPoints) {
        return new InstitutionPoints(institutionPoints.institutionId(),
                                     institutionPoints.points());
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
