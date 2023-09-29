package no.sikt.nva.nvi.common.model;

import java.net.URI;

public record UpdateAssigneeRequest(URI institutionId, String username) implements UpdateApprovalRequest {

}
