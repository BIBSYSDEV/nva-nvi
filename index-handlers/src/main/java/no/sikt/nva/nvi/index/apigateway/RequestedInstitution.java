package no.sikt.nva.nvi.index.apigateway;

import static no.sikt.nva.nvi.common.utils.RequestUtil.isNviAdmin;
import static no.sikt.nva.nvi.index.apigateway.CristinOrgUriUtil.toCristinOrgUri;

import java.net.URI;
import java.util.Optional;
import java.util.regex.Pattern;
import nva.commons.apigateway.RequestInfo;
import nva.commons.apigateway.exceptions.BadRequestException;
import nva.commons.apigateway.exceptions.UnauthorizedException;

/**
 * Resolves which institution a report request applies to. The 'institutionId' query parameter may
 * name the requesting user's own institution, but only privileged clients may use it to request a
 * report for another institution. Clients without a top level organization, such as internal
 * backend clients, must provide it.
 */
final class RequestedInstitution {

  private static final String QUERY_PARAMETER_INSTITUTION_ID = "institutionId";
  private static final Pattern CRISTIN_ORGANIZATION_IDENTIFIER =
      Pattern.compile("\\d+\\.\\d+\\.\\d+\\.\\d+");
  private static final String MISSING_INSTITUTION_ID_MESSAGE =
      "Query parameter 'institutionId' is required for clients without a top level organization";
  private static final String INVALID_INSTITUTION_ID_MESSAGE =
      "Query parameter 'institutionId' must be a Cristin organization identifier, "
          + "for example 185.90.0.0";

  private RequestedInstitution() {}

  static void validate(RequestInfo requestInfo, String apiHost)
      throws UnauthorizedException, BadRequestException {
    var institutionIdentifier = institutionIdentifier(requestInfo);
    if (institutionIdentifier.isPresent()) {
      validateFormatOfInstitutionIdentifier(institutionIdentifier.get());
      validateAccessToRequestedInstitution(requestInfo, apiHost);
    } else {
      validateTopLevelOrganizationIsPresent(requestInfo);
    }
  }

  static URI resolve(RequestInfo requestInfo, String apiHost) {
    return requestedInstitution(requestInfo, apiHost)
        .orElseGet(() -> requestInfo.getTopLevelOrgCristinId().orElseThrow());
  }

  private static Optional<URI> requestedInstitution(RequestInfo requestInfo, String apiHost) {
    return institutionIdentifier(requestInfo)
        .map(identifier -> toCristinOrgUri(apiHost, identifier));
  }

  private static Optional<String> institutionIdentifier(RequestInfo requestInfo) {
    return requestInfo.getQueryParameterOpt(QUERY_PARAMETER_INSTITUTION_ID);
  }

  private static void validateFormatOfInstitutionIdentifier(String institutionIdentifier)
      throws BadRequestException {
    if (!CRISTIN_ORGANIZATION_IDENTIFIER.matcher(institutionIdentifier).matches()) {
      throw new BadRequestException(INVALID_INSTITUTION_ID_MESSAGE);
    }
  }

  private static void validateAccessToRequestedInstitution(RequestInfo requestInfo, String apiHost)
      throws UnauthorizedException {
    if (!isPrivilegedClient(requestInfo) && !requestsOwnInstitution(requestInfo, apiHost)) {
      throw new UnauthorizedException();
    }
  }

  private static boolean requestsOwnInstitution(RequestInfo requestInfo, String apiHost) {
    var ownInstitution = requestInfo.getTopLevelOrgCristinId();
    return ownInstitution.isPresent()
        && ownInstitution.equals(requestedInstitution(requestInfo, apiHost));
  }

  private static void validateTopLevelOrganizationIsPresent(RequestInfo requestInfo)
      throws BadRequestException {
    if (requestInfo.getTopLevelOrgCristinId().isEmpty()) {
      throw new BadRequestException(MISSING_INSTITUTION_ID_MESSAGE);
    }
  }

  private static boolean isPrivilegedClient(RequestInfo requestInfo) {
    return isNviAdmin(requestInfo) || requestInfo.clientIsInternalBackend();
  }
}
