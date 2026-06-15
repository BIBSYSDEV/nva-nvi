package no.sikt.nva.nvi.index.report;

import static java.util.Objects.nonNull;
import static nva.commons.apigateway.MediaType.CSV_UTF_8;
import static nva.commons.apigateway.MediaType.OOXML_SHEET;
import static nva.commons.core.attempt.Try.attempt;

import java.util.List;
import java.util.Optional;
import java.util.function.Predicate;
import java.util.stream.Stream;
import no.sikt.nva.nvi.common.service.NviPeriodService;
import no.sikt.nva.nvi.common.service.model.NviPeriod;
import no.sikt.nva.nvi.index.model.document.NviContributor;
import no.sikt.nva.nvi.index.model.report.InstitutionReportMapper;
import no.sikt.nva.nvi.index.model.report.PublicationPointsReportMapper;
import no.sikt.nva.nvi.index.model.report.ReportDocument;
import no.sikt.nva.nvi.index.report.query.AllInstitutionsQuery;
import no.sikt.nva.nvi.index.report.query.InstitutionQuery;
import no.sikt.nva.nvi.index.report.query.ReportAggregationQuery;
import no.sikt.nva.nvi.index.report.response.AllInstitutionsReport;
import no.sikt.nva.nvi.index.report.response.GenerateReportMessage;
import no.sikt.nva.nvi.index.report.response.InstitutionJsonReport;
import no.sikt.nva.nvi.report.generators.CsvGenerator;
import no.sikt.nva.nvi.report.generators.XlsxGenerator;
import no.sikt.nva.nvi.report.model.Row;
import no.sikt.nva.nvi.report.presigner.ReportPresigner.ReportPresignedUrl;
import nva.commons.apigateway.MediaType;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;

public class ReportGenerator {

  private static final Predicate<NviContributor> ALL_CONTRIBUTORS = contributor -> true;
  private static final Predicate<NviContributor> CONTRIBUTORS_WITH_ID =
      contributor -> nonNull(contributor.id());
  private final NviPeriodService nviPeriodService;
  private final ReportDocumentClient reportDocumentClient;
  private final S3Client s3Client;
  private final ReportAggregationClient reportAggregationClient;

  public ReportGenerator(
      NviPeriodService nviPeriodService,
      ReportDocumentClient reportDocumentClient,
      ReportAggregationClient reportAggregationClient,
      S3Client s3Client) {
    this.nviPeriodService = nviPeriodService;
    this.reportDocumentClient = reportDocumentClient;
    this.s3Client = s3Client;
    this.reportAggregationClient = reportAggregationClient;
  }

  public void generateReport(GenerateReportMessage message) {
    var rows = createReportRows(message);
    var mediaType = message.reportFormat().getMediaType();
    var report = generateReport(rows, mediaType);
    var reportPresignedUrl = message.reportPresignedUrl();
    upload(reportPresignedUrl, mediaType, report);
  }

  private void upload(ReportPresignedUrl reportPresignedUrl, MediaType mediaType, byte[] report) {
    s3Client.putObject(
        PutObjectRequest.builder()
            .bucket(reportPresignedUrl.bucket())
            .key(reportPresignedUrl.key())
            .contentType(mediaType.toString())
            .build(),
        RequestBody.fromBytes(report));
  }

  private List<Row> createReportRows(GenerateReportMessage message) {
    var period = nviPeriodService.getByPublishingYear(message.period());
    var query = createQuery(message, period);
    if (message.reportFormat().isPublicationPointsReport()) {
      return createRowsForPublicationPointsReport(message, query, period);
    }
    var documents = reportDocumentClient.fetchDocuments(query.query());
    return createRows(documents, query, applyContributorFilter(message));
  }

  private static Predicate<NviContributor> applyContributorFilter(GenerateReportMessage message) {
    return message.reportFormat().isAuthorSharesControlReport()
        ? ALL_CONTRIBUTORS
        : CONTRIBUTORS_WITH_ID;
  }

  private List<Row> createRowsForPublicationPointsReport(
      GenerateReportMessage message, ReportAggregationQuery<?> query, NviPeriod period) {
    if (query instanceof InstitutionQuery institutionQuery) {
      return creatRowsForInstitutionPublicationPointsReport(message, period, institutionQuery);
    }
    return createRowsForAllInstitutionsPublicationPointsReport(
        message, (AllInstitutionsQuery) query, period);
  }

  private List<Row> creatRowsForInstitutionPublicationPointsReport(
      GenerateReportMessage message, NviPeriod period, InstitutionQuery institutionQuery) {
    var report =
        attempt(() -> reportAggregationClient.executeQuery(institutionQuery))
            .map(Optional::orElseThrow)
            .map(result -> InstitutionJsonReport.from(message.queryId(), period, result))
            .orElseThrow();
    return PublicationPointsReportMapper.toRows(report);
  }

  private List<Row> createRowsForAllInstitutionsPublicationPointsReport(
      GenerateReportMessage message, AllInstitutionsQuery query, NviPeriod period) {
    var results = attempt(() -> reportAggregationClient.executeQuery(query)).orElseThrow();
    var report = AllInstitutionsReport.from(message.queryId(), period, results);
    return report.institutions().stream().map(PublicationPointsReportMapper::toRow).toList();
  }

  private static Stream<Row> toReportRows(
      ReportDocument document, Predicate<NviContributor> contributorFilter) {
    return document.approvals().stream()
        .flatMap(
            approval ->
                InstitutionReportMapper.mapToReportRows(
                    document, approval.institutionId(), contributorFilter));
  }

  private static byte[] generateReport(List<Row> rows, MediaType mediaType) {
    if (CSV_UTF_8.equals(mediaType)) {
      return new CsvGenerator(rows).toWorkbookByteArray();
    } else if (OOXML_SHEET.equals(mediaType)) {
      return new XlsxGenerator(rows).toWorkbookByteArray();
    } else {
      throw new IllegalArgumentException(
          "Unsupported media type for report %s".formatted(mediaType));
    }
  }

  private List<Row> createRows(
      List<ReportDocument> documents,
      ReportAggregationQuery<?> query,
      Predicate<NviContributor> contributorFilter) {
    if (query instanceof InstitutionQuery institutionQuery) {
      return documents.stream()
          .flatMap(
              document ->
                  InstitutionReportMapper.mapToReportRows(
                      document, institutionQuery.institutionId(), contributorFilter))
          .toList();
    }
    return documents.stream()
        .flatMap(document -> toReportRows(document, contributorFilter))
        .toList();
  }

  private ReportAggregationQuery<?> createQuery(GenerateReportMessage message, NviPeriod period) {
    return nonNull(message.institutionId())
        ? new InstitutionQuery(period, message.institutionId(), message.reportFormat())
        : new AllInstitutionsQuery(period);
  }
}
