package no.sikt.nva.nvi.index.model.report;

import java.math.BigDecimal;
import java.util.Map;
import no.sikt.nva.nvi.common.service.model.GlobalApprovalStatus;
import no.sikt.nva.nvi.index.model.document.ApprovalStatus;

public record OrganizationStatusAggregation(
    int candidateCount,
    BigDecimal pointsFromAffiliatedCreators,
    Map<GlobalApprovalStatus, Integer> globalApprovalStatus,
    Map<ApprovalStatus, Integer> approvalStatus) {


    public OrganizationStatusAggregation {
        // TODO: Add helper method to copy the two approval status maps and ensure we have values for all enums
        // TODO Add validation that candidateCount is greater than zero
        // TODO Add validation that pointsFromAffiliatedCreators is greater than zero
    }
}
