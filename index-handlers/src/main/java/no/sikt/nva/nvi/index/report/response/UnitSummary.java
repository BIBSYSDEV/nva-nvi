package no.sikt.nva.nvi.index.report.response;

import com.fasterxml.jackson.annotation.JsonTypeInfo;
import java.util.List;
import no.sikt.nva.nvi.common.client.model.Organization;

@JsonTypeInfo(use = JsonTypeInfo.Id.NAME, property = "type")
public record UnitSummary(
    Organization unit,
    UnitTotals totals,
    UndisputedCandidatesByLocalApprovalStatus byLocalApprovalStatus,
    List<UnitSummary> units) {}
