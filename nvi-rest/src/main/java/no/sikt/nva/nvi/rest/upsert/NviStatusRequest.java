package no.sikt.nva.nvi.rest.upsert;

import static java.util.Objects.nonNull;
import java.net.URI;
import java.util.UUID;
import no.sikt.nva.nvi.common.db.ApprovalStatusDao.DbStatus;
import no.sikt.nva.nvi.common.service.requests.UpdateStatusRequest;

public record NviStatusRequest(UUID candidateId,
                               URI institutionId,
                               NviApprovalStatus status,
                               String reason) {

    public UpdateStatusRequest toUpdateRequest(String username) {
        return nonNull(reason)
                   ? UpdateStatusRequest.builder()
                         .withInstitutionId(institutionId)
                         .withApprovalStatus(DbStatus.parse(status.getValue()))
                         .withUsername(username)
                         .withReason(reason)
                         .build()
                   : UpdateStatusRequest.builder()
                         .withInstitutionId(institutionId)
                         .withApprovalStatus(DbStatus.parse(status.getValue()))
                         .withUsername(username)
                         .build();
    }
}
