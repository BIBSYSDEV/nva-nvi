package no.sikt.nva.nvi.rest.fetch;

import java.net.URI;
import java.util.Objects;
import no.sikt.nva.nvi.common.service.model.Candidate;
import no.sikt.nva.nvi.common.service.model.GlobalApprovalStatus;
import no.sikt.nva.nvi.common.service.model.NviPeriod;

public record ReportStatusDto(
    URI publicationId, StatusWithDescriptionDto reportStatus, String period) {

  public static ReportStatusDto fromCandidate(Candidate candidate) {
    var publishingYear =
        candidate.period().map(NviPeriod::publishingYear).map(Objects::toString).orElse(null);
    return new ReportStatusDto(
        candidate.getPublicationId(),
        StatusWithDescriptionDto.fromStatus(getStatus(candidate)),
        publishingYear);
  }

  public static Builder builder() {
    return new Builder();
  }

  private static StatusDto getStatus(Candidate candidate) {
    var isOpenPeriod = candidate.period().filter(NviPeriod::isOpen).isPresent();
    if (!candidate.isApplicable()) {
      return StatusDto.NOT_CANDIDATE;
    } else if (candidate.isReported()) {
      return StatusDto.REPORTED;
    } else if (candidate.isPendingReview()) {
      return StatusDto.PENDING_REVIEW;
    } else if (GlobalApprovalStatus.REJECTED.equals(candidate.getGlobalApprovalStatus())
        && isOpenPeriod) {
      return StatusDto.REJECTED;
    } else if (GlobalApprovalStatus.APPROVED.equals(candidate.getGlobalApprovalStatus())
        && isOpenPeriod) {
      return StatusDto.APPROVED;
    } else if (candidate.isUnderReview()) {
      return StatusDto.UNDER_REVIEW;
    } else if (candidate.isNotReportedInClosedPeriod()) {
      return StatusDto.NOT_REPORTED;
    } else {
      return StatusDto.UNKNOWN;
    }
  }

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

  public static final class Builder {

    private URI publicationId;
    private StatusWithDescriptionDto status;
    private String year;

    private Builder() {}

    public Builder withPublicationId(URI publicationId) {
      this.publicationId = publicationId;
      return this;
    }

    public Builder withStatus(StatusDto status) {
      this.status = StatusWithDescriptionDto.fromStatus(status);
      return this;
    }

    public Builder withYear(String year) {
      this.year = year;
      return this;
    }

    public ReportStatusDto build() {
      return new ReportStatusDto(publicationId, status, year);
    }
  }

  private record StatusWithDescriptionDto(String status, String description) {

    public static StatusWithDescriptionDto fromStatus(StatusDto status) {
      return new StatusWithDescriptionDto(status.toString(), status.getDescription());
    }
  }
}
