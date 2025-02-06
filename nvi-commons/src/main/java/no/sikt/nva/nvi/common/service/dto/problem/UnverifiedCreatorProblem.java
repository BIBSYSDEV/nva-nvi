package no.sikt.nva.nvi.common.service.dto.problem;

public record UnverifiedCreatorProblem(String title, String scope, String detail)
    implements CandidateProblem {
  private static final String DEFAULT_TITLE = "Unverified contributor exists";
  private static final String DEFAULT_DESCRIPTION =
      """
At least one of the contributors associated with this publication is unverified. \
Organizations affiliated with this contributor cannot approve or reject the publication as an NVI candidate, \
or receive NVI points for it, until the contributor is verified or removed from the publication.\
""";

  public UnverifiedCreatorProblem() {
    this(DEFAULT_TITLE, GLOBAL_SCOPE, DEFAULT_DESCRIPTION);
  }
}
