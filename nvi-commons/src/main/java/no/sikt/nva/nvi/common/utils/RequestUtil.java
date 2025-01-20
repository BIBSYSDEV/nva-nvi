package no.sikt.nva.nvi.common.utils;

import java.util.List;
import java.util.stream.Stream;
import no.sikt.nva.nvi.common.service.dto.NviCreatorDto;
import no.sikt.nva.nvi.common.service.dto.VerifiedNviCreatorDto;
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

  public static List<NviCreatorDto> getAllCreators(UpsertCandidateRequest request) {
    Stream<NviCreatorDto> verifiedCreators =
        request.creators().entrySet().stream()
            .map(
                creator ->
                    VerifiedNviCreatorDto.builder()
                        .withId(creator.getKey())
                        .withAffiliations(creator.getValue())
                        .build());
    var unverifiedCreators = request.unverifiedCreators().stream();
    return Stream.concat(verifiedCreators, unverifiedCreators).toList();
  }
}
