package no.sikt.nva.nvi.index.report.response;

import java.net.URI;
import java.util.Collection;
import java.util.List;
import no.sikt.nva.nvi.index.report.model.PeriodAggregationResult;
import nva.commons.core.paths.UriWrapper;

public record AllPeriodsReport(URI id, List<PeriodReport> periods) implements ReportResponse {

  public static AllPeriodsReport from(URI queryId, Collection<PeriodAggregationResult> results) {
    var periodReports =
        results.stream().map(periodResult -> getPeriodReport(queryId, periodResult)).toList();
    return new AllPeriodsReport(queryId, periodReports);
  }

  private static PeriodReport getPeriodReport(URI queryId, PeriodAggregationResult result) {
    var periodQueryId = periodQueryId(queryId, result);
    return PeriodReport.from(periodQueryId, result);
  }

  private static URI periodQueryId(URI allPeriodsQueryId, PeriodAggregationResult periodResult) {
    return UriWrapper.fromUri(allPeriodsQueryId)
        .addChild(String.valueOf(periodResult.period().publishingYear()))
        .getUri();
  }
}
