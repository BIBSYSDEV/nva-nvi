package no.sikt.nva.nvi.common.utils;

import java.util.List;
import java.util.stream.Stream;
import no.sikt.nva.nvi.common.service.dto.NviCreatorDto;
import no.sikt.nva.nvi.common.service.model.Username;
import no.sikt.nva.nvi.common.service.requests.UpsertCandidateRequest;
import nva.commons.apigateway.AccessRight;
import nva.commons.apigateway.RequestInfo;
import nva.commons.apigateway.exceptions.UnauthorizedException;

public final class RequestUtil {

  private RequestUtil() {}

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

  public static List<NviCreatorDto> getAllCreators(UpsertCandidateRequest request) {
    var verifiedCreators = request.verifiedCreators().stream();
    var unverifiedCreators = request.unverifiedCreators().stream();
    return Stream.concat(verifiedCreators, unverifiedCreators)
        .map(NviCreatorDto.class::cast)
        .toList();
  }
}
