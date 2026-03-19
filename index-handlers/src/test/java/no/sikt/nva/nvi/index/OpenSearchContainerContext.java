package no.sikt.nva.nvi.index;

import java.io.IOException;
import java.net.URI;
import java.util.Collection;
import java.util.List;
import no.sikt.nva.nvi.index.aws.CandidateSearchClient;
import no.sikt.nva.nvi.index.aws.OpenSearchClientFactory;
import no.sikt.nva.nvi.index.model.document.NviCandidateIndexDocument;
import no.sikt.nva.nvi.index.report.ReportAggregationClient;
import no.sikt.nva.nvi.index.report.ReportDocumentClient;
import org.apache.hc.core5.http.HttpHost;
import org.opensearch.client.opensearch._types.OpenSearchException;
import org.opensearch.testcontainers.OpenSearchContainer;
import org.picocontainer.Startable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class OpenSearchContainerContext implements Startable {
  private static final String OPEN_SEARCH_IMAGE = "opensearchproject/opensearch:2.11.1";
  private static final OpenSearchContainer<?> container =
      new OpenSearchContainer<>(OPEN_SEARCH_IMAGE);
  private static CandidateSearchClient searchClient;
  private static ReportAggregationClient reportAggregationClient;
  private static ReportDocumentClient reportDocumentClient;
  private final Logger logger = LoggerFactory.getLogger(OpenSearchContainerContext.class);

  @Override
  public void start() {
    container.start();
    var httpHost = HttpHost.create(URI.create(container.getHttpHostAddress()));
    var fakeJwtProvider = FakeCachedJwtProvider.setup();
    var nativeClient = OpenSearchClientFactory.createClient(httpHost, fakeJwtProvider);
    searchClient = new CandidateSearchClient(nativeClient);
    reportAggregationClient = new ReportAggregationClient(nativeClient);
    reportDocumentClient = new ReportDocumentClient(nativeClient);
  }

  @Override
  public void stop() {
    container.stop();
  }

  public void createIndex() {
    searchClient.createIndex();
  }

  public void deleteIndex() {
    try {
      searchClient.deleteIndex();
    } catch (OpenSearchException | IOException e) {
      logger.warn("Could not delete index: {}", e.getMessage());
    }
  }

  /**
   * Refreshes all indices to make sure that new documents are searchable before tests are executed.
   */
  public void refreshIndex() {
    searchClient.refreshIndex();
  }

  public CandidateSearchClient getOpenSearchClient() {
    return searchClient;
  }

  public ReportAggregationClient getReportAggregationClient() {
    return reportAggregationClient;
  }

  public ReportDocumentClient getReportDocumentClient() {
    return reportDocumentClient;
  }

  public void addDocumentsToIndex(Collection<NviCandidateIndexDocument> documents) {
    documents.forEach(searchClient::addDocumentToIndex);
    refreshIndex();
  }

  public void addDocumentsToIndex(NviCandidateIndexDocument... documents) {
    addDocumentsToIndex(List.of(documents));
  }
}
