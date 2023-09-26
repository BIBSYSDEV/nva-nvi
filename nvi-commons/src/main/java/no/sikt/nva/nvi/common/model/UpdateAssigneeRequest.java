package no.sikt.nva.nvi.common.model;

import no.sikt.nva.nvi.common.db.model.Username;

public record UpdateAssigneeRequest(Username username) implements UpdateApprovalRequest {

}
