package no.sikt.nva.nvi.common.utils;

import static org.apache.commons.collections4.ListUtils.union;
import java.util.List;
import no.sikt.nva.nvi.common.service.model.NviCreatorType;
import no.sikt.nva.nvi.common.service.model.Username;
import no.sikt.nva.nvi.common.service.model.VerifiedNviCreator;
import no.sikt.nva.nvi.common.service.requests.UpsertCandidateRequest;
import nva.commons.apigateway.AccessRight;
import nva.commons.apigateway.RequestInfo;
import nva.commons.apigateway.exceptions.UnauthorizedException;

public final class RequestUtil {

    private RequestUtil() {
    }

    public static Username getUsername(RequestInfo requestInfo) throws UnauthorizedException {
        return Username.fromString(requestInfo.getUserName());
    }

    public static void hasAccessRight(RequestInfo requestInfo, AccessRight accessRight) throws UnauthorizedException {
        if (!requestInfo.userIsAuthorized(accessRight)) {
            throw new UnauthorizedException();
        }
    }

    public static List<NviCreatorType> getAllCreators(UpsertCandidateRequest request) {
        var verifiedCreators = request.creators()
                                      .entrySet()
                                      .stream()
                                      .map(creator -> VerifiedNviCreator.builder()
                                                                        .withId(creator.getKey())
                                                                        .withAffiliations(creator.getValue())
                                                                        .build())
                                      .toList();
        return union(verifiedCreators, request.unverifiedCreators());
    }
}
