package no.sikt.nva.nvi.index.apigateway;

import java.net.URI;
import java.util.List;
import no.sikt.nva.nvi.common.validator.ViewingScopeValidator;

public class FakeViewingScopeValidator implements ViewingScopeValidator {

    private final boolean returnValue;

    public FakeViewingScopeValidator(boolean returnValue) {
        this.returnValue = returnValue;
    }

    @Override
    public boolean userIsAllowedToAccess(String userName, List<URI> requestedOrganizations) {
        return returnValue;
    }
}
