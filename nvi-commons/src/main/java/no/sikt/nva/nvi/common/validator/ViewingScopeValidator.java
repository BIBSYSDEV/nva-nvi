package no.sikt.nva.nvi.common.validator;

import java.net.URI;
import java.util.List;

public interface ViewingScopeValidator {

    boolean userIsAllowedToAccess(String userName, List<URI> requestedOrganizations);
}
