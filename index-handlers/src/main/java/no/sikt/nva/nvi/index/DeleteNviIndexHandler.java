package no.sikt.nva.nvi.index;

import static no.unit.nva.commons.json.JsonUtils.dtoObjectMapper;

import com.amazonaws.services.lambda.runtime.Context;
import com.amazonaws.services.lambda.runtime.RequestHandler;
import java.io.IOException;
import java.util.Map;
import java.util.function.Function;
import no.sikt.nva.nvi.index.aws.CandidateSearchClient;
import no.sikt.nva.nvi.index.aws.OpenSearchClientFactory;
import no.sikt.nva.nvi.index.aws.SearchClient;
import no.sikt.nva.nvi.index.model.IndexManagementRequest;
import no.sikt.nva.nvi.index.model.document.NviCandidateIndexDocument;
import nva.commons.core.JacocoGenerated;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class DeleteNviIndexHandler implements RequestHandler<Object, String> {

  public static final String FINISHED = "FINISHED";
  public static final String INDEX_DELETION_FAILED_MESSAGE = "Index deletion failed";
  private static final Logger LOGGER = LoggerFactory.getLogger(DeleteNviIndexHandler.class);
  private final Function<String, SearchClient<NviCandidateIndexDocument>> clientFactory;

  @JacocoGenerated
  public DeleteNviIndexHandler() {
    var openSearchClient = OpenSearchClientFactory.createAuthenticatedClient();
    this.clientFactory = indexName -> CandidateSearchClient.forIndex(openSearchClient, indexName);
  }

  public DeleteNviIndexHandler(
      Function<String, SearchClient<NviCandidateIndexDocument>> clientFactory) {
    this.clientFactory = clientFactory;
  }

  @Override
  public String handleRequest(Object input, Context context) {
    var request = parseRequest(input);
    var targetIndexName = request.resolvedIndexName();
    var targetClient = clientFactory.apply(targetIndexName);
    try {
      targetClient.deleteIndex();
    } catch (IOException e) {
      LOGGER.error(INDEX_DELETION_FAILED_MESSAGE);
      throw new RuntimeException(e);
    }
    LOGGER.info("Deleted index: {}", targetIndexName);
    return FINISHED;
  }

  private static IndexManagementRequest parseRequest(Object input) {
    try {
      if (input instanceof Map) {
        return dtoObjectMapper.convertValue(input, IndexManagementRequest.class);
      }
      if (input instanceof String stringInput) {
        return dtoObjectMapper.readValue(stringInput, IndexManagementRequest.class);
      }
    } catch (Exception e) {
      LOGGER.info("Could not parse input as IndexManagementRequest, using defaults");
    }
    return new IndexManagementRequest(null);
  }
}
