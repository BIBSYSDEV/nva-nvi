package no.sikt.nva.nvi.common.model;

import java.util.List;
import java.util.UUID;
import no.sikt.nva.nvi.common.model.business.DbApprovalStatus;
import no.sikt.nva.nvi.common.model.business.DbCandidate;

public record CandidateWithIdentifier(DbCandidate candidate, UUID identifier, List<DbApprovalStatus> approvalStatuses) {

}