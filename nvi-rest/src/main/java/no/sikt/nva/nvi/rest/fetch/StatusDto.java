package no.sikt.nva.nvi.rest.fetch;

public enum StatusDto {
  PENDING_REVIEW("Pending review. Awaiting approval from all institutions"),
  UNDER_REVIEW("Under review. At least one institution has approved/rejected"),
  APPROVED("Approved by all involved institutions in open period"),
  REJECTED("Rejected by all involved institutions in open period"),
  REPORTED("Reported in closed period"),
  NOT_REPORTED("Not reported in closed period"),
  NOT_CANDIDATE("Not a candidate"),
  UNKNOWN("Unknown status");
  private final String description;

  StatusDto(String description) {
    this.description = description;
  }

  public String getDescription() {
    return description;
  }
}
