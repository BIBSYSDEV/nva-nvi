package no.sikt.nva.nvi.rest.upsert;

import static java.util.Objects.nonNull;
import java.net.URI;
import java.util.UUID;
import no.sikt.nva.nvi.common.model.UpdateStatusRequest;
import no.sikt.nva.nvi.common.service.model.ApprovalStatus;

public record NviStatusRequest(UUID candidateId,
                               URI institutionId,
                               ApprovalStatus status,
                               String reason) {

    public UpdateStatusRequest toUpdateRequest(String username) {
        return nonNull(reason)
                   ? UpdateStatusRequest.builder()
                         .withInstitutionId(institutionId)
                         .withApprovalStatus(status)
                         .withUsername(username)
                         .withReason(reason)
                         .build()
                   : UpdateStatusRequest.builder()
                         .withInstitutionId(institutionId)
                         .withApprovalStatus(status)
                         .withUsername(username)
                         .build();
    }
}
