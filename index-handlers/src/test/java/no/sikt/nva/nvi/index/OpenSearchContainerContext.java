package no.sikt.nva.nvi.index;

import static no.sikt.nva.nvi.common.EnvironmentFixtures.SEARCH_INFRASTRUCTURE_API_HOST;
import static no.sikt.nva.nvi.common.EnvironmentFixtures.getDefaultEnvironmentBuilder;
import static no.sikt.nva.nvi.index.utils.SearchConstants.getSearchIndexName;
import static no.sikt.nva.nvi.index.utils.SearchConstants.getSearchInfrastructureApiHost;

import java.io.IOException;
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
import org.testcontainers.utility.DockerImageName;

public class OpenSearchContainerContext implements Startable {

  private static final Logger LOGGER = LoggerFactory.getLogger(OpenSearchContainerContext.class);
  private static final DockerImageName OPEN_SEARCH_IMAGE =
      DockerImageName.parse("opensearchproject/opensearch:3.5.0");
  private static final OpenSearchContainer<?> CONTAINER =
      new OpenSearchContainer<>(OPEN_SEARCH_IMAGE);
  private static CandidateSearchClient searchClient;
  private static ReportAggregationClient reportAggregationClient;
  private static ReportDocumentClient reportDocumentClient;

  @Override
  public void start() {
    CONTAINER.start();
    var environment =
        getDefaultEnvironmentBuilder()
            .with(SEARCH_INFRASTRUCTURE_API_HOST.getKey(), CONTAINER.getHttpHostAddress())
            .build();
    var httpHost = HttpHost.create(getSearchInfrastructureApiHost(environment));
    var fakeJwtProvider = FakeCachedJwtProvider.setup();
    var nativeClient = OpenSearchClientFactory.createClient(httpHost, fakeJwtProvider);
    var indexName = getSearchIndexName(environment);
    searchClient = new CandidateSearchClient(nativeClient, indexName, indexName);
    reportAggregationClient = new ReportAggregationClient(nativeClient, indexName);
    reportDocumentClient = new ReportDocumentClient(nativeClient, indexName);
  }

  @Override
  public void stop() {
    CONTAINER.stop();
  }

  public void createIndex() {
    searchClient.createIndex();
  }

  public void deleteIndex() {
    try {
      searchClient.deleteIndex();
    } catch (OpenSearchException | IOException e) {
      LOGGER.warn("Could not delete index: {}", e.getMessage());
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
