package no.sikt.nva.nvi.common.permissions.grant;

import no.sikt.nva.nvi.common.service.dto.CandidateOperation;

@FunctionalInterface
public interface GrantStrategy {
  boolean allowsAction(CandidateOperation operation);
}
