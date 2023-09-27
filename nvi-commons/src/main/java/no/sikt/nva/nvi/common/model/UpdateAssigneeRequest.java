package no.sikt.nva.nvi.common.model;

import java.net.URI;
import no.sikt.nva.nvi.common.db.model.Username;

public record UpdateAssigneeRequest(URI institutionId, Username username) implements UpdateApprovalRequest {

}
