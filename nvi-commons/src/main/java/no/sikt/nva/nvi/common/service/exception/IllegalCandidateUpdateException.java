package no.sikt.nva.nvi.common.service.exception;

public class IllegalCandidateUpdateException extends RuntimeException {
  public static final String CANDIDATE_IS_REPORTED = "Cannot update reported candidate";
  public static final String CANNOT_MOVE_CANDIDATE_TO_CLOSED_PERIOD =
      "Cannot move candidate to closed period";
  public static final String PERIOD_IS_CLOSED = "Target period is closed";
  public static final String NO_APPROVAL_FOUND = "No approval found matching request";
  public static final String NO_NOTE_FOUND = "No note found matching request";

  public IllegalCandidateUpdateException(String message) {
    super(message);
  }
}
