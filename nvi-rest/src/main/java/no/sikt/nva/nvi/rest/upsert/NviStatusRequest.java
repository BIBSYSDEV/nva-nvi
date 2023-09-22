package no.sikt.nva.nvi.rest.upsert;

import java.net.URI;
import java.util.UUID;
import no.sikt.nva.nvi.common.db.model.DbStatus;
import no.sikt.nva.nvi.common.model.UpdateStatusRequest;

public record NviStatusRequest(UUID candidateId,
                               URI institutionId,
                               NviApprovalStatus status) {

    public UpdateStatusRequest toUpdateRequest(String username) {
        return new UpdateStatusRequest(DbStatus.parse(status.getValue()), username);
    }
}
