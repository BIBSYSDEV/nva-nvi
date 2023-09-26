package no.sikt.nva.nvi.rest.model;

import static java.util.Objects.nonNull;
import java.net.URI;
import no.sikt.nva.nvi.common.db.model.Username;
import no.sikt.nva.nvi.common.model.UpdateAssigneeRequest;

public record ApprovalDto(String assignee, URI institutionId) {

    public UpdateAssigneeRequest toUpdateRequest() {
        return nonNull(assignee)
                   ? new UpdateAssigneeRequest(Username.fromString(assignee))
                   : new UpdateAssigneeRequest(null);
    }
}
