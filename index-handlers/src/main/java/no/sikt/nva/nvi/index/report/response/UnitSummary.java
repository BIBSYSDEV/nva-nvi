package no.sikt.nva.nvi.index.report.response;

import com.fasterxml.jackson.annotation.JsonTypeInfo;
import java.util.List;
import no.sikt.nva.nvi.common.client.model.Organization;
import nva.commons.core.JacocoGenerated;

// TODO: Implemented later (NP-50858)
@JacocoGenerated
@JsonTypeInfo(use = JsonTypeInfo.Id.NAME, property = "type")
public record UnitSummary(
    Organization unit,
    UnitTotals totals,
    UndisputedCandidatesByLocalApprovalStatus byLocalApprovalStatus,
    List<UnitSummary> units) {}
