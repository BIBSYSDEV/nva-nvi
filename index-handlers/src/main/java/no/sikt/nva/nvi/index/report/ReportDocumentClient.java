package no.sikt.nva.nvi.index.report;

import static java.util.Objects.nonNull;
import static no.sikt.nva.nvi.index.utils.SearchConstants.NVI_CANDIDATES_INDEX;
import static nva.commons.core.attempt.Try.attempt;

import java.util.ArrayList;
import java.util.List;
import no.sikt.nva.nvi.index.model.report.ReportDocument;
import org.opensearch.client.opensearch.OpenSearchClient;
import org.opensearch.client.opensearch._types.query_dsl.Query;
import org.opensearch.client.opensearch.core.ClearScrollRequest;
import org.opensearch.client.opensearch.core.ScrollRequest;
import org.opensearch.client.opensearch.core.SearchRequest;
import org.opensearch.client.opensearch.core.search.Hit;
import org.opensearch.client.opensearch.core.search.HitsMetadata;
import org.opensearch.client.opensearch.core.search.SourceConfig;

public class ReportDocumentClient {

  private static final String SCROLL_TIMEOUT = "1m";
  private static final int SCROLL_PAGE_SIZE = 1000;
  static final SourceConfig REPORT_SOURCE_CONFIG =
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
                                  "approvals.points",
                                  "approvals.sector",
                                  "approvals.rboInstitution"))
                          .excludes(
                              List.of(
                                  "publicationDetails.nviContributors.role",
                                  "publicationDetails.nviContributors.affiliations.partOfIdentifiers",
                                  "approvals.points.institutionId",
                                  "approvals.points.institutionPoints"))));

  private final OpenSearchClient client;

  public ReportDocumentClient(OpenSearchClient client) {
    this.client = client;
  }

  public List<ReportDocument> fetchDocuments(Query query) {
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
            .source(REPORT_SOURCE_CONFIG)
            .scroll(builder -> builder.time(SCROLL_TIMEOUT))
            .build();

    var response = attempt(() -> client.search(request, ReportDocument.class)).orElseThrow();
    addHits(response.hits(), documents);
    return response.scrollId();
  }

  private String fetchRemainingPages(List<ReportDocument> documents, String scrollId) {
    var request =
        ScrollRequest.of(
            builder -> builder.scrollId(scrollId).scroll(build -> build.time(SCROLL_TIMEOUT)));
    var response = attempt(() -> client.scroll(request, ReportDocument.class)).orElseThrow();
    var hits = response.hits();
    if (hits.hits().isEmpty()) {
      return scrollId;
    }
    addHits(hits, documents);
    return fetchRemainingPages(documents, response.scrollId());
  }

  private void clearScroll(String scrollId) {
    if (nonNull(scrollId)) {
      attempt(
          () -> client.clearScroll(ClearScrollRequest.of(builder -> builder.scrollId(scrollId))));
    }
  }

  private static void addHits(HitsMetadata<ReportDocument> hits, List<ReportDocument> documents) {
    hits.hits().stream().map(Hit::source).forEach(documents::add);
  }
}
