package no.sikt.nva.nvi.index.apigateway;

import static no.sikt.nva.nvi.common.utils.RequestUtil.isNviAdmin;
import static no.sikt.nva.nvi.index.apigateway.CristinOrgUriUtil.toCristinOrgUri;

import java.net.URI;
import nva.commons.apigateway.RequestInfo;
import nva.commons.apigateway.exceptions.ApiGatewayException;
import nva.commons.apigateway.exceptions.BadRequestException;
import nva.commons.apigateway.exceptions.UnauthorizedException;

/**
 * Resolves which institution a report request applies to. Only privileged clients may request a
 * report for another institution than their own by using the 'institutionId' query parameter.
 * Clients without a top level organization, such as internal backend clients, must provide it.
 */
final class RequestedInstitution {

  private static final String QUERY_PARAMETER_INSTITUTION_ID = "institutionId";
  private static final String MISSING_INSTITUTION_ID_MESSAGE =
      "Query parameter 'institutionId' is required for clients without a top level organization";

  private RequestedInstitution() {}

  static void validate(RequestInfo requestInfo) throws ApiGatewayException {
    if (hasInstitutionIdParameter(requestInfo)) {
      validateAccessToInstitutionIdParameter(requestInfo);
    } else {
      validateTopLevelOrganizationIsPresent(requestInfo);
    }
  }

  static URI resolve(RequestInfo requestInfo, String apiHost) {
    return requestInfo
        .getQueryParameterOpt(QUERY_PARAMETER_INSTITUTION_ID)
        .map(identifier -> toCristinOrgUri(apiHost, identifier))
        .orElseGet(() -> requestInfo.getTopLevelOrgCristinId().orElseThrow());
  }

  private static boolean hasInstitutionIdParameter(RequestInfo requestInfo) {
    return requestInfo.getQueryParameterOpt(QUERY_PARAMETER_INSTITUTION_ID).isPresent();
  }

  private static void validateAccessToInstitutionIdParameter(RequestInfo requestInfo)
      throws UnauthorizedException {
    if (!isPrivilegedClient(requestInfo)) {
      throw new UnauthorizedException();
    }
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
