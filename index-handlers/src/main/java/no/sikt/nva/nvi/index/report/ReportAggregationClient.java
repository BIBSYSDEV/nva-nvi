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
import no.sikt.nva.nvi.index.model.report.InstitutionReportMapper;
import no.sikt.nva.nvi.index.model.report.InstitutionReportRow;
import no.sikt.nva.nvi.index.model.report.ReportDocument;
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

  public byte[] executeCsvReport(InstitutionQuery query) {
    LOGGER.info("Executing CSV report query: {}", query);
    var data =
        fetchCandidates(query.query()).stream()
            .map(candidate -> candidate.toReportRowsForInstitution(query.institutionId()))
            .flatMap(this::orderByHeaderOrder)
            .toList();
    return new CsvGenerator(InstitutionReportHeader.getOrderedValues(), data).toCsvBytes();
  }

  public byte[] executeCsvReport(AllInstitutionsQuery query) {
    LOGGER.info("Executing CSV report query: {}", query);
    var data =
        fetchCandidates(query.query()).stream()
            .flatMap(
                candidate ->
                    candidate.approvals().stream()
                        .map(
                            approval ->
                                candidate.toReportRowsForInstitution(approval.institutionId()))
                        .flatMap(this::orderByHeaderOrder))
            .toList();
    return new CsvGenerator(InstitutionReportHeader.getOrderedValues(), data).toCsvBytes();
  }

  public byte[] executeXlsxExport(AllInstitutionsQuery query) {
    LOGGER.info("Executing CSV report query: {}", query);
    var data =
        fetchCandidates(query.query()).stream()
            .flatMap(
                candidate ->
                    candidate.approvals().stream()
                        .map(
                            approval ->
                                candidate.toReportRowsForInstitution(approval.institutionId()))
                        .flatMap(this::orderByHeaderOrder))
            .toList();
    return new FastExcelXlsxGenerator(InstitutionReportHeader.getOrderedValues(), data)
        .toWorkbookByteArray();
  }

  public byte[] executeXlsxReport(InstitutionQuery query) {
    LOGGER.info("Executing XLSX report query: {}", query);
    var rows =
        fetchReportDocuments(query.query()).stream()
            .flatMap(document -> InstitutionReportMapper.mapToRows(document, query.institutionId()))
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
    var scrollId = initializeCandidateScroll(candidates, query);
    try {
      scrollId = fetchRemainingCandidatePages(candidates, scrollId);
    } finally {
      clearScroll(scrollId);
    }
    return candidates;
  }

  private String initializeCandidateScroll(
      List<NviCandidateIndexDocument> candidates, Query query) {
    var request =
        new SearchRequest.Builder()
            .index(NVI_CANDIDATES_INDEX)
            .size(SCROLL_PAGE_SIZE)
            .query(query)
            .scroll(builder -> builder.time(SCROLL_TIMEOUT))
            .build();
    var response =
        attempt(() -> client.search(request, NviCandidateIndexDocument.class)).orElseThrow();
    addHitsToListOfCandidates(response.hits(), candidates);
    return response.scrollId();
  }

  private String fetchRemainingCandidatePages(
      List<NviCandidateIndexDocument> candidates, String scrollId) {
    var request =
        ScrollRequest.of(
            builder -> builder.scrollId(scrollId).scroll(build -> build.time(SCROLL_TIMEOUT)));
    var scrollResponse =
        attempt(() -> client.scroll(request, NviCandidateIndexDocument.class)).orElseThrow();
    var hits = scrollResponse.hits();
    if (hits.hits().isEmpty()) {
      return scrollId;
    }
    addHitsToListOfCandidates(hits, candidates);
    return fetchRemainingCandidatePages(candidates, scrollResponse.scrollId());
  }

  private static void addHitsToListOfCandidates(
      HitsMetadata<NviCandidateIndexDocument> hits,
      List<NviCandidateIndexDocument> fetchedCandidates) {
    hits.hits().stream().map(Hit::source).forEach(fetchedCandidates::add);
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
