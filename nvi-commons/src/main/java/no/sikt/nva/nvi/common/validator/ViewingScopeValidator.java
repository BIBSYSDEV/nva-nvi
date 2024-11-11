package no.sikt.nva.nvi.common.validator;

import java.net.URI;
import java.util.List;

public interface ViewingScopeValidator {

    boolean userIsAllowedToAccessAll(String userName, List<URI> organizations);

    boolean userIsAllowedToAccessOneOf(String userName, List<URI> organizations);
}
