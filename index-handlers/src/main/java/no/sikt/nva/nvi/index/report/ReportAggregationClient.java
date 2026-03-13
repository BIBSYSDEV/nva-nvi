package no.sikt.nva.nvi.index.report;

import static java.util.Objects.nonNull;
import static no.sikt.nva.nvi.index.utils.SearchConstants.NVI_CANDIDATES_INDEX;
import static nva.commons.core.attempt.Try.attempt;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import no.sikt.nva.nvi.index.aws.OpenSearchClientFactory;
import no.sikt.nva.nvi.index.model.report.InstitutionReportMapper;
import no.sikt.nva.nvi.index.model.report.InstitutionReportRow;
import no.sikt.nva.nvi.index.model.report.ReportDocument;
import no.sikt.nva.nvi.index.report.query.AllInstitutionsQuery;
import no.sikt.nva.nvi.index.report.query.InstitutionQuery;
import no.sikt.nva.nvi.index.report.query.ReportAggregationQuery;
import no.sikt.nva.nvi.index.report.query.XlsxReportQuery;
import nva.commons.core.JacocoGenerated;
import org.opensearch.client.opensearch.OpenSearchClient;
import org.opensearch.client.opensearch._types.query_dsl.Query;
import org.opensearch.client.opensearch.core.ClearScrollRequest;
import org.opensearch.client.opensearch.core.ScrollRequest;
import org.opensearch.client.opensearch.core.SearchRequest;
import org.opensearch.client.opensearch.core.SearchResponse;
import org.opensearch.client.opensearch.core.search.Hit;
import org.opensearch.client.opensearch.core.search.HitsMetadata;
import org.opensearch.client.opensearch.core.search.SourceConfig;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class ReportAggregationClient {

  private static final String SCROLL_TIMEOUT = "1m";
  private static final int SCROLL_PAGE_SIZE = 300;
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
  private static final ReportGenerator<InstitutionReportRow> reportGenerator =
      new ReportGenerator<>(InstitutionReportRow.class);
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

  public String executeXlsxReport(XlsxReportQuery query) {
    LOGGER.info("Executing XLSX report query: {}", query);
    return switch (query) {
      case InstitutionQuery institutionQuery -> createXlsxForInstitution(institutionQuery);
      case AllInstitutionsQuery allInstitutionsQuery ->
          createXlsxReportForAllInstitutions(allInstitutionsQuery);
    };
  }

  private String createXlsxForInstitution(InstitutionQuery institutionQuery) {
    var rows =
        fetchReportDocuments(institutionQuery.query()).stream()
            .flatMap(
                document ->
                    InstitutionReportMapper.mapToRows(document, institutionQuery.institutionId()))
            .toList();
    return reportGenerator.generate(rows);
  }

  private String createXlsxReportForAllInstitutions(AllInstitutionsQuery query) {
    var rows =
        fetchReportDocuments(query.query()).stream()
            .flatMap(
                document ->
                    document.approvals().stream()
                        .flatMap(
                            approval ->
                                InstitutionReportMapper.mapToRows(
                                    document, approval.institutionId())))
            .toList();
    return reportGenerator.generate(rows);
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
    var candidates = new ArrayList<ReportDocument>();
    var scrollId = initializeScroll(candidates, query);
    try {
      scrollId = fetchRemainingPages(candidates, scrollId);
    } finally {
      clearScroll(scrollId);
    }
    return candidates;
  }

  private String initializeScroll(List<ReportDocument> candidates, Query query) {
    var request =
        new SearchRequest.Builder()
            .index(NVI_CANDIDATES_INDEX)
            .size(SCROLL_PAGE_SIZE)
            .query(query)
            .source(REPORT_SOURCE_FILTER)
            .scroll(builder -> builder.time(SCROLL_TIMEOUT))
            .build();
    var response = searchWithScroll(request);
    addHitsToListOfCandidates(response.hits(), candidates);
    return response.scrollId();
  }

  private SearchResponse<ReportDocument> searchWithScroll(SearchRequest request) {
    return attempt(() -> client.search(request, ReportDocument.class)).orElseThrow();
  }

  private String fetchRemainingPages(List<ReportDocument> candidates, String scrollId) {
    var scrollResponse = scroll(scrollId);
    var hits = scrollResponse.hits();
    if (hits.hits().isEmpty()) {
      return scrollId;
    }
    addHitsToListOfCandidates(hits, candidates);
    return fetchRemainingPages(candidates, scrollResponse.scrollId());
  }

  private SearchResponse<ReportDocument> scroll(String scrollId) {
    var request =
        ScrollRequest.of(
            builder -> builder.scrollId(scrollId).scroll(build -> build.time(SCROLL_TIMEOUT)));
    return attempt(() -> client.scroll(request, ReportDocument.class)).orElseThrow();
  }

  private void clearScroll(String scrollId) {
    if (nonNull(scrollId)) {
      attempt(
          () -> client.clearScroll(ClearScrollRequest.of(builder -> builder.scrollId(scrollId))));
    }
  }

  private static void addHitsToListOfCandidates(
      HitsMetadata<ReportDocument> hits, List<ReportDocument> fetchedCandidates) {
    hits.hits().stream().map(Hit::source).forEach(fetchedCandidates::add);
  }
}
