package no.sikt.nva.nvi.index.report.response;

import java.io.IOException;
import java.util.NoSuchElementException;
import java.util.function.Supplier;
import no.sikt.nva.nvi.common.service.NviPeriodService;
import no.sikt.nva.nvi.common.service.model.NviPeriod;
import no.sikt.nva.nvi.index.report.ReportAggregationClient;
import no.sikt.nva.nvi.index.report.query.AllInstitutionsQuery;
import no.sikt.nva.nvi.index.report.query.AllPeriodsQuery;
import no.sikt.nva.nvi.index.report.query.InstitutionQuery;
import no.sikt.nva.nvi.index.report.query.PeriodQuery;
import no.sikt.nva.nvi.index.report.request.AllInstitutionsReportRequest;
import no.sikt.nva.nvi.index.report.request.AllPeriodsReportRequest;
import no.sikt.nva.nvi.index.report.request.InstitutionReportRequest;
import no.sikt.nva.nvi.index.report.request.PeriodReportRequest;
import no.sikt.nva.nvi.index.report.request.ReportRequest;
import no.sikt.nva.nvi.report.presigner.Extension;

public class ReportService {

  private final NviPeriodService nviPeriodService;
  private final ReportAggregationClient reportAggregationClient;
  private final PresignReportService presignReportService;

  public ReportService(
      NviPeriodService nviPeriodService,
      ReportAggregationClient reportAggregationClient,
      PresignReportService presignReportService) {
    this.nviPeriodService = nviPeriodService;
    this.reportAggregationClient = reportAggregationClient;
    this.presignReportService = presignReportService;
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
    return AllPeriodsReport.from(request.queryId(), results);
  }

  private PeriodReport periodReport(PeriodReportRequest request) throws IOException {
    var period = nviPeriodService.getByPublishingYear(request.period());
    var result = reportAggregationClient.executeQuery(new PeriodQuery(period));
    return PeriodReport.from(request.queryId(), result);
  }

  private ReportResponse allInstitutionsReport(AllInstitutionsReportRequest request)
      throws IOException {
    var period = nviPeriodService.getByPublishingYear(request.period());
    var query = new AllInstitutionsQuery(period);
    return switch (request.reportType()) {
      case CSV_AUTHOR_SHARES, CSV_PUBLICATION_POINTS ->
          presignReportService.presign(request, Extension.CSV);
      case XLSX_AUTHOR_SHARES, XLSX_PUBLICATION_POINTS ->
          presignReportService.presign(request, Extension.XLSX);
      case JSON -> createAllInstitutionsJsonReport(request, query, period);
    };
  }

  private AllInstitutionsReport createAllInstitutionsJsonReport(
      AllInstitutionsReportRequest request, AllInstitutionsQuery query, NviPeriod period)
      throws IOException {
    var results = reportAggregationClient.executeQuery(query);
    return AllInstitutionsReport.from(request.queryId(), period, results);
  }

  private ReportResponse institutionReport(InstitutionReportRequest request) throws IOException {
    var period = nviPeriodService.getByPublishingYear(request.period());
    var query = new InstitutionQuery(period, request.institutionId(), request.reportType());
    return switch (request.reportType()) {
      case CSV_AUTHOR_SHARES, CSV_PUBLICATION_POINTS ->
          presignReportService.presign(request, Extension.CSV);
      case XLSX_AUTHOR_SHARES, XLSX_PUBLICATION_POINTS ->
          presignReportService.presign(request, Extension.XLSX);
      default -> createInstitutionJsonReport(request, query, period);
    };
  }

  private InstitutionJsonReport createInstitutionJsonReport(
      InstitutionReportRequest request, InstitutionQuery query, NviPeriod period)
      throws IOException {
    return reportAggregationClient
        .executeQuery(query)
        .map(result -> InstitutionJsonReport.from(request.queryId(), period, result))
        .orElseThrow(noSuchElementException(request));
  }

  private static Supplier<NoSuchElementException> noSuchElementException(
      InstitutionReportRequest request) {
    return () ->
        new NoSuchElementException(
            "No report found for institution %s in period %s"
                .formatted(request.institutionId(), request.period()));
  }
}
