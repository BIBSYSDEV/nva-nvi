package no.sikt.nva.nvi.common.service.dto.problem;

public record UnverifiedCreatorProblem(String title, String scope, String detail)
    implements CandidateProblem {
  private static final String DEFAULT_TITLE = "Unverified contributor exists";
  private static final String DEFAULT_DESCRIPTION =
      "At least one of the contributors on this publication is unverified "
          + "and affiliated with an institution that should report to NVI.";

  public UnverifiedCreatorProblem() {
    this(DEFAULT_TITLE, GLOBAL_SCOPE, DEFAULT_DESCRIPTION);
  }
}
