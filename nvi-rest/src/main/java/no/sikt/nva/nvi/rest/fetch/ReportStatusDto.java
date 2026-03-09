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
        candidate.getPeriod().map(NviPeriod::publishingYear).map(Objects::toString).orElse(null);
    return new ReportStatusDto(
        candidate.getPublicationId(),
        StatusWithDescriptionDto.fromStatus(getStatus(candidate)),
        publishingYear);
  }

  public static Builder builder() {
    return new Builder();
  }

  private static StatusDto getStatus(Candidate candidate) {
    var isOpenPeriod = candidate.getPeriod().filter(NviPeriod::isOpen).isPresent();
    if (!candidate.isApplicable()) {
      return StatusDto.NOT_CANDIDATE;
    } else if (candidate.isReported()) {
      return StatusDto.REPORTED;
    } else if (candidate.isPendingReview()) {
      return StatusDto.PENDING_REVIEW;
    } else if (candidate.getGlobalApprovalStatus() == GlobalApprovalStatus.REJECTED
        && isOpenPeriod) {
      return StatusDto.REJECTED;
    } else if (candidate.getGlobalApprovalStatus() == GlobalApprovalStatus.APPROVED
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
}
