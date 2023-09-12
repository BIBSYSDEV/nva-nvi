package no.sikt.nva.nvi.common.db;

import java.util.List;
import java.util.UUID;
import no.sikt.nva.nvi.common.db.model.DbApprovalStatus;
import no.sikt.nva.nvi.common.db.model.DbCandidate;
import no.sikt.nva.nvi.common.db.model.DbNote;

public record Candidate(
    UUID identifier,
    DbCandidate candidate,
    List<DbApprovalStatus> approvalStatuses,
    List<DbNote> notes
) {

}
