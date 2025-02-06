package no.sikt.nva.nvi.common.service.dto.issue;

import java.util.Collection;
import java.util.Set;
import java.util.stream.Collectors;

public record UnverifiedCreatorFromOrganizationIssue(
    String title, String scope, String detail, Set<String> contributorNames)
    implements CandidateIssue {
  private static final String DEFAULT_TITLE = "Unverified contributor from this organization";
  private static final String DEFAULT_DESCRIPTION =
      """
At least one of the contributors from this user's organization. \
Organizations affiliated with this contributor cannot approve or reject the publication as an NVI candidate, \
or receive NVI points for it, until the contributor is verified or removed from the publication.\
""";

  public UnverifiedCreatorFromOrganizationIssue(Collection<String> contributorNames) {
    this(
        DEFAULT_TITLE,
        GLOBAL_SCOPE,
        DEFAULT_DESCRIPTION,
        contributorNames.stream().collect(Collectors.toSet()));
  }
}
