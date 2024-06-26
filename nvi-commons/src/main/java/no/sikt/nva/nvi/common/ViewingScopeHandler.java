package no.sikt.nva.nvi.common;

import no.sikt.nva.nvi.common.service.model.Candidate;
import no.sikt.nva.nvi.common.service.model.Username;
import no.sikt.nva.nvi.common.validator.ViewingScopeValidator;
import nva.commons.apigateway.exceptions.UnauthorizedException;

public interface ViewingScopeHandler {

    default Candidate validateViewingScope(ViewingScopeValidator validator, Username username, Candidate candidate)
        throws UnauthorizedException {
        var organizations = candidate.getNviCreatorAffiliations();
        if (!validator.userIsAllowedToAccessOneOf(username.value(), organizations)) {
            throw new UnauthorizedException();
        }
        return candidate;
    }
}
