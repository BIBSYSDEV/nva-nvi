package no.sikt.nva.nvi.index.report.response;

import static java.util.Collections.emptyList;

import java.io.IOException;
import java.net.URI;
import java.util.NoSuchElementException;
import java.util.function.Supplier;
import no.sikt.nva.nvi.common.client.model.Organization;
import no.sikt.nva.nvi.common.service.NviPeriodService;
import no.sikt.nva.nvi.index.report.ReportAggregationClient;
import no.sikt.nva.nvi.index.report.model.InstitutionAggregationResult;
import no.sikt.nva.nvi.index.report.model.PeriodAggregationResult;
import no.sikt.nva.nvi.index.report.query.AllInstitutionsQuery;
import no.sikt.nva.nvi.index.report.query.AllPeriodsQuery;
import no.sikt.nva.nvi.index.report.query.InstitutionQuery;
import no.sikt.nva.nvi.index.report.query.PeriodQuery;
import no.sikt.nva.nvi.index.report.request.AllInstitutionsReportRequest;
import no.sikt.nva.nvi.index.report.request.AllPeriodsReportRequest;
import no.sikt.nva.nvi.index.report.request.InstitutionReportRequest;
import no.sikt.nva.nvi.index.report.request.PeriodReportRequest;
import no.sikt.nva.nvi.index.report.request.ReportRequest;
import nva.commons.core.paths.UriWrapper;

public class ReportService {

  private final NviPeriodService nviPeriodService;
  private final ReportAggregationClient reportAggregationClient;

  public ReportService(
      NviPeriodService nviPeriodService, ReportAggregationClient reportAggregationClient) {
    this.nviPeriodService = nviPeriodService;
    this.reportAggregationClient = reportAggregationClient;
  }

  public ReportResponse getResponse(ReportRequest reportRequest) throws IOException {
    return switch (reportRequest) {
      case AllPeriodsReportRequest request -> allPeriodsReport(request);
      case PeriodReportRequest request -> periodReport(request);
      case AllInstitutionsReportRequest request -> allInstitutionsReport(request);
      case InstitutionReportRequest request -> institutionReport(request);
    };
  }

  private AllPeriodsReport allPeriodsReport(AllPeriodsReportRequest request) throws IOException {
    var periods = nviPeriodService.getAll();
    var results = reportAggregationClient.executeQuery(new AllPeriodsQuery(periods));
    var periodReports =
        results.stream().map(result -> toPeriodReport(request.queryId(), result)).toList();
    return new AllPeriodsReport(request.queryId(), periodReports);
  }

  private static PeriodReport toPeriodReport(
      URI allPeriodsQueryId, PeriodAggregationResult result) {
    var periodQueryId =
        UriWrapper.fromUri(allPeriodsQueryId)
            .addChild(String.valueOf(result.period().publishingYear()))
            .getUri();
    return new PeriodReport(
        periodQueryId,
        result.period().toDto(),
        PeriodTotals.from(result),
        CandidatesByGlobalApprovalStatus.from(result));
  }

  private PeriodReport periodReport(PeriodReportRequest request) throws IOException {
    var period = nviPeriodService.getByPublishingYear(request.period());
    var result = reportAggregationClient.executeQuery(new PeriodQuery(period));
    return new PeriodReport(
        request.queryId(),
        result.period().toDto(),
        PeriodTotals.from(result),
        CandidatesByGlobalApprovalStatus.from(result));
  }

  private AllInstitutionsReport allInstitutionsReport(AllInstitutionsReportRequest request)
      throws IOException {
    var period = nviPeriodService.getByPublishingYear(request.period());
    var institutionReports =
        reportAggregationClient.executeQuery(new AllInstitutionsQuery(period)).stream()
            .map(result -> toInstitutionReport(request, result))
            .toList();
    return new AllInstitutionsReport(request.queryId(), period.toDto(), institutionReports);
  }

  private InstitutionReport institutionReport(InstitutionReportRequest request) throws IOException {
    var period = nviPeriodService.getByPublishingYear(request.period());
    var query = new InstitutionQuery(period, request.institutionId());
    return reportAggregationClient
        .executeQuery(query)
        .map(result -> toInstitutionReport(request.queryId(), result))
        .orElseThrow(noSuchElementException(request));
  }

  private static Supplier<NoSuchElementException> noSuchElementException(
      InstitutionReportRequest request) {
    return () ->
        new NoSuchElementException(
            "No report found for institution %s in period %s"
                .formatted(request.institutionId(), request.period()));
  }

  private static InstitutionReport toInstitutionReport(
      AllInstitutionsReportRequest request, InstitutionAggregationResult result) {
    var queryId = institutionQueryId(request.queryId(), result.institutionId());
    return toInstitutionReport(queryId, result);
  }

  private static InstitutionReport toInstitutionReport(
      URI queryId, InstitutionAggregationResult result) {
    var organization =
        Organization.builder().withId(result.institutionId()).withLabels(result.labels()).build();
    var totals = InstitutionTotals.from(result);
    var byLocalApprovalStatus = UndisputedCandidatesByLocalApprovalStatus.from(result.undisputed());
    return new InstitutionReport(
        queryId,
        result.period().toDto(),
        result.sector(),
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
