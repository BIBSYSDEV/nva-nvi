package no.sikt.nva.nvi.index.model.report;

import com.fasterxml.jackson.annotation.JsonTypeInfo;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import java.math.BigDecimal;
import java.util.Map;
import no.sikt.nva.nvi.common.service.model.GlobalApprovalStatus;
import no.sikt.nva.nvi.index.model.document.ApprovalStatus;

/**
 * Aggregation based on direct creator affiliations to a specific organization. Points are
 * calculated only from creators directly affiliated with this organization.
 */
@JsonSerialize
@JsonTypeInfo(use = JsonTypeInfo.Id.NAME, property = "type")
public record DirectAffiliationAggregation(
    int candidateCount,
    BigDecimal points,
    Map<GlobalApprovalStatus, Integer> globalApprovalStatus,
    Map<ApprovalStatus, Integer> approvalStatus)
    implements OrganizationStatusAggregation {

  public DirectAffiliationAggregation {
    OrganizationStatusAggregation.validateCandidateCount(candidateCount);
    points = OrganizationStatusAggregation.validateAndAdjustPoints(points);
    globalApprovalStatus =
        OrganizationStatusAggregation.validateAndCopyStatusMap(
            GlobalApprovalStatus.class, globalApprovalStatus, "globalApprovalStatus");
    approvalStatus =
        OrganizationStatusAggregation.validateAndCopyStatusMap(
            ApprovalStatus.class, approvalStatus, "approvalStatus");
  }
}
