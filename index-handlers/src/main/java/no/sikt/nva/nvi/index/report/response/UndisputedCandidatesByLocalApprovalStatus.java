package no.sikt.nva.nvi.index.report.response;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonTypeInfo;
import no.sikt.nva.nvi.index.model.document.ApprovalStatus;
import no.sikt.nva.nvi.index.report.model.LocalStatusSummary;

@JsonTypeInfo(use = JsonTypeInfo.Id.NAME, property = "type")
public record UndisputedCandidatesByLocalApprovalStatus(
    @JsonProperty("new") int newCount,
    @JsonProperty("pending") int pendingCount,
    @JsonProperty("approved") int approvedCount,
    @JsonProperty("rejected") int rejectedCount) {

  public static UndisputedCandidatesByLocalApprovalStatus from(LocalStatusSummary statusSummary) {
    return new UndisputedCandidatesByLocalApprovalStatus(
        statusSummary.forStatus(ApprovalStatus.NEW).candidateCount(),
        statusSummary.forStatus(ApprovalStatus.PENDING).candidateCount(),
        statusSummary.forStatus(ApprovalStatus.APPROVED).candidateCount(),
        statusSummary.forStatus(ApprovalStatus.REJECTED).candidateCount());
  }
}
