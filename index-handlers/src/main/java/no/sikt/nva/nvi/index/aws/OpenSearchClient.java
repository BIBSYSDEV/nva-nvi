package no.sikt.nva.nvi.index.aws;

import static no.sikt.nva.nvi.index.utils.SearchConstants.MAPPINGS;
import static no.sikt.nva.nvi.index.utils.SearchConstants.NVI_CANDIDATES_INDEX;
import static nva.commons.core.attempt.Try.attempt;

import java.io.IOException;
import java.util.List;
import java.util.UUID;
import java.util.function.Function;
import no.sikt.nva.nvi.index.model.document.NviCandidateIndexDocument;
import no.sikt.nva.nvi.index.model.search.CandidateSearchParameters;
import no.sikt.nva.nvi.index.model.search.SearchResultParameters;
import no.sikt.nva.nvi.index.query.Aggregations;
import no.sikt.nva.nvi.index.utils.SearchConstants;
import nva.commons.core.JacocoGenerated;
import org.opensearch.client.opensearch._types.FieldSort;
import org.opensearch.client.opensearch._types.FieldSort.Builder;
import org.opensearch.client.opensearch._types.OpenSearchException;
import org.opensearch.client.opensearch._types.SortOptions;
import org.opensearch.client.opensearch._types.SortOrder;
import org.opensearch.client.opensearch.core.DeleteRequest;
import org.opensearch.client.opensearch.core.DeleteResponse;
import org.opensearch.client.opensearch.core.IndexRequest;
import org.opensearch.client.opensearch.core.IndexResponse;
import org.opensearch.client.opensearch.core.SearchRequest;
import org.opensearch.client.opensearch.core.SearchResponse;
import org.opensearch.client.opensearch.core.search.SourceConfig;
import org.opensearch.client.opensearch.core.search.SourceFilter;
import org.opensearch.client.opensearch.indices.CreateIndexRequest;
import org.opensearch.client.opensearch.indices.DeleteIndexRequest;
import org.opensearch.client.opensearch.indices.GetIndexRequest;
import org.opensearch.client.opensearch.indices.RefreshRequest;
import org.opensearch.client.util.ObjectBuilder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@JacocoGenerated
public class OpenSearchClient implements SearchClient<NviCandidateIndexDocument> {

  private static final String INDEX_NOT_FOUND_EXCEPTION = "index_not_found_exception";
  private static final Logger LOGGER = LoggerFactory.getLogger(OpenSearchClient.class);
  private static final String ERROR_MSG_CREATE_INDEX =
      "Error while creating index: " + NVI_CANDIDATES_INDEX;
  private static final int MAX_QUERY_SIZE = 150;
  private final org.opensearch.client.opensearch.OpenSearchClient client;

  public OpenSearchClient(org.opensearch.client.opensearch.OpenSearchClient client) {
    this.client = client;
  }

  public static OpenSearchClient defaultOpenSearchClient() {
    return new OpenSearchClient(OpenSearchClientFactory.createAuthenticatedClient());
  }

  @Override
  public IndexResponse addDocumentToIndex(NviCandidateIndexDocument indexDocument) {
    return attempt(() -> client.index(constructIndexRequest(indexDocument)))
        .map(
            indexResponse -> {
              LOGGER.info("Indexed document from index: {}", indexDocument.identifier());
              return indexResponse;
            })
        .orElseThrow(
            failure ->
                handleFailure("Failed to add/update document from index", failure.getException()));
  }

  @Override
  public DeleteResponse removeDocumentFromIndex(UUID identifier) {
    return attempt(() -> client.delete(contructDeleteRequest(identifier)))
        .map(
            deleteResponse -> {
              LOGGER.info("Removing document from index: {}", identifier);
              return deleteResponse;
            })
        .orElseThrow(
            failure ->
                handleFailure("Failed to remove document from index", failure.getException()));
  }

  @Override
  public SearchResponse<NviCandidateIndexDocument> search(
      CandidateSearchParameters candidateSearchParameters) throws IOException {
    var query = constructSearchRequest(candidateSearchParameters);
    logQueryDetails(query);
    return client.search(query, NviCandidateIndexDocument.class);
  }

  private void logQueryDetails(SearchRequest query) {
    var queryString = query.toJsonString();
    var estimatedQueryComplexity = (int) queryString.chars().filter(ch -> '{' == ch).count();
    if (estimatedQueryComplexity > MAX_QUERY_SIZE) {
      LOGGER.warn(
          "Query complexity ({} nested objects) exceeds recommended limit of {}."
              + "Consider simplifying query structure",
          estimatedQueryComplexity,
          MAX_QUERY_SIZE);
    }
    LOGGER.debug("Executing query with {} nested objects", estimatedQueryComplexity);
    LOGGER.trace("Executing query: {}", queryString);
  }

  @Override
  public void deleteIndex() throws IOException {
    client.indices().delete(new DeleteIndexRequest.Builder().index(NVI_CANDIDATES_INDEX).build());
  }

  public boolean indexExists() {
    try {
      client.indices().get(GetIndexRequest.of(request -> request.index(NVI_CANDIDATES_INDEX)));
    } catch (IOException io) {
      throw new RuntimeException(io);
    } catch (OpenSearchException osex) {
      if (osex.status() == 404 && INDEX_NOT_FOUND_EXCEPTION.equals(osex.error().type())) {
        return false;
      }
      throw osex;
    }
    return true;
  }

  public void createIndex() {
    attempt(() -> client.indices().create(getCreateIndexRequest()))
        .orElseThrow(failure -> handleFailure(ERROR_MSG_CREATE_INDEX, failure.getException()));
  }

  public void refreshIndex() {
    attempt(() -> client.indices().refresh(new RefreshRequest.Builder().build()))
        .orElseThrow(failure -> handleFailure("Failed to refresh index", failure.getException()));
  }

  private static DeleteRequest contructDeleteRequest(UUID identifier) {
    return new DeleteRequest.Builder()
        .index(NVI_CANDIDATES_INDEX)
        .id(identifier.toString())
        .build();
  }

  private static IndexRequest<NviCandidateIndexDocument> constructIndexRequest(
      NviCandidateIndexDocument indexDocument) {
    return new IndexRequest.Builder<NviCandidateIndexDocument>()
        .index(NVI_CANDIDATES_INDEX)
        .id(indexDocument.identifier().toString())
        .document(indexDocument)
        .build();
  }

  private static CreateIndexRequest getCreateIndexRequest() {
    return new CreateIndexRequest.Builder().mappings(MAPPINGS).index(NVI_CANDIDATES_INDEX).build();
  }

  private static SortOptions getSortOptions(CandidateSearchParameters parameters) {
    var resultParameters = parameters.searchResultParameters();
    return new SortOptions.Builder()
        .field(FieldSort.of(fieldSortBuilderFunction(resultParameters)))
        .build();
  }

  private static Function<Builder, ObjectBuilder<FieldSort>> fieldSortBuilderFunction(
      SearchResultParameters resultParameters) {
    return builder ->
        builder.field(resultParameters.orderBy()).order(getSortOrder(resultParameters.sortOrder()));
  }

  private static SourceConfig getSourceConfigWithExcludedFields(
      CandidateSearchParameters parameters) {
    var filterBuilderFunction = getFilterBuilderFunction(parameters.excludeFields());
    return SourceConfig.of(
        sourceConfigBuilder -> sourceConfigBuilder.filter(filterBuilderFunction));
  }

  private static Function<SourceFilter.Builder, ObjectBuilder<SourceFilter>>
      getFilterBuilderFunction(List<String> excludeFields) {
    return filterBuilder -> filterBuilder.excludes(excludeFields);
  }

  private static SortOrder getSortOrder(String sortOrder) {
    return SortOrder.Asc.jsonValue().equalsIgnoreCase(sortOrder) ? SortOrder.Asc : SortOrder.Desc;
  }

  private RuntimeException handleFailure(String msg, Exception exception) {
    LOGGER.error(msg, exception);
    return new RuntimeException(exception.getMessage());
  }

  private SearchRequest constructSearchRequest(CandidateSearchParameters parameters) {
    var query = SearchConstants.constructQuery(parameters);
    var resultParameters = parameters.searchResultParameters();
    var sortOptions = getSortOptions(parameters);
    var sourceConfig = getSourceConfigWithExcludedFields(parameters);
    return new SearchRequest.Builder()
        .index(NVI_CANDIDATES_INDEX)
        .query(query)
        .sort(sortOptions)
        .aggregations(
            Aggregations.generateAggregations(
                parameters.aggregationType(),
                parameters.username(),
                parameters.topLevelOrgUriAsString()))
        .from(resultParameters.offset())
        .size(resultParameters.size())
        .source(sourceConfig)
        .build();
  }
}
