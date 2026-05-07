package no.sikt.nva.nvi.index.report.response;

import static java.util.Collections.emptyList;

import java.net.URI;
import java.util.List;
import no.sikt.nva.nvi.common.client.model.Organization;
import no.sikt.nva.nvi.common.model.Sector;
import no.sikt.nva.nvi.common.service.dto.NviPeriodDto;
import no.sikt.nva.nvi.common.service.model.NviPeriod;
import no.sikt.nva.nvi.index.report.model.InstitutionAggregationResult;

public record InstitutionJsonReport(
    URI id,
    NviPeriodDto period,
    Sector sector,
    Organization institution,
    InstitutionSummary institutionSummary,
    List<UnitSummary> units)
    implements ReportResponse, InstitutionReport {

  public static InstitutionJsonReport from(
      URI queryId, NviPeriod period, InstitutionAggregationResult result) {
    var organization =
        Organization.builder().withId(result.institutionId()).withLabels(result.labels()).build();
    var totals = InstitutionTotals.from(result);
    var byLocalApprovalStatus = UndisputedCandidatesByLocalApprovalStatus.from(result.undisputed());
    return new InstitutionJsonReport(
        queryId,
        period.toDto(),
        result.sector(),
        organization,
        new InstitutionSummary(totals, byLocalApprovalStatus),
        emptyList() // TODO: Implemented later (NP-50858)
        );
  }
}
