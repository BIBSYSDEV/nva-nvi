package no.sikt.nva.nvi.common.utils;

import static no.unit.nva.commons.json.JsonUtils.dtoObjectMapper;
import static no.unit.nva.testutils.RandomDataGenerator.randomString;
import static no.unit.nva.testutils.RandomDataGenerator.randomUri;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.params.provider.Arguments.argumentSet;

import com.fasterxml.jackson.core.JsonProcessingException;
import java.io.InputStream;
import java.net.URI;
import java.util.stream.Stream;
import no.sikt.nva.nvi.common.service.model.Username;
import no.unit.nva.testutils.HandlerRequestBuilder;
import nva.commons.apigateway.AccessRight;
import nva.commons.apigateway.RequestInfo;
import nva.commons.apigateway.exceptions.ApiIoException;
import nva.commons.apigateway.exceptions.UnauthorizedException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

class RequestUtilTest {

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
