package no.sikt.nva.nvi.common.service.exception;

public class IllegalCandidateUpdateException extends RuntimeException {
  public static final String CANDIDATE_IS_REPORTED = "Cannot update reported candidate";
  public static final String PERIOD_IS_NOT_OPEN = "Target period is not open";
  public static final String NO_APPROVAL_FOUND = "No approval found matching request";

  public IllegalCandidateUpdateException(String message) {
    super(message);
  }
}
