package no.sikt.nva.nvi.common.model;

import java.net.URI;
import java.util.Collection;
import nva.commons.apigateway.AccessRight;
import nva.commons.apigateway.RequestInfo;
import nva.commons.apigateway.exceptions.UnauthorizedException;

public record UserInstance(
    String userName, URI topLevelOrganizationId, Collection<AccessRight> accessRights) {

  public static UserInstance fromRequestInfo(RequestInfo requestInfo) throws UnauthorizedException {
    var userName = requestInfo.getUserName();
    var topLevelOrganization = requestInfo.getTopLevelOrgCristinId().orElse(null);
    var accessRights = requestInfo.getAccessRights();
    return new UserInstance(userName, topLevelOrganization, accessRights);
  }

  public boolean isNviCurator() {
    return accessRights().contains(AccessRight.MANAGE_NVI_CANDIDATES);
  }
}
