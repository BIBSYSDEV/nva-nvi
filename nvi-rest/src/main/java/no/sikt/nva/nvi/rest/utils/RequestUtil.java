package no.sikt.nva.nvi.rest.utils;

import no.sikt.nva.nvi.common.db.model.DbUsername;
import nva.commons.apigateway.AccessRight;
import nva.commons.apigateway.RequestInfo;
import nva.commons.apigateway.exceptions.UnauthorizedException;
import nva.commons.core.JacocoGenerated;

@JacocoGenerated
public final class RequestUtil {

    private RequestUtil() {
    }

    public static DbUsername getUsername(RequestInfo requestInfo) throws UnauthorizedException {
        return DbUsername.fromString(requestInfo.getUserName());
    }

    public static void hasAccessRight(RequestInfo requestInfo, AccessRight accessRight) throws UnauthorizedException {
        if (!requestInfo.userIsAuthorized(accessRight.name())) {
            throw new UnauthorizedException();
        }
    }
}
