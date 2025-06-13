package no.sikt.nva.nvi.common.permissions.grant;

import no.sikt.nva.nvi.common.model.UserInstance;
import no.sikt.nva.nvi.common.permissions.BaseStrategy;
import no.sikt.nva.nvi.common.service.dto.CandidateOperation;
import no.sikt.nva.nvi.common.service.model.Candidate;

public class NviAdminGrantStrategy extends BaseStrategy implements GrantStrategy {

  public NviAdminGrantStrategy(Candidate candidate, UserInstance userInstance) {
    super(candidate, userInstance);
  }

  @Override
  public boolean allowsAction(CandidateOperation operation) {
    // TODO: Placeholder class. Should include the actions for application administrators (NviAdmin)
    // when those are defined as "allowedOperations".
    return false;
  }
}
