package no.sikt.nva.nvi.index.report;

import static java.util.Objects.nonNull;
import static no.sikt.nva.nvi.index.utils.SearchConstants.NVI_CANDIDATES_INDEX;
import static nva.commons.core.attempt.Try.attempt;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.stream.Stream;
import no.sikt.nva.nvi.index.aws.OpenSearchClientFactory;
import no.sikt.nva.nvi.index.model.document.NviCandidateIndexDocument;
import no.sikt.nva.nvi.index.model.report.InstitutionReportHeader;
import no.sikt.nva.nvi.index.report.query.AllInstitutionsQuery;
import no.sikt.nva.nvi.index.report.query.InstitutionQuery;
import no.sikt.nva.nvi.index.report.query.ReportAggregationQuery;
import no.sikt.nva.nvi.index.report.query.XlsxReportQuery;
import no.sikt.nva.nvi.index.xlsx.FastExcelXlsxGenerator;
import nva.commons.core.JacocoGenerated;
import org.opensearch.client.opensearch.OpenSearchClient;
import org.opensearch.client.opensearch._types.query_dsl.Query;
import org.opensearch.client.opensearch.core.ClearScrollRequest;
import org.opensearch.client.opensearch.core.ScrollRequest;
import org.opensearch.client.opensearch.core.SearchRequest;
import org.opensearch.client.opensearch.core.SearchResponse;
import org.opensearch.client.opensearch.core.search.Hit;
import org.opensearch.client.opensearch.core.search.HitsMetadata;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class ReportAggregationClient {

  private static final String SCROLL_TIMEOUT = "1m";
  private static final int SCROLL_PAGE_SIZE = 300;
  private static final Logger LOGGER = LoggerFactory.getLogger(ReportAggregationClient.class);
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
    var data =
        fetchCandidates(institutionQuery.query()).stream()
            .map(
                candidate -> candidate.toReportRowsForInstitution(institutionQuery.institutionId()))
            .flatMap(this::orderByHeaderOrder)
            .toList();
    return new FastExcelXlsxGenerator(InstitutionReportHeader.getOrderedValues(), data)
        .toBase64EncodedString();
  }

  private String createXlsxReportForAllInstitutions(AllInstitutionsQuery query) {
    var data = fetchCandidates(query.query()).stream().flatMap(this::toReportRows).toList();

    return new FastExcelXlsxGenerator(InstitutionReportHeader.getOrderedValues(), data)
        .toBase64EncodedString();
  }

  private Stream<List<String>> toReportRows(NviCandidateIndexDocument candidate) {
    return candidate.approvals().stream()
        .map(approval -> candidate.toReportRowsForInstitution(approval.institutionId()))
        .flatMap(this::orderByHeaderOrder);
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

  private Stream<List<String>> orderByHeaderOrder(
      List<Map<InstitutionReportHeader, String>> reportRows) {
    return reportRows.stream().map(ReportAggregationClient::sortValuesByHeaderOrder);
  }

  private static List<String> sortValuesByHeaderOrder(
      Map<InstitutionReportHeader, String> keyValueMap) {
    return keyValueMap.entrySet().stream()
        .sorted(Comparator.comparing(entry -> entry.getKey().getOrder()))
        .map(Entry::getValue)
        .toList();
  }

  private List<NviCandidateIndexDocument> fetchCandidates(Query query) {
    var candidates = new ArrayList<NviCandidateIndexDocument>();
    var scrollId = initializeScroll(candidates, query);
    try {
      scrollId = fetchRemainingPages(candidates, scrollId);
    } finally {
      clearScroll(scrollId);
    }
    return candidates;
  }

  private String initializeScroll(List<NviCandidateIndexDocument> candidates, Query query) {
    var request =
        new SearchRequest.Builder()
            .index(NVI_CANDIDATES_INDEX)
            .size(SCROLL_PAGE_SIZE)
            .query(query)
            .scroll(builder -> builder.time(SCROLL_TIMEOUT))
            .build();
    var response = searchWithScroll(request);
    addHitsToListOfCandidates(response.hits(), candidates);
    return response.scrollId();
  }

  private SearchResponse<NviCandidateIndexDocument> searchWithScroll(SearchRequest request) {
    return attempt(() -> client.search(request, NviCandidateIndexDocument.class)).orElseThrow();
  }

  private String fetchRemainingPages(List<NviCandidateIndexDocument> candidates, String scrollId) {
    var scrollResponse = scroll(scrollId);
    var hits = scrollResponse.hits();
    if (hits.hits().isEmpty()) {
      return scrollId;
    }
    addHitsToListOfCandidates(hits, candidates);
    return fetchRemainingPages(candidates, scrollResponse.scrollId());
  }

  private SearchResponse<NviCandidateIndexDocument> scroll(String scrollId) {
    var request =
        ScrollRequest.of(
            builder -> builder.scrollId(scrollId).scroll(build -> build.time(SCROLL_TIMEOUT)));
    return attempt(() -> client.scroll(request, NviCandidateIndexDocument.class)).orElseThrow();
  }

  private void clearScroll(String scrollId) {
    if (nonNull(scrollId)) {
      attempt(
          () -> client.clearScroll(ClearScrollRequest.of(builder -> builder.scrollId(scrollId))));
    }
  }

  private static void addHitsToListOfCandidates(
      HitsMetadata<NviCandidateIndexDocument> hits,
      List<NviCandidateIndexDocument> fetchedCandidates) {
    hits.hits().stream().map(Hit::source).forEach(fetchedCandidates::add);
  }
}
