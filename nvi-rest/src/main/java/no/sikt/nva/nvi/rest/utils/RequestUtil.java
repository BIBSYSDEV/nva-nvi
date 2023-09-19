package no.sikt.nva.nvi.rest.utils;

import static java.util.Objects.nonNull;
import static nva.commons.core.attempt.Try.attempt;
import java.net.URI;
import java.time.Instant;
import java.util.UUID;
import no.sikt.nva.nvi.common.db.Candidate;
import no.sikt.nva.nvi.common.db.model.DbCandidate;
import no.sikt.nva.nvi.common.db.model.DbNviPeriod;
import no.sikt.nva.nvi.common.db.model.DbPublicationDate;
import no.sikt.nva.nvi.common.db.model.DbUsername;
import no.sikt.nva.nvi.common.service.NviService;
import nva.commons.apigateway.AccessRight;
import nva.commons.apigateway.RequestInfo;
import nva.commons.apigateway.exceptions.BadRequestException;
import nva.commons.apigateway.exceptions.ForbiddenException;
import nva.commons.apigateway.exceptions.NotFoundException;
import nva.commons.apigateway.exceptions.UnauthorizedException;
import nva.commons.core.JacocoGenerated;

@JacocoGenerated
public final class RequestUtil {

    public static final String REPORTING_PERIOD_CLOSED_MESSAGE =
        "Can not update approval, reporting period is already closed";
    public static final String CANDIDATE_NOT_FOUND_MESSAGE = "Candidate to update does not exist";
    public static final String PARAM_CANDIDATE_IDENTIFIER = "candidateIdentifier";

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

    public static void validatePeriod(RequestInfo requestInfo, NviService nviService)
        throws BadRequestException, NotFoundException {
        var candidateIdentifier = requestInfo.getPathParameter(PARAM_CANDIDATE_IDENTIFIER);
        var publicationYear = fetchPublicationDate(nviService, candidateIdentifier);
        var period = attempt(() -> nviService.getPeriod(publicationYear)).orElse(failure -> null);
        if (periodIsClosed(period)) {
            throw new BadRequestException(REPORTING_PERIOD_CLOSED_MESSAGE);
        }
    }

    private static String fetchPublicationDate(NviService nviService, String candidateIdentifier)
        throws NotFoundException {
        return nviService.findCandidateById(UUID.fromString(candidateIdentifier))
                   .map(Candidate::candidate)
                   .map(DbCandidate::publicationDate)
                   .map(DbPublicationDate::year)
                   .orElseThrow(RequestUtil::candidateNotFoundException);
    }

    private static NotFoundException candidateNotFoundException() {
        return new NotFoundException(CANDIDATE_NOT_FOUND_MESSAGE);
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
