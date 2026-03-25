package no.sikt.nva.nvi.index;

import static java.util.Objects.isNull;
import static no.unit.nva.commons.json.JsonUtils.dtoObjectMapper;

import com.amazonaws.services.lambda.runtime.Context;
import com.amazonaws.services.lambda.runtime.RequestHandler;
import java.io.IOException;
import java.util.Map;
import no.sikt.nva.nvi.index.aws.CandidateSearchClient;
import no.sikt.nva.nvi.index.model.AliasManagementRequest;
import nva.commons.core.JacocoGenerated;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class ManageIndexAliasHandler implements RequestHandler<Object, String> {

  public static final String SUCCESS = "SUCCESS";
  public static final String MISSING_REQUIRED_FIELDS =
      "Both 'aliasName' and 'targetIndex' are required";
  private static final Logger LOGGER = LoggerFactory.getLogger(ManageIndexAliasHandler.class);
  private final CandidateSearchClient searchClient;

  @JacocoGenerated
  public ManageIndexAliasHandler() {
    this(CandidateSearchClient.defaultOpenSearchClient());
  }

  public ManageIndexAliasHandler(CandidateSearchClient searchClient) {
    this.searchClient = searchClient;
  }

  @Override
  public String handleRequest(Object input, Context context) {
    var request = parseRequest(input);
    if (isNull(request.aliasName()) || isNull(request.targetIndex())) {
      throw new IllegalArgumentException(MISSING_REQUIRED_FIELDS);
    }
    try {
      searchClient.updateAlias(request.aliasName(), request.targetIndex());
    } catch (IOException e) {
      LOGGER.error(
          "Failed to update alias '{}' to '{}'", request.aliasName(), request.targetIndex());
      throw new RuntimeException(e);
    }
    return SUCCESS;
  }

  @SuppressWarnings("unchecked")
  private static AliasManagementRequest parseRequest(Object input) {
    try {
      if (input instanceof Map) {
        return dtoObjectMapper.convertValue(input, AliasManagementRequest.class);
      }
      if (input instanceof String stringInput) {
        return dtoObjectMapper.readValue(stringInput, AliasManagementRequest.class);
      }
    } catch (Exception e) {
      throw new IllegalArgumentException("Invalid request format", e);
    }
    throw new IllegalArgumentException("Invalid request format");
  }
}
