package no.sikt.nva.nvi.rest.fetch;

import static com.google.common.net.MediaType.JSON_UTF_8;
import static nva.commons.apigateway.MediaTypes.APPLICATION_JSON_LD;
import static nva.commons.core.attempt.Try.attempt;

import com.amazonaws.services.lambda.runtime.Context;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.common.net.MediaType;
import java.net.HttpURLConnection;
import java.util.List;
import no.sikt.nva.nvi.common.service.model.Candidate;
import no.unit.nva.commons.json.JsonUtils;
import nva.commons.apigateway.ApiGatewayHandler;
import nva.commons.apigateway.RequestInfo;
import nva.commons.apigateway.exceptions.ApiGatewayException;
import nva.commons.core.Environment;
import nva.commons.core.JacocoGenerated;

public class FetchNviCandidateContextHandler extends ApiGatewayHandler<Void, String> {

  public static final ObjectMapper MAPPER = JsonUtils.dtoObjectMapper;
  public static final String CONTEXT_PROPERTY = "@context";

  @JacocoGenerated
  public FetchNviCandidateContextHandler() {
    this(new Environment());
  }

  public FetchNviCandidateContextHandler(Environment environment) {
    super(Void.class, environment);
  }

  @Override
  protected List<MediaType> listSupportedMediaTypes() {
    return List.of(APPLICATION_JSON_LD, JSON_UTF_8);
  }

  @Override
  protected void validateRequest(Void unused, RequestInfo requestInfo, Context context)
      throws ApiGatewayException {}

  @Override
  protected String processInput(Void input, RequestInfo requestInfo, Context context)
      throws ApiGatewayException {
    return generateContextString();
  }

  @Override
  protected Integer getSuccessStatusCode(Void input, String output) {
    return HttpURLConnection.HTTP_OK;
  }

  private static String generateContextString() {
    var contextNode = MAPPER.createObjectNode();
    var contextValue = attempt(() -> MAPPER.readTree(Candidate.getJsonLdContext())).orElseThrow();
    contextNode.set(CONTEXT_PROPERTY, contextValue);
    return attempt(() -> MAPPER.writeValueAsString(contextNode)).orElseThrow();
  }
}
