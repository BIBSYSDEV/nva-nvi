package no.sikt.nva.nvi.common.service.requests;

import no.sikt.nva.nvi.common.service.model.Username;

public interface CreatePeriodRequest extends UpsertPeriodRequest {
  Username createdBy();
}
