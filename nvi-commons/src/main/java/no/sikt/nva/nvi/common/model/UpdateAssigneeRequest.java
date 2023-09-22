package no.sikt.nva.nvi.common.model;

import no.sikt.nva.nvi.common.db.model.DbUsername;

public record UpdateAssigneeRequest(DbUsername username) implements UpdateApprovalRequest {
}
