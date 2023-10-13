package no.sikt.nva.nvi.common.service.requests;

import java.net.URI;

public record UpdateAssigneeRequest(URI institutionId, String username) implements UpdateApprovalRequest {

}
