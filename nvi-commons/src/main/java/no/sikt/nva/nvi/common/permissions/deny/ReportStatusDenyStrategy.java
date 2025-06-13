package no.sikt.nva.nvi.common.permissions.deny;

import no.sikt.nva.nvi.common.model.UserInstance;
import no.sikt.nva.nvi.common.permissions.BaseStrategy;
import no.sikt.nva.nvi.common.service.dto.CandidateOperation;
import no.sikt.nva.nvi.common.service.model.Candidate;

public class ReportStatusDenyStrategy extends BaseStrategy implements DenyStrategy {
  private static final boolean DENY = true;
  private static final boolean PASS = false;

  public ReportStatusDenyStrategy(Candidate candidate, UserInstance userInstance) {
    super(candidate, userInstance);
  }

  @Override
  public boolean deniesAction(CandidateOperation operation) {
    if (candidate.isReported()) {
      return DENY;
    }
    return PASS;
  }
}
