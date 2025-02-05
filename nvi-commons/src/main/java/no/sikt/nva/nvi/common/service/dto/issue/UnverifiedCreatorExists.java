package no.sikt.nva.nvi.common.service.dto.issue;

public record UnverifiedCreatorExists(String title, String scope, String description)
    implements CandidateIssue {
  private static String DEFAULT_TITLE = "Unverified contributor exists";

  public UnverifiedCreatorExists(String description) {
    this(DEFAULT_TITLE, GLOBAL_SCOPE, description);
  }
}
