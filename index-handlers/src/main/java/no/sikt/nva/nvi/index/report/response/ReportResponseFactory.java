package no.sikt.nva.nvi.index.report.response;

import static java.util.Collections.emptyList;

import java.io.IOException;
import java.math.BigDecimal;
import java.net.URI;
import no.sikt.nva.nvi.common.client.model.Organization;
import no.sikt.nva.nvi.common.model.Sector;
import no.sikt.nva.nvi.common.service.NviPeriodService;
import no.sikt.nva.nvi.common.service.dto.NviPeriodDto;
import no.sikt.nva.nvi.index.report.ReportAggregationClient;
import no.sikt.nva.nvi.index.report.model.InstitutionAggregationResult;
import no.sikt.nva.nvi.index.report.query.AllInstitutionsQuery;
import no.sikt.nva.nvi.index.report.query.InstitutionQuery;
import no.sikt.nva.nvi.index.report.request.AllInstitutionsReportRequest;
import no.sikt.nva.nvi.index.report.request.AllPeriodsReportRequest;
import no.sikt.nva.nvi.index.report.request.InstitutionReportRequest;
import no.sikt.nva.nvi.index.report.request.PeriodReportRequest;
import no.sikt.nva.nvi.index.report.request.ReportRequest;
import nva.commons.core.paths.UriWrapper;

public class ReportResponseFactory {

  private final NviPeriodService nviPeriodService;
  private final ReportAggregationClient reportAggregationClient;

  public ReportResponseFactory(
      NviPeriodService nviPeriodService, ReportAggregationClient reportAggregationClient) {
    this.nviPeriodService = nviPeriodService;
    this.reportAggregationClient = reportAggregationClient;
  }

  public ReportResponse getResponse(ReportRequest reportRequest) throws IOException {
    return switch (reportRequest) {
      case AllPeriodsReportRequest request -> placeholderAllPeriodsReport(request);
      case PeriodReportRequest request -> placeholderPeriodReport(request);
      case AllInstitutionsReportRequest request -> allInstitutionsReport(request);
      case InstitutionReportRequest request -> institutionReport(request);
    };
  }

  // FIXME: Temporary placeholder
  private AllPeriodsReport placeholderAllPeriodsReport(AllPeriodsReportRequest request) {
    return new AllPeriodsReport(request.queryId(), emptyList());
  }

  // FIXME: Temporary placeholder
  private PeriodReport placeholderPeriodReport(PeriodReportRequest request) {
    var periodDto = nviPeriodService.getByPublishingYear(request.period()).toDto();
    return new PeriodReport(
        request.queryId(),
        periodDto,
        new PeriodTotals(BigDecimal.ZERO, 0, 0, 0),
        new CandidatesByGlobalApprovalStatus(0, 0, 0, 0));
  }

  private AllInstitutionsReport allInstitutionsReport(AllInstitutionsReportRequest request)
      throws IOException {
    var period = nviPeriodService.getByPublishingYear(request.period());
    var periodDto = period.toDto();
    var results = reportAggregationClient.executeQuery(new AllInstitutionsQuery(period));
    var institutionReports =
        results.stream()
            .map(
                result -> {
                  var queryId = institutionQueryId(request.queryId(), result.institutionId());
                  return toInstitutionReport(queryId, periodDto, result);
                })
            .toList();
    return new AllInstitutionsReport(request.queryId(), periodDto, institutionReports);
  }

  private InstitutionReport institutionReport(InstitutionReportRequest request) throws IOException {
    var period = nviPeriodService.getByPublishingYear(request.period());
    var periodDto = period.toDto();
    var query = new InstitutionQuery(period, request.institutionId());
    return reportAggregationClient
        .executeQuery(query)
        .map(result -> toInstitutionReport(request.queryId(), periodDto, result))
        .orElseThrow();
  }

  private static InstitutionReport toInstitutionReport(
      URI queryId, NviPeriodDto periodDto, InstitutionAggregationResult result) {
    var organization =
        Organization.builder().withId(result.institutionId()).withLabels(result.labels()).build();
    var sector = Sector.fromString(result.sector()).orElse(Sector.UNKNOWN);
    var totals = InstitutionTotals.from(result);
    var byLocalApprovalStatus = UndisputedCandidatesByLocalApprovalStatus.from(result.undisputed());
    return new InstitutionReport(
        queryId,
        periodDto,
        sector,
        organization,
        new InstitutionSummary(totals, byLocalApprovalStatus),
        emptyList() // TODO: Implemented later (NP-50858)
        );
  }

  private static URI institutionQueryId(URI allInstitutionsQueryId, URI institutionId) {
    var identifier = UriWrapper.fromUri(institutionId).getLastPathElement();
    return UriWrapper.fromUri(allInstitutionsQueryId).addChild(identifier).getUri();
  }
}
