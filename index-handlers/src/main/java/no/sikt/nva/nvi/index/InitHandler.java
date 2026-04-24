package no.sikt.nva.nvi.index;

import static no.unit.nva.commons.json.JsonUtils.dtoObjectMapper;

import com.amazonaws.services.lambda.runtime.Context;
import com.amazonaws.services.lambda.runtime.RequestHandler;
import com.fasterxml.jackson.core.JsonProcessingException;
import java.util.Map;
import java.util.function.Function;
import no.sikt.nva.nvi.index.aws.CandidateSearchClient;
import no.sikt.nva.nvi.index.aws.OpenSearchClientFactory;
import no.sikt.nva.nvi.index.model.IndexManagementRequest;
import nva.commons.core.JacocoGenerated;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class InitHandler implements RequestHandler<Object, String> {
  public static final String SUCCESS = "SUCCESS";
  private static final Logger LOGGER = LoggerFactory.getLogger(InitHandler.class);
  private final Function<String, CandidateSearchClient> clientFactory;

  @JacocoGenerated
  public InitHandler() {
    var openSearchClient = OpenSearchClientFactory.createAuthenticatedClient();
    this.clientFactory = indexName -> CandidateSearchClient.forIndex(openSearchClient, indexName);
  }

  public InitHandler(Function<String, CandidateSearchClient> clientFactory) {
    this.clientFactory = clientFactory;
  }

  @Override
  public String handleRequest(Object input, Context context) {
    var request = parseRequest(input);
    var targetIndexName = request.resolvedIndexName();
    var targetClient = clientFactory.apply(targetIndexName);
    if (!targetClient.indexExists()) {
      LOGGER.info("Creating index: {}", targetIndexName);
      targetClient.createIndex();
    } else {
      LOGGER.info("Index already exists: {}", targetIndexName);
    }
    return SUCCESS;
  }

  private static IndexManagementRequest parseRequest(Object input) {
    try {
      if (input instanceof Map) {
        return dtoObjectMapper.convertValue(input, IndexManagementRequest.class);
      }
      if (input instanceof String stringInput) {
        return dtoObjectMapper.readValue(stringInput, IndexManagementRequest.class);
      }
    } catch (IllegalArgumentException | JsonProcessingException e) {
      LOGGER.info("Could not parse input as IndexManagementRequest, using defaults");
    }
    return new IndexManagementRequest(null);
  }
}
