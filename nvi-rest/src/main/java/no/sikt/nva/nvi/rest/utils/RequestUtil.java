package no.sikt.nva.nvi.rest.utils;

import static java.util.Objects.nonNull;
import java.net.URI;
import java.time.Instant;
import no.sikt.nva.nvi.common.db.model.DbNviPeriod;
import no.sikt.nva.nvi.common.db.model.DbUsername;
import nva.commons.apigateway.AccessRight;
import nva.commons.apigateway.RequestInfo;
import nva.commons.apigateway.exceptions.BadRequestException;
import nva.commons.apigateway.exceptions.ForbiddenException;
import nva.commons.apigateway.exceptions.UnauthorizedException;
import nva.commons.core.JacocoGenerated;

@JacocoGenerated
public final class RequestUtil {

    public static final String REPORTING_PERIOD_CLOSED_MESSAGE =
        "Can not update approval, reporting period is already closed or not created yet";

    private RequestUtil() {
    }

    public static DbUsername getUsername(RequestInfo requestInfo) throws UnauthorizedException {
        return new DbUsername(requestInfo.getUserName());
    }

    public static void hasAccessRight(RequestInfo requestInfo, AccessRight accessRight) throws UnauthorizedException {
        if (!requestInfo.userIsAuthorized(accessRight.name())) {
            throw new UnauthorizedException();
        }
    }

    public static void validatePeriod(DbNviPeriod period)
        throws BadRequestException {
        if (periodIsClosed(period)) {
            throw new BadRequestException(REPORTING_PERIOD_CLOSED_MESSAGE);
        }
    }

    private static boolean periodIsClosed(DbNviPeriod period) {
        return nonNull(period) && period.reportingDate().isBefore(Instant.now());
    }

    public static void validateCustomer(RequestInfo requestInfo, URI currentCustomer) throws ForbiddenException {
        var topLevelOrg = requestInfo.getTopLevelOrgCristinId().orElseThrow();
        if (!topLevelOrg.toString().equals(currentCustomer.toString())) {
            throw new ForbiddenException();
        }
    }
}
