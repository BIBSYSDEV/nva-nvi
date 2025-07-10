package no.sikt.nva.nvi.index;

import java.io.IOException;
import java.net.URISyntaxException;
import java.util.Collection;
import java.util.List;
import no.sikt.nva.nvi.index.aws.OpenSearchClient;
import no.sikt.nva.nvi.index.model.document.NviCandidateIndexDocument;
import org.apache.hc.core5.http.HttpHost;
import org.opensearch.client.Request;
import org.opensearch.client.RestClient;
import org.opensearch.client.opensearch._types.OpenSearchException;
import org.opensearch.testcontainers.OpenSearchContainer;
import org.picocontainer.Startable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class OpenSearchContainerContext implements Startable {
  private static final String OPEN_SEARCH_IMAGE = "opensearchproject/opensearch:2.11.1";
  private static final OpenSearchContainer<?> container =
      new OpenSearchContainer<>(OPEN_SEARCH_IMAGE);
  private static RestClient restClient;
  private static OpenSearchClient openSearchClient;
  private final Logger logger = LoggerFactory.getLogger(OpenSearchContainerContext.class);

  @Override
  public void start() {
    container.start();
      try {
          restClient = RestClient.builder(HttpHost.create(container.getHttpHostAddress())).build();
      } catch (URISyntaxException e) {
          throw new IllegalArgumentException(e);
      }
      openSearchClient = new OpenSearchClient(restClient, FakeCachedJwtProvider.setup());
  }

  @Override
  public void stop() {
    try {
      restClient.close();
    } catch (IOException e) {
      throw new RuntimeException(e);
    } finally {
      container.stop();
    }
  }

  public void createIndex() {
    openSearchClient.createIndex();
  }

  public void deleteIndex() {
    try {
      openSearchClient.deleteIndex();
    } catch (OpenSearchException | IOException e) {
      logger.warn("Could not delete index: {}", e.getMessage());
    }
  }

  /**
   * Refreshes all indices to make sure that new documents are searchable before tests are executed.
   */
  public void refreshIndex() {
    var refreshRequest = new Request("POST", "/_refresh");
    try {
      restClient.performRequest(refreshRequest);
    } catch (IOException e) {
      throw new RuntimeException(e);
    }
  }

  public OpenSearchClient getOpenSearchClient() {
    return openSearchClient;
  }

  public void addDocumentsToIndex(Collection<NviCandidateIndexDocument> documents) {
    documents.forEach(openSearchClient::addDocumentToIndex);
    refreshIndex();
  }

  public void addDocumentsToIndex(NviCandidateIndexDocument... documents) {
    addDocumentsToIndex(List.of(documents));
    refreshIndex();
  }
}
