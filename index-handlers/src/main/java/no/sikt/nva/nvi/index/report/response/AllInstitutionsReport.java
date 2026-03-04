package no.sikt.nva.nvi.index.report.response;

import java.net.URI;
import java.util.Collection;
import java.util.List;
import no.sikt.nva.nvi.common.service.dto.NviPeriodDto;
import no.sikt.nva.nvi.common.service.model.NviPeriod;
import no.sikt.nva.nvi.index.report.model.InstitutionAggregationResult;
import nva.commons.core.paths.UriWrapper;

public record AllInstitutionsReport(
    URI id, NviPeriodDto period, List<InstitutionReport> institutions) implements ReportResponse {

  public static AllInstitutionsReport from(
      URI queryId, NviPeriod period, Collection<InstitutionAggregationResult> results) {
    var institutionReports =
        results.stream()
            .map(institutionResult -> getInstitutionReport(queryId, institutionResult))
            .toList();
    return new AllInstitutionsReport(queryId, period.toDto(), institutionReports);
  }

  private static InstitutionReport getInstitutionReport(
      URI queryId, InstitutionAggregationResult institutionResult) {
    var institutionQueryId = institutionQueryId(queryId, institutionResult.institutionId());
    return InstitutionReport.from(
        institutionQueryId, institutionResult.period(), institutionResult);
  }

  private static URI institutionQueryId(URI allInstitutionsQueryId, URI institutionId) {
    var identifier = UriWrapper.fromUri(institutionId).getLastPathElement();
    return UriWrapper.fromUri(allInstitutionsQueryId).addChild(identifier).getUri();
  }
}
