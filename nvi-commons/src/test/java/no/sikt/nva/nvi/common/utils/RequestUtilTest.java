package no.sikt.nva.nvi.common.utils;

import static no.unit.nva.commons.json.JsonUtils.dtoObjectMapper;
import static no.unit.nva.testutils.RandomDataGenerator.randomString;
import static no.unit.nva.testutils.RandomDataGenerator.randomUri;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.fasterxml.jackson.core.JsonProcessingException;
import java.io.InputStream;
import java.net.URI;
import no.sikt.nva.nvi.common.service.model.Username;
import no.unit.nva.testutils.HandlerRequestBuilder;
import nva.commons.apigateway.AccessRight;
import nva.commons.apigateway.RequestInfo;
import nva.commons.apigateway.exceptions.UnauthorizedException;
import org.junit.jupiter.api.Test;

class RequestUtilTest {

  @Test
  void shouldGetUsername() throws UnauthorizedException, JsonProcessingException {
    var userName = randomString();
    var requestInfo =
        RequestInfo.fromRequest(createRequest(randomUri(), AccessRight.MANAGE_NVI, userName));
    var actual = RequestUtil.getUsername(requestInfo);
    assertEquals(Username.fromString(userName), actual);
  }

  @Test
  void shouldThrowUnauthorizedExceptionIfUserDoesNotHaveAccessRight()
      throws JsonProcessingException {
    var requestInfo =
        RequestInfo.fromRequest(createRequest(randomUri(), AccessRight.MANAGE_DOI, randomString()));
    assertThrows(
        UnauthorizedException.class,
        () -> RequestUtil.hasAccessRight(requestInfo, AccessRight.MANAGE_NVI));
  }

  private static InputStream createRequest(
      URI userTopLevelCristinInstitution, AccessRight accessRight, String userName)
      throws JsonProcessingException {
    var customerId = randomUri();
    return new HandlerRequestBuilder<InputStream>(dtoObjectMapper)
        .withCurrentCustomer(customerId)
        .withTopLevelCristinOrgId(userTopLevelCristinInstitution)
        .withAccessRights(customerId, accessRight)
        .withUserName(userName)
        .build();
  }
}
