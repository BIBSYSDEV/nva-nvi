package no.sikt.nva.nvi.rest.fetch;

import static com.google.common.net.HttpHeaders.ACCEPT;
import static no.unit.nva.commons.json.JsonUtils.dtoObjectMapper;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.core.Is.is;
import static org.hamcrest.core.IsEqual.equalTo;
import static org.hamcrest.core.StringContains.containsString;

import com.amazonaws.services.lambda.runtime.Context;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.util.Map;
import java.util.stream.Stream;
import no.sikt.nva.nvi.common.service.model.Candidate;
import no.sikt.nva.nvi.rest.EnvironmentFixtures;
import no.unit.nva.stubs.FakeContext;
import no.unit.nva.testutils.HandlerRequestBuilder;
import nva.commons.apigateway.GatewayResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import org.zalando.problem.Problem;

class FetchNviCandidateContextHandlerTest {

  public static final ObjectMapper MAPPER = dtoObjectMapper;
  private static final String TEXT_ANY = "text/*";
  private static final String TEXT_HTML = "text/html";
  private static final String APPLICATION_XHTML = "application/xhtml+xml";
  private static final String APPLICATION_JSON_LD = "application/ld+json";
  private static final String APPLICATION_JSON = "application/json";
  private static final String DEFAULT_MEDIA_TYPE = "*/*";
  private static final String UNSUPPORTED_ACCEPT_HEADER_MESSAGE =
      "contains no supported Accept header values.";
  private final Context context = new FakeContext();
  private FetchNviCandidateContextHandler fetchNviCandidateContextHandler;
  private ByteArrayOutputStream output;

  @BeforeEach
  void setUp() {
    output = new ByteArrayOutputStream();
    fetchNviCandidateContextHandler =
        new FetchNviCandidateContextHandler(
            EnvironmentFixtures.FETCH_NVI_CANDIDATE_CONTEXT_HANDLER);
  }

  @Test
  void shouldReturnNviCandidateContextAsString() throws IOException {
    var request = generateHandlerRequest(Map.of(ACCEPT, APPLICATION_JSON));
    fetchNviCandidateContextHandler.handleRequest(request, output, context);
    var response = GatewayResponse.fromOutputStream(output, String.class);
    var expectedContext = "{\"@context\":" + Candidate.getJsonLdContext() + "}";
    var expected = formatJson(expectedContext);
    assertThat(response.getBody(), is(equalTo(expected)));
  }

  @ParameterizedTest(name = "mediaType {0} is invalid")
  @MethodSource("unsupportedMediaTypes")
  void shouldReturnUnsupportedMediaTypeIfRequestHeaderAcceptsAnythingOtherThanJsonOrJsonLdOrDefault(
      String mediaType) throws IOException {
    var request = generateHandlerRequest(Map.of(ACCEPT, mediaType));
    fetchNviCandidateContextHandler.handleRequest(request, output, context);
    var response = GatewayResponse.fromOutputStream(output, Problem.class);
    assertThat(response.getStatusCode(), is(equalTo(HttpURLConnection.HTTP_UNSUPPORTED_TYPE)));
    var problem = response.getBodyObject(Problem.class);
    assertThat(problem.getDetail(), is(containsString(UNSUPPORTED_ACCEPT_HEADER_MESSAGE)));
  }

  @ParameterizedTest(name = "mediaType {0} is valid")
  @MethodSource("supportedMediaTypes")
  void shouldReturnSuccessfulStatusCodeIfRequestHeaderAcceptsJsonOrJsonLdOrDefault(String mediaType)
      throws IOException {
    var request = generateHandlerRequest(Map.of(ACCEPT, mediaType));
    fetchNviCandidateContextHandler.handleRequest(request, output, context);
    var response = GatewayResponse.fromOutputStream(output, String.class);
    assertThat(response.getStatusCode(), is(equalTo(HttpURLConnection.HTTP_OK)));
  }

  private static String formatJson(String json) throws JsonProcessingException {
    return MAPPER.writeValueAsString(MAPPER.readTree(json));
  }

  private static Stream<String> unsupportedMediaTypes() {
    return Stream.of(TEXT_ANY, TEXT_HTML, APPLICATION_XHTML);
  }

  private static Stream<String> supportedMediaTypes() {
    return Stream.of(APPLICATION_JSON, APPLICATION_JSON_LD, DEFAULT_MEDIA_TYPE);
  }

  private InputStream generateHandlerRequest(Map<String, String> headers)
      throws JsonProcessingException {
    return new HandlerRequestBuilder<InputStream>(dtoObjectMapper).withHeaders(headers).build();
  }
}
