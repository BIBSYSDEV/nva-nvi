package no.sikt.nva.nvi.common.permissions.deny;

import no.sikt.nva.nvi.common.service.dto.CandidateOperation;

public interface DenyStrategy {
  boolean deniesAction(CandidateOperation operation);
}
