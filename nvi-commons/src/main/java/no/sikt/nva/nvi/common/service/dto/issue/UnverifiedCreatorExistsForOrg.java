package no.sikt.nva.nvi.common.service.dto.issue;

import java.net.URI;

public record UnverifiedCreatorExistsForOrg(
    String title, String scope, String description, URI orgId) implements CandidateIssue {
  private static String DEFAULT_TITLE =
      "Unverified contributor affiliated with current organization";

  public UnverifiedCreatorExistsForOrg(String description, URI orgId) {
    this(DEFAULT_TITLE, ORGANIZATION_SCOPE, description, orgId);
  }
}
