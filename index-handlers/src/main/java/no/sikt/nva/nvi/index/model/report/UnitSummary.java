package no.sikt.nva.nvi.index.model.report;

import com.fasterxml.jackson.annotation.JsonTypeInfo;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import java.util.List;
import no.sikt.nva.nvi.common.client.model.Organization;

@JsonSerialize
@JsonTypeInfo(use = JsonTypeInfo.Id.NAME, property = "type")
public record UnitSummary(
    Organization unit,
    UnitTotals totals,
    UndisputedCandidatesByLocalApprovalStatus byLocalApprovalStatus,
    List<UnitSummary> units) {}
