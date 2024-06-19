package no.sikt.nva.nvi.common.utils;

import no.sikt.nva.nvi.common.service.model.Username;
import nva.commons.apigateway.AccessRight;
import nva.commons.apigateway.RequestInfo;
import nva.commons.apigateway.exceptions.UnauthorizedException;
import nva.commons.core.JacocoGenerated;

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
}
