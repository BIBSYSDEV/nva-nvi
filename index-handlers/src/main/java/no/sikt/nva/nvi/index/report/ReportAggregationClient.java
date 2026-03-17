package no.sikt.nva.nvi.index.report;

import static java.util.Objects.nonNull;
import static no.sikt.nva.nvi.index.utils.SearchConstants.NVI_CANDIDATES_INDEX;
import static nva.commons.core.attempt.Try.attempt;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;
import no.sikt.nva.nvi.index.aws.OpenSearchClientFactory;
import no.sikt.nva.nvi.index.model.report.InstitutionReportMapper;
import no.sikt.nva.nvi.index.model.report.ReportDocument;
import no.sikt.nva.nvi.index.report.model.Row;
import no.sikt.nva.nvi.index.report.query.AllInstitutionsQuery;
import no.sikt.nva.nvi.index.report.query.InstitutionQuery;
import no.sikt.nva.nvi.index.report.query.ReportAggregationQuery;
import no.sikt.nva.nvi.index.xlsx.CsvGenerator;
import no.sikt.nva.nvi.index.xlsx.FastExcelXlsxGenerator;
import nva.commons.core.JacocoGenerated;
import org.opensearch.client.opensearch.OpenSearchClient;
import org.opensearch.client.opensearch._types.query_dsl.Query;
import org.opensearch.client.opensearch.core.ClearScrollRequest;
import org.opensearch.client.opensearch.core.ScrollRequest;
import org.opensearch.client.opensearch.core.SearchRequest;
import org.opensearch.client.opensearch.core.search.Hit;
import org.opensearch.client.opensearch.core.search.HitsMetadata;
import org.opensearch.client.opensearch.core.search.SourceConfig;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class ReportAggregationClient {

  private static final String SCROLL_TIMEOUT = "1m";
  private static final int SCROLL_PAGE_SIZE = 1000;
  private static final Logger LOGGER = LoggerFactory.getLogger(ReportAggregationClient.class);
  private static final SourceConfig REPORT_SOURCE_FILTER =
      SourceConfig.of(
          s ->
              s.filter(
                  f ->
                      f.includes(
                              List.of(
                                  "identifier",
                                  "reportingPeriod",
                                  "globalApprovalStatus",
                                  "publicationTypeChannelLevelPoints",
                                  "internationalCollaborationFactor",
                                  "creatorShareCount",
                                  "publicationDetails.id",
                                  "publicationDetails.type",
                                  "publicationDetails.title",
                                  "publicationDetails.publicationDate",
                                  "publicationDetails.nviContributors",
                                  "publicationDetails.publicationChannel",
                                  "publicationDetails.pages",
                                  "publicationDetails.language",
                                  "approvals.institutionId",
                                  "approvals.approvalStatus",
                                  "approvals.points"))
                          .excludes(
                              List.of(
                                  "publicationDetails.nviContributors.role",
                                  "publicationDetails.nviContributors.affiliations.partOfIdentifiers",
                                  "approvals.points.institutionId",
                                  "approvals.points.institutionPoints"))));
  private final OpenSearchClient client;

  public ReportAggregationClient(OpenSearchClient client) {
    this.client = client;
  }

  @JacocoGenerated
  public static ReportAggregationClient defaultClient() {
    return new ReportAggregationClient(OpenSearchClientFactory.createAuthenticatedClient());
  }

  public <T> T executeQuery(ReportAggregationQuery<T> query) throws IOException {
    LOGGER.info("Executing query: {}", query);
    return processQuery(query);
  }

  public byte[] executeCsvReport(InstitutionQuery query) {
    LOGGER.info("Executing CSV report query: {}", query);
    var rows =
        fetchReportDocuments(query.query()).stream()
            .flatMap(
                document ->
                    InstitutionReportMapper.mapToReportRows(document, query.institutionId()))
            .toList();
    return new CsvGenerator(rows).toWorkbookByteArray();
  }

  public byte[] executeCsvReport(AllInstitutionsQuery query) {
    LOGGER.info("Executing CSV report query: {}", query);
    var rows =
        fetchReportDocuments(query.query()).stream()
            .flatMap(ReportAggregationClient::toReportRows)
            .toList();
    return new CsvGenerator(rows).toWorkbookByteArray();
  }

  private static Stream<Row> toReportRows(ReportDocument document) {
    return document.approvals().stream()
        .flatMap(
            approval ->
                InstitutionReportMapper.mapToReportRows(document, approval.institutionId()));
  }

  public byte[] executeXlsxExport(AllInstitutionsQuery query) {
    LOGGER.info("Executing XLSX report query: {}", query);
    var rows =
        fetchReportDocuments(query.query()).stream()
            .flatMap(ReportAggregationClient::toReportRows)
            .toList();
    return new FastExcelXlsxGenerator(rows).toWorkbookByteArray();
  }

  public byte[] executeXlsxReport(InstitutionQuery query) {
    LOGGER.info("Executing XLSX report query: {}", query);
    var rows =
        fetchReportDocuments(query.query()).stream()
            .flatMap(
                document ->
                    InstitutionReportMapper.mapToReportRows(document, query.institutionId()))
            .toList();
    return new FastExcelXlsxGenerator(rows).toWorkbookByteArray();
  }

  private <T> T processQuery(ReportAggregationQuery<T> query) throws IOException {
    var searchRequest =
        new SearchRequest.Builder()
            .index(NVI_CANDIDATES_INDEX)
            .size(0)
            .query(query.query())
            .aggregations(query.aggregations())
            .build();
    var response = client.search(searchRequest, Void.class);
    return query.parseResponse(response);
  }

  private List<ReportDocument> fetchReportDocuments(Query query) {
    var documents = new ArrayList<ReportDocument>();
    var scrollId = initializeScroll(documents, query);
    try {
      scrollId = fetchRemainingPages(documents, scrollId);
    } finally {
      clearScroll(scrollId);
    }
    return documents;
  }

  private String initializeScroll(List<ReportDocument> documents, Query query) {
    var request =
        new SearchRequest.Builder()
            .index(NVI_CANDIDATES_INDEX)
            .size(SCROLL_PAGE_SIZE)
            .query(query)
            .source(REPORT_SOURCE_FILTER)
            .scroll(builder -> builder.time(SCROLL_TIMEOUT))
            .build();
    var response = attempt(() -> client.search(request, ReportDocument.class)).orElseThrow();
    addHitsToListOfDocuments(response.hits(), documents);
    return response.scrollId();
  }

  private String fetchRemainingPages(List<ReportDocument> documents, String scrollId) {
    var request =
        ScrollRequest.of(
            builder -> builder.scrollId(scrollId).scroll(build -> build.time(SCROLL_TIMEOUT)));
    var scrollResponse = attempt(() -> client.scroll(request, ReportDocument.class)).orElseThrow();
    var hits = scrollResponse.hits();
    if (hits.hits().isEmpty()) {
      return scrollId;
    }
    addHitsToListOfDocuments(hits, documents);
    return fetchRemainingPages(documents, scrollResponse.scrollId());
  }

  private static void addHitsToListOfDocuments(
      HitsMetadata<ReportDocument> hits, List<ReportDocument> documents) {
    hits.hits().stream().map(Hit::source).forEach(documents::add);
  }

  private void clearScroll(String scrollId) {
    if (nonNull(scrollId)) {
      attempt(
          () -> client.clearScroll(ClearScrollRequest.of(builder -> builder.scrollId(scrollId))));
    }
  }
}
