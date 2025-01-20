package no.sikt.nva.nvi.common.model;

import java.net.URI;
import no.sikt.nva.nvi.common.service.model.ApprovalStatus;

public record UpdateStatusRequest(
    URI institutionId, ApprovalStatus approvalStatus, String username, String reason)
    implements UpdateApprovalRequest {

  public static Builder builder() {
    return new Builder();
  }

  public static final class Builder {

    private URI institutionId;
    private ApprovalStatus approvalStatus;
    private String username;
    private String reason;

    private Builder() {}

    public Builder withInstitutionId(URI institutionId) {
      this.institutionId = institutionId;
      return this;
    }

    public Builder withApprovalStatus(ApprovalStatus approvalStatus) {
      this.approvalStatus = approvalStatus;
      return this;
    }

    public Builder withUsername(String username) {
      this.username = username;
      return this;
    }

    public Builder withReason(String reason) {
      this.reason = reason;
      return this;
    }

    public UpdateStatusRequest build() {
      return new UpdateStatusRequest(institutionId, approvalStatus, username, reason);
    }
  }
}
