package no.sikt.nva.nvi.index.apigateway;

import static no.sikt.nva.nvi.common.utils.RequestUtil.isNviAdmin;
import static no.sikt.nva.nvi.index.apigateway.CristinOrgUriUtil.toCristinOrgUri;

import java.net.URI;
import nva.commons.apigateway.RequestInfo;
import nva.commons.apigateway.exceptions.UnauthorizedException;

/**
 * Resolves which institution a report request applies to. Only privileged clients may request a
 * report for another institution than their own by using the 'institutionId' query parameter.
 */
final class RequestedInstitution {

  private static final String QUERY_PARAMETER_INSTITUTION_ID = "institutionId";

  private RequestedInstitution() {}

  static void validateQueryParameterAccess(RequestInfo requestInfo) throws UnauthorizedException {
    var institutionIdParam = requestInfo.getQueryParameterOpt(QUERY_PARAMETER_INSTITUTION_ID);
    if (institutionIdParam.isPresent() && !isPrivilegedClient(requestInfo)) {
      throw new UnauthorizedException();
    }
  }

  static URI resolve(RequestInfo requestInfo, String apiHost) {
    return requestInfo
        .getQueryParameterOpt(QUERY_PARAMETER_INSTITUTION_ID)
        .map(identifier -> toCristinOrgUri(apiHost, identifier))
        .orElseGet(() -> requestInfo.getTopLevelOrgCristinId().orElseThrow());
  }

  private static boolean isPrivilegedClient(RequestInfo requestInfo) {
    return isNviAdmin(requestInfo) || requestInfo.clientIsInternalBackend();
  }
}
