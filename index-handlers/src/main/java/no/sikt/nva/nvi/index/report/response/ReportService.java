package no.sikt.nva.nvi.index.report.response;

import java.io.IOException;
import java.net.URI;
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
import nva.commons.apigateway.MediaType;

public class ReportService {

  private final NviPeriodService nviPeriodService;
  private final ReportAggregationClient reportAggregationClient;
  private final ReportUploader reportUploader;

  public ReportService(
      NviPeriodService nviPeriodService,
      ReportAggregationClient reportAggregationClient,
      ReportUploader reportUploader) {
    this.nviPeriodService = nviPeriodService;
    this.reportAggregationClient = reportAggregationClient;
    this.reportUploader = reportUploader;
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
      case CSV -> createCsvReport(request, query);
      case XLSX -> createXlsxReport(request, query);
      case JSON -> createAllInstitutionsJsonReport(request, query, period);
    };
  }

  private CsvReport createCsvReport(
      AllInstitutionsReportRequest request, AllInstitutionsQuery query) {
    var base64Content = reportAggregationClient.executeCsvReport(query);
    var uri = upload(base64Content, "csv", MediaType.CSV_UTF_8.toString());
    return new CsvReport(request.queryId(), uri);
  }

  private CsvReport createXlsxReport(
      AllInstitutionsReportRequest request, AllInstitutionsQuery query) {
    var base64Content = reportAggregationClient.executeXlsxExport(query);
    var uri = upload(base64Content, "xlsx", MediaType.CSV_UTF_8.toString());
    return new CsvReport(request.queryId(), uri);
  }

  private CsvReport createCsvReport(ReportRequest request, InstitutionQuery query) {
    var bytes = reportAggregationClient.executeCsvReport(query);
    var uri = upload(bytes, "csv", MediaType.CSV_UTF_8.toString());
    return new CsvReport(request.queryId(), uri);
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
      case XLSX -> createXlsxReport(request, query);
      case CSV -> createCsvReport(request, query);
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

  private XlsxReport createXlsxReport(ReportRequest request, InstitutionQuery query) {
    var base64Content = reportAggregationClient.executeXlsxReport(query);
    var uri = upload(base64Content, "xlsx", MediaType.OOXML_SHEET.toString());
    return new XlsxReport(request.queryId(), uri);
  }

  private URI upload(byte[] bytes, String extension, String contentType) {
    return reportUploader.upload(bytes, extension, contentType);
  }

  private static Supplier<NoSuchElementException> noSuchElementException(
      InstitutionReportRequest request) {
    return () ->
        new NoSuchElementException(
            "No report found for institution %s in period %s"
                .formatted(request.institutionId(), request.period()));
  }
}
