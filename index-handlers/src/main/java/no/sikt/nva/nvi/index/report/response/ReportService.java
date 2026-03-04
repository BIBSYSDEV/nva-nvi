package no.sikt.nva.nvi.index.report.response;

import java.io.IOException;
import java.net.URI;
import java.util.NoSuchElementException;
import java.util.function.Supplier;
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
    var periodReports = results.stream().map(result -> toPeriodReport(request, result)).toList();
    return new AllPeriodsReport(request.queryId(), periodReports);
  }

  private PeriodReport periodReport(PeriodReportRequest request) throws IOException {
    var period = nviPeriodService.getByPublishingYear(request.period());
    var result = reportAggregationClient.executeQuery(new PeriodQuery(period));
    return toPeriodReport(request, result);
  }

  private static PeriodReport toPeriodReport(
      PeriodReportRequest request, PeriodAggregationResult result) {
    return PeriodReport.from(request.queryId(), result);
  }

  private static PeriodReport toPeriodReport(
      AllPeriodsReportRequest request, PeriodAggregationResult result) {
    var periodQueryId =
        UriWrapper.fromUri(request.queryId())
            .addChild(String.valueOf(result.period().publishingYear()))
            .getUri();
    return PeriodReport.from(periodQueryId, result);
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
        .map(result -> toInstitutionReport(request, result))
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
    return InstitutionReport.from(queryId, result);
  }

  private static InstitutionReport toInstitutionReport(
      InstitutionReportRequest request, InstitutionAggregationResult result) {
    return InstitutionReport.from(request.queryId(), result);
  }

  private static URI institutionQueryId(URI allInstitutionsQueryId, URI institutionId) {
    var identifier = UriWrapper.fromUri(institutionId).getLastPathElement();
    return UriWrapper.fromUri(allInstitutionsQueryId).addChild(identifier).getUri();
  }
}
