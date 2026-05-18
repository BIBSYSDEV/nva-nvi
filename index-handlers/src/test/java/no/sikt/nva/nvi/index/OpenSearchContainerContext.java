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
  /**
   * Lock name to serialize tests that touch the shared OpenSearch index. Use with
   * {@code @ResourceLock(OpenSearchContainerContext.INDEX_RESOURCE_LOCK)} on classes that call
   * {@link #createIndex()}/{@link #deleteIndex()} or otherwise depend on index contents.
   */
  public static final String INDEX_RESOURCE_LOCK = "opensearch-index";

  private static final String OPEN_SEARCH_IMAGE = "opensearchproject/opensearch:3.5.0";
  private static final OpenSearchContainer<?> CONTAINER;
  private static final CandidateSearchClient SEARCH_CLIENT;
  private static final ReportAggregationClient REPORT_AGGREGATION_CLIENT;
  private static final ReportDocumentClient REPORT_DOCUMENT_CLIENT;
  private final Logger logger = LoggerFactory.getLogger(OpenSearchContainerContext.class);

  static {
    CONTAINER = new OpenSearchContainer<>(OPEN_SEARCH_IMAGE);
    CONTAINER.start();
    var httpHost = HttpHost.create(URI.create(CONTAINER.getHttpHostAddress()));
    var fakeJwtProvider = FakeCachedJwtProvider.setup();
    var nativeClient = OpenSearchClientFactory.createClient(httpHost, fakeJwtProvider);
    SEARCH_CLIENT = new CandidateSearchClient(nativeClient);
    REPORT_AGGREGATION_CLIENT = new ReportAggregationClient(nativeClient);
    REPORT_DOCUMENT_CLIENT = new ReportDocumentClient(nativeClient);
  }

  @Override
  public void start() {
    // No-op: container is started exactly once via static initializer and reused for the JVM
    // lifetime. The test classes that share this container must serialize their access to the
    // shared index via @ResourceLock("opensearch-index").
  }

  @Override
  public void stop() {
    // No-op: container outlives any single test class; Testcontainers reaps it on JVM shutdown.
  }

  public void createIndex() {
    if (SEARCH_CLIENT.indexExists()) {
      // Cluster-state propagation after a prior deleteIndex() can briefly outlive the HTTP
      // response, so we clear leftover state before creating to keep concurrent test classes
      // robust against the lag (in addition to the @ResourceLock serialization).
      deleteIndex();
    }
    SEARCH_CLIENT.createIndex();
  }

  public void deleteIndex() {
    try {
      SEARCH_CLIENT.deleteIndex();
    } catch (OpenSearchException | IOException e) {
      logger.warn("Could not delete index: {}", e.getMessage());
    }
  }

  /**
   * Refreshes all indices to make sure that new documents are searchable before tests are executed.
   */
  public void refreshIndex() {
    SEARCH_CLIENT.refreshIndex();
  }

  public CandidateSearchClient getOpenSearchClient() {
    return SEARCH_CLIENT;
  }

  public ReportAggregationClient getReportAggregationClient() {
    return REPORT_AGGREGATION_CLIENT;
  }

  public ReportDocumentClient getReportDocumentClient() {
    return REPORT_DOCUMENT_CLIENT;
  }

  public void addDocumentsToIndex(Collection<NviCandidateIndexDocument> documents) {
    documents.forEach(SEARCH_CLIENT::addDocumentToIndex);
    refreshIndex();
  }

  public void addDocumentsToIndex(NviCandidateIndexDocument... documents) {
    addDocumentsToIndex(List.of(documents));
  }
}
