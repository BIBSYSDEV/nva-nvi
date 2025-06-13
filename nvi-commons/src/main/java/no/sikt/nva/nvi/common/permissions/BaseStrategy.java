package no.sikt.nva.nvi.common.permissions;

import static java.util.function.Predicate.not;
import static no.sikt.nva.nvi.common.model.NviCreator.isAffiliatedWithTopLevelOrganization;

import no.sikt.nva.nvi.common.model.NviCreator;
import no.sikt.nva.nvi.common.model.UserInstance;
import no.sikt.nva.nvi.common.service.model.Candidate;

public class BaseStrategy {
  protected final Candidate candidate;
  protected final UserInstance userInstance;

  public BaseStrategy(Candidate candidate, UserInstance userInstance) {
    this.candidate = candidate;
    this.userInstance = userInstance;
  }

  protected boolean hasUnverifiedCreatorFromUserOrganization() {
    return candidate.getPublicationDetails().nviCreators().stream()
        .filter(not(NviCreator::isVerified))
        .anyMatch(isAffiliatedWithTopLevelOrganization(userInstance.topLevelOrganizationId()));
  }
}
