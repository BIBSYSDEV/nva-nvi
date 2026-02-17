package no.sikt.nva.nvi.index.report.response;

import com.fasterxml.jackson.annotation.JsonTypeInfo;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;

@JsonSerialize
@JsonTypeInfo(use = JsonTypeInfo.Id.NAME, property = "type")
public record InstitutionSummary(
    InstitutionTotals totals, UndisputedCandidatesByLocalApprovalStatus byLocalApprovalStatus) {}
