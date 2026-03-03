package no.sikt.nva.nvi.index.report.response;

import com.fasterxml.jackson.annotation.JsonTypeInfo;
import no.sikt.nva.nvi.common.service.model.GlobalApprovalStatus;
import no.sikt.nva.nvi.index.report.model.PeriodAggregationResult;

@JsonTypeInfo(use = JsonTypeInfo.Id.NAME, property = "type")
public record CandidatesByGlobalApprovalStatus(
    int dispute, int pending, int rejected, int approved) {

  public static CandidatesByGlobalApprovalStatus from(PeriodAggregationResult result) {
    return new CandidatesByGlobalApprovalStatus(
        result.countForStatus(GlobalApprovalStatus.DISPUTE),
        result.countForStatus(GlobalApprovalStatus.PENDING),
        result.countForStatus(GlobalApprovalStatus.REJECTED),
        result.countForStatus(GlobalApprovalStatus.APPROVED));
  }
}
