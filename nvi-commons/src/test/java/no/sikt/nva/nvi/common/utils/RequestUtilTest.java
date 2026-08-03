package no.sikt.nva.nvi.common.utils;

import static no.sikt.nva.nvi.common.EnvironmentFixtures.API_HOST;
import static no.unit.nva.commons.json.JsonUtils.dtoObjectMapper;
import static no.unit.nva.testutils.RandomDataGenerator.randomString;
import static no.unit.nva.testutils.RandomDataGenerator.randomUri;
import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.params.provider.Arguments.argumentSet;

import com.fasterxml.jackson.core.JsonProcessingException;
import java.io.InputStream;
import java.net.URI;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.stream.Stream;
import no.sikt.nva.nvi.common.EnvironmentFixtures;
import no.sikt.nva.nvi.common.service.model.Username;
import no.unit.nva.identifiers.SortableIdentifier;
import no.unit.nva.testutils.HandlerRequestBuilder;
import nva.commons.apigateway.AccessRight;
import nva.commons.apigateway.RequestInfo;
import nva.commons.apigateway.exceptions.ApiIoException;
import nva.commons.apigateway.exceptions.UnauthorizedException;
import nva.commons.logutils.LogRecorder;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.ValueSource;

class RequestUtilTest {

  private static final String PATH_PARAM_PUBLICATION_ID = "publicationId";

  @Test
  void shouldGetUsername() throws UnauthorizedException, JsonProcessingException, ApiIoException {
    var userName = randomString();
    var request = createRequest(randomUri(), AccessRight.MANAGE_NVI, userName);
    var requestInfo = RequestInfo.fromRequest(request);
    var actual = RequestUtil.getUsername(requestInfo);
    assertEquals(Username.fromString(userName), actual);
  }

  @ParameterizedTest
  @MethodSource("accessRightsProvider")
  void shouldParseAccessRights(AccessRight accessRight, boolean isNviAdmin, boolean isNviCurator)
      throws JsonProcessingException, ApiIoException {
    var request = createRequest(randomUri(), accessRight, randomString());
    var requestInfo = RequestInfo.fromRequest(request);
    var actualIsNviAdmin = RequestUtil.isNviAdmin(requestInfo);
    var actualIsNviCurator = RequestUtil.isNviCurator(requestInfo);
    assertEquals(isNviAdmin, actualIsNviAdmin);
    assertEquals(isNviCurator, actualIsNviCurator);
  }

  @Test
  void shouldThrowUnauthorizedExceptionIfUserDoesNotHaveAccessRight()
      throws JsonProcessingException, ApiIoException {
    var request = createRequest(randomUri(), AccessRight.MANAGE_DOI, randomString());
    var requestInfo = RequestInfo.fromRequest(request);
    assertThrows(
        UnauthorizedException.class,
        () -> RequestUtil.hasAccessRight(requestInfo, AccessRight.MANAGE_NVI));
  }

  @Test
  void shouldConstructPublicationIdWhenPathParameterIsPublicationIdentifier()
      throws JsonProcessingException, ApiIoException {
    var publicationIdentifier = SortableIdentifier.next().toString();
    var requestInfo = createRequestWithPublicationIdPathParameter(publicationIdentifier);
    var actualPublicationId =
        RequestUtil.getPublicationId(
            requestInfo, PATH_PARAM_PUBLICATION_ID, EnvironmentFixtures.getGlobalEnvironment());
    var expectedPublicationId =
        URI.create(
            "https://%s/publication/%s".formatted(API_HOST.getValue(), publicationIdentifier));
    assertEquals(expectedPublicationId, actualPublicationId);
  }

  @Test
  void shouldAcceptEncodedPublicationUriAsPathParameterAndLogWarning()
      throws JsonProcessingException, ApiIoException {
    var logRecorder = LogRecorder.forClass(RequestUtil.class);
    var publicationId = randomUri();
    var encodedPublicationId = URLEncoder.encode(publicationId.toString(), StandardCharsets.UTF_8);
    var requestInfo = createRequestWithPublicationIdPathParameter(encodedPublicationId);
    var actualPublicationId =
        RequestUtil.getPublicationId(
            requestInfo, PATH_PARAM_PUBLICATION_ID, EnvironmentFixtures.getGlobalEnvironment());
    assertEquals(publicationId, actualPublicationId);
    assertThat(logRecorder.asString()).contains("deprecated", publicationId.toString());
  }

  @ParameterizedTest
  @ValueSource(
      strings = {
        "not-a-valid-identifier",
        "not a parseable uri",
        "gopher://",
        "file://",
        "/relative/foo.html"
      })
  void shouldThrowIllegalArgumentExceptionWhenPathParameterIsNeitherIdentifierNorUri(
      String pathParameterValue) throws JsonProcessingException, ApiIoException {
    var requestInfo = createRequestWithPublicationIdPathParameter(pathParameterValue);
    var environment = EnvironmentFixtures.getGlobalEnvironment();
    assertThrows(
        IllegalArgumentException.class,
        () -> RequestUtil.getPublicationId(requestInfo, PATH_PARAM_PUBLICATION_ID, environment));
  }

  private static RequestInfo createRequestWithPublicationIdPathParameter(String pathParameterValue)
      throws JsonProcessingException, ApiIoException {
    var request =
        new HandlerRequestBuilder<InputStream>(dtoObjectMapper)
            .withPathParameters(Map.of(PATH_PARAM_PUBLICATION_ID, pathParameterValue))
            .build();
    return RequestInfo.fromRequest(request);
  }

  private static InputStream createRequest(
      URI userTopLevelCristinInstitution, AccessRight accessRight, String userName)
      throws JsonProcessingException {
    return new HandlerRequestBuilder<InputStream>(dtoObjectMapper)
        .withTopLevelCristinOrgId(userTopLevelCristinInstitution)
        .withAccessRights(userTopLevelCristinInstitution, accessRight)
        .withUserName(userName)
        .build();
  }

  private static Stream<Arguments> accessRightsProvider() {
    return Stream.of(
        argumentSet("User is NVI admin", AccessRight.MANAGE_NVI, true, false),
        argumentSet("User is NVI curator", AccessRight.MANAGE_NVI_CANDIDATES, false, true),
        argumentSet("User has no NVI access", AccessRight.MANAGE_OWN_RESOURCES, false, false));
  }
}
