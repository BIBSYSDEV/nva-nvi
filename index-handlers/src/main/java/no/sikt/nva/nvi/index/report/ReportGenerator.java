package no.sikt.nva.nvi.index.report;

import static java.util.Objects.nonNull;

import java.util.List;
import java.util.stream.Stream;
import no.sikt.nva.nvi.common.service.NviPeriodService;
import no.sikt.nva.nvi.common.service.model.NviPeriod;
import no.sikt.nva.nvi.index.model.report.InstitutionReportMapper;
import no.sikt.nva.nvi.index.model.report.ReportDocument;
import no.sikt.nva.nvi.index.report.query.AllInstitutionsQuery;
import no.sikt.nva.nvi.index.report.query.InstitutionQuery;
import no.sikt.nva.nvi.index.report.query.ReportAggregationQuery;
import no.sikt.nva.nvi.index.report.request.ReportType;
import no.sikt.nva.nvi.index.report.response.GenerateReportMessage;
import no.sikt.nva.nvi.report.generators.CsvGenerator;
import no.sikt.nva.nvi.report.generators.XlsxGenerator;
import no.sikt.nva.nvi.report.model.Row;
import no.sikt.nva.nvi.report.presigner.Extension;
import no.sikt.nva.nvi.report.presigner.ReportPresigner.ReportPresignedUrl;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;

public class ReportGenerator {

  private final NviPeriodService nviPeriodService;
  private final ReportDocumentClient reportDocumentClient;
  private final S3Client s3Client;

  public ReportGenerator(
      NviPeriodService nviPeriodService,
      ReportDocumentClient reportDocumentClient,
      S3Client s3Client) {
    this.nviPeriodService = nviPeriodService;
    this.reportDocumentClient = reportDocumentClient;
    this.s3Client = s3Client;
  }

  public void generateReport(GenerateReportMessage message) {
    var rows = createReportRows(message);
    var extension = message.reportPresignedUrl().extension();
    var report = generateReport(rows, extension);
    var reportPresignedUrl = message.reportPresignedUrl();
    upload(reportPresignedUrl, report);
  }

  private void upload(ReportPresignedUrl reportPresignedUrl, byte[] report) {
    s3Client.putObject(
        PutObjectRequest.builder()
            .bucket(reportPresignedUrl.bucket())
            .key(reportPresignedUrl.key())
            .contentType(reportPresignedUrl.extension().getMediaType())
            .build(),
        RequestBody.fromBytes(report));
  }

  private List<Row> createReportRows(GenerateReportMessage message) {
    var period = nviPeriodService.getByPublishingYear(message.period());
    var query = createQuery(message, period);
    var documents = reportDocumentClient.fetchDocuments(query.query());
    return createRows(documents, query);
  }

  private static Stream<Row> toReportRows(ReportDocument document) {
    return document.approvals().stream()
        .flatMap(
            approval ->
                InstitutionReportMapper.mapToReportRows(document, approval.institutionId()));
  }

  private static byte[] generateReport(List<Row> rows, Extension extension) {
    return switch (extension) {
      case CSV -> new CsvGenerator(rows).toWorkbookByteArray();
      case XLSX -> new XlsxGenerator(rows).toWorkbookByteArray();
    };
  }

  private static ReportType extractReportType(Extension extension) {
    return switch (extension) {
      case XLSX -> ReportType.XLSX;
      case CSV -> ReportType.CSV;
    };
  }

  private List<Row> createRows(List<ReportDocument> documents, ReportAggregationQuery<?> query) {
    if (query instanceof InstitutionQuery institutionQuery) {
      return documents.stream()
          .flatMap(
              document ->
                  InstitutionReportMapper.mapToReportRows(
                      document, institutionQuery.institutionId()))
          .toList();
    }
    return documents.stream().flatMap(ReportGenerator::toReportRows).toList();
  }

  private ReportAggregationQuery<?> createQuery(GenerateReportMessage message, NviPeriod period) {
    return nonNull(message.institutionId())
        ? new InstitutionQuery(
            period,
            message.institutionId(),
            extractReportType(message.reportPresignedUrl().extension()))
        : new AllInstitutionsQuery(period);
  }
}
