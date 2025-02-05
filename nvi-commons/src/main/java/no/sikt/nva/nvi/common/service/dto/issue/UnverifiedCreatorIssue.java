package no.sikt.nva.nvi.common.service.dto.issue;

public record UnverifiedCreatorIssue(String title, String scope, String description)
    implements CandidateIssue {
  private static final String DEFAULT_TITLE = "Unverified contributor exists";
  private static final String DEFAULT_DESCRIPTION =
      """
At least one of the contributors associated with this publication is unverified. \
Organizations affiliated with this contributor cannot approve/reject the publication as an NVI candidate, \
or receive NVI points for it, until the contributor is verified or removed from the publication.\
""";

  public UnverifiedCreatorIssue() {
    this(DEFAULT_TITLE, GLOBAL_SCOPE, DEFAULT_DESCRIPTION);
  }
}
