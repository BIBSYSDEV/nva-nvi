package no.sikt.nva.nvi.index.report;

import static no.sikt.nva.nvi.index.utils.SearchConstants.getSearchIndexName;

import java.io.IOException;
import no.sikt.nva.nvi.index.aws.OpenSearchClientFactory;
import no.sikt.nva.nvi.index.report.query.ReportAggregationQuery;
import nva.commons.core.Environment;
import nva.commons.core.JacocoGenerated;
import org.opensearch.client.opensearch.OpenSearchClient;
import org.opensearch.client.opensearch.core.SearchRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class ReportAggregationClient {

  private static final Logger LOGGER = LoggerFactory.getLogger(ReportAggregationClient.class);
  private final OpenSearchClient client;
  private final String indexName;

  public ReportAggregationClient(OpenSearchClient client, String indexName) {
    this.client = client;
    this.indexName = indexName;
  }

  @JacocoGenerated
  public static ReportAggregationClient defaultClient() {
    return new ReportAggregationClient(
        OpenSearchClientFactory.createAuthenticatedClient(new Environment()),
        getSearchIndexName(new Environment()));
  }

  public <T> T executeQuery(ReportAggregationQuery<T> query) throws IOException {
    LOGGER.info("Executing query: {}", query);
    return processQuery(query);
  }

  private <T> T processQuery(ReportAggregationQuery<T> query) throws IOException {
    var searchRequest =
        new SearchRequest.Builder()
            .index(indexName)
            .size(0)
            .query(query.query())
            .aggregations(query.aggregations())
            .build();
    var response = client.search(searchRequest, Void.class);
    return query.parseResponse(response);
  }
}
