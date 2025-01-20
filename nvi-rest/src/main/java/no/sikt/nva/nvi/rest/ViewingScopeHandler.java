package no.sikt.nva.nvi.rest;

import no.sikt.nva.nvi.common.client.OrganizationRetriever;
import no.sikt.nva.nvi.common.service.model.Candidate;
import no.sikt.nva.nvi.common.service.model.Username;
import no.sikt.nva.nvi.common.validator.ViewingScopeValidator;
import no.sikt.nva.nvi.common.validator.ViewingScopeValidatorImpl;
import no.unit.nva.auth.uriretriever.UriRetriever;
import no.unit.nva.clients.IdentityServiceClient;
import nva.commons.apigateway.exceptions.UnauthorizedException;
import nva.commons.core.JacocoGenerated;

public interface ViewingScopeHandler {

  @JacocoGenerated
  static ViewingScopeValidatorImpl defaultViewingScopeValidator() {
    return new ViewingScopeValidatorImpl(
        IdentityServiceClient.prepare(), new OrganizationRetriever(new UriRetriever()));
  }

  default Candidate validateViewingScope(
      ViewingScopeValidator validator, Username username, Candidate candidate)
      throws UnauthorizedException {
    var organizations = candidate.getNviCreatorAffiliations();
    if (!validator.userIsAllowedToAccessOneOf(username.value(), organizations)) {
      throw new UnauthorizedException();
    }
    return candidate;
  }
}
