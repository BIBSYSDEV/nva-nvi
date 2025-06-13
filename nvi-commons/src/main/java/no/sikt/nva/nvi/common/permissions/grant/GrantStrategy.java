package no.sikt.nva.nvi.common.permissions.grant;

import no.sikt.nva.nvi.common.service.dto.CandidateOperation;

public interface GrantStrategy {
  boolean allowsAction(CandidateOperation operation);
}
