package no.sikt.nva.nvi.fetch;

import com.fasterxml.jackson.annotation.JsonAutoDetect;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import java.net.URI;
import java.util.List;
import no.sikt.nva.nvi.common.model.business.Candidate;

@JsonAutoDetect(fieldVisibility = JsonAutoDetect.Visibility.ANY)
@JsonSerialize
public record FetchCandidateResponse(
    URI publicationId,
    List<ApprovalStatus> approvalStatuses,
    List<Note> notes
) {

    public static FetchCandidateResponse of(Candidate candidate) {
        return new FetchCandidateResponse(
            candidate.publicationId(),
            candidate.approvalStatuses()
                .stream()
                .map(as -> new ApprovalStatus(as.institutionId(), as.status(), as.finalizedBy(), as.finalizedDate()))
                .toList(),
            candidate.notes()
                .stream()
                .map(n -> new Note(n.user(), n.text(), n.createdDate()))
                .toList()
        );
    }
}
