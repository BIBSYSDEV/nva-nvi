package no.sikt.nva.nvi.index.apigateway;

import static no.sikt.nva.nvi.common.EnvironmentFixtures.getSearchNviCandidatesHandlerEnvironment;
import static no.sikt.nva.nvi.index.IndexDocumentFixtures.SEARCH_RESULT_TYPE;
import static no.unit.nva.testutils.RandomDataGenerator.objectMapper;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.amazonaws.services.lambda.runtime.Context;
import com.fasterxml.jackson.core.JsonProcessingException;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.util.List;
import java.util.Map;
import no.sikt.nva.nvi.common.validator.FakeViewingScopeValidator;
import no.sikt.nva.nvi.common.validator.ViewingScopeValidator;
import no.sikt.nva.nvi.index.aws.SearchClient;
import no.sikt.nva.nvi.index.model.document.NviCandidateIndexDocument;
import no.unit.nva.clients.IdentityServiceClient;
import no.unit.nva.clients.UserDto;
import no.unit.nva.clients.UserDto.ViewingScope;
import no.unit.nva.commons.json.JsonUtils;
import no.unit.nva.commons.pagination.PaginatedSearchResult;
import no.unit.nva.stubs.FakeContext;
import no.unit.nva.testutils.HandlerRequestBuilder;
import nva.commons.apigateway.AccessRight;
import nva.commons.apigateway.GatewayResponse;
import nva.commons.apigateway.exceptions.NotFoundException;
import nva.commons.core.Environment;
import org.zalando.problem.Problem;

public class SearchNviCandidatesHandlerTestBase {

  private static final Environment ENVIRONMENT = getSearchNviCandidatesHandlerEnvironment();
  private static final Context CONTEXT = new FakeContext();
  protected static final IdentityServiceClient identityServiceClient =
      mock(IdentityServiceClient.class);
  protected ByteArrayOutputStream output;
  protected SearchNviCandidatesHandler handler;
  protected String currentUsername;
  protected URI currentOrganization;
  protected AccessRight currentAccessRight;

  protected void createHandler(SearchClient<NviCandidateIndexDocument> openSearchClient) {
    var viewingScopeValidator = new FakeViewingScopeValidator(true);
    createHandler(openSearchClient, viewingScopeValidator);
  }

  protected void createHandler(
      SearchClient<NviCandidateIndexDocument> openSearchClient,
      ViewingScopeValidator viewingScopeValidator) {
    output = new ByteArrayOutputStream();
    handler =
        new SearchNviCandidatesHandler(
            openSearchClient, viewingScopeValidator, identityServiceClient, ENVIRONMENT);
  }

  protected PaginatedSearchResult<NviCandidateIndexDocument> handleRequest(
      Map<String, String> queryParams) {
    var request = createRequest(queryParams);
    try {
      handler.handleRequest(request, output, CONTEXT);
      var response = GatewayResponse.fromOutputStream(output, PaginatedSearchResult.class);
      output.reset();
      return objectMapper.readValue(response.getBody(), SEARCH_RESULT_TYPE);
    } catch (IOException e) {
      throw new RuntimeException(e);
    }
  }

  protected Problem handleBadRequest(Map<String, String> queryParams) {
    try {
      var request = createRequest(queryParams);
      handler.handleRequest(request, output, CONTEXT);
      var response = GatewayResponse.fromOutputStream(output, Problem.class);
      return objectMapper.readValue(response.getBody(), Problem.class);
    } catch (IOException e) {
      throw new RuntimeException(e);
    }
  }

  protected InputStream createRequest(Map<String, String> queryParams) {
    try {
      return new HandlerRequestBuilder<Void>(JsonUtils.dtoObjectMapper)
          .withTopLevelCristinOrgId(currentOrganization)
          .withAccessRights(currentOrganization, currentAccessRight)
          .withCurrentCustomer(currentOrganization)
          .withUserName(currentUsername)
          .withQueryParameters(queryParams)
          .build();
    } catch (JsonProcessingException e) {
      throw new RuntimeException(e);
    }
  }

  protected static void mockIdentityService(String username, URI... organizations) {
    try {
      when(identityServiceClient.getUser(username))
          .thenReturn(buildGetUserResponse(List.of(organizations)));
    } catch (NotFoundException e) {
      throw new RuntimeException(e);
    }
  }

  protected static UserDto buildGetUserResponse(List<URI> usersViewingScopeIncludedUnits) {
    return UserDto.builder()
        .withViewingScope(buildViewingScope(usersViewingScopeIncludedUnits))
        .build();
  }

  protected static ViewingScope buildViewingScope(List<URI> includedUnits) {
    return ViewingScope.builder().withIncludedUnits(includedUnits).build();
  }
}
