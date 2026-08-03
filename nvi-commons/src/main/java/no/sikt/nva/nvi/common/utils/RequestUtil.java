package no.sikt.nva.nvi.common.utils;

import static nva.commons.apigateway.RestRequestHandler.COMMA;
import static nva.commons.core.attempt.Try.attempt;

import java.net.URI;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.List;
import no.sikt.nva.nvi.common.service.model.Username;
import no.unit.nva.identifiers.SortableIdentifier;
import nva.commons.apigateway.AccessRight;
import nva.commons.apigateway.RequestInfo;
import nva.commons.apigateway.exceptions.UnauthorizedException;
import nva.commons.core.Environment;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public final class RequestUtil {

  private static final Logger LOGGER = LoggerFactory.getLogger(RequestUtil.class);

  private RequestUtil() {}

  /**
   * Resolves a publication ID from a path parameter that contains either a publication identifier
   * or a full, URL-encoded publication URI. The URI format is deprecated and only supported until
   * all consumers send the identifier. Throws IllegalArgumentException when the parameter is
   * neither an absolute URI nor a valid SortableIdentifier.
   */
  public static URI getPublicationId(
      RequestInfo requestInfo, String pathParameterName, Environment environment) {
    var decodedParameter =
        URLDecoder.decode(
            requestInfo.getPathParameters().get(pathParameterName), StandardCharsets.UTF_8);
    if (isAbsoluteUri(decodedParameter)) {
      LOGGER.warn("Publication ID parameter is in deprecated URI format: {}", decodedParameter);
      return URI.create(decodedParameter);
    } else {
      var publicationIdentifier = new SortableIdentifier(decodedParameter);
      return EnvironmentUriFactory.publicationId(environment, publicationIdentifier);
    }
  }

  private static boolean isAbsoluteUri(String value) {
    return attempt(() -> URI.create(value).isAbsolute()).orElse(_ -> false);
  }

  public static Username getUsername(RequestInfo requestInfo) throws UnauthorizedException {
    return Username.fromString(requestInfo.getUserName());
  }

  public static void hasAccessRight(RequestInfo requestInfo, AccessRight accessRight)
      throws UnauthorizedException {
    if (!requestInfo.userIsAuthorized(accessRight)) {
      throw new UnauthorizedException();
    }
  }

  public static boolean isNviAdmin(RequestInfo requestInfo) {
    return requestInfo.userIsAuthorized(AccessRight.MANAGE_NVI);
  }

  public static boolean isNviCurator(RequestInfo requestInfo) {
    return requestInfo.userIsAuthorized(AccessRight.MANAGE_NVI_CANDIDATES);
  }

  public static List<String> parseStringAsCommaSeparatedList(String commaSeparatedValues) {
    return Arrays.stream(commaSeparatedValues.split(COMMA))
        .map(String::trim)
        .filter(entry -> !entry.isEmpty())
        .toList();
  }
}
