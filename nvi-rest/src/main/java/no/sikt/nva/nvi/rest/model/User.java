package no.sikt.nva.nvi.rest.model;

import java.util.List;
import nva.commons.apigateway.AccessRight;

public record User(List<Role> roles) {

  public record Role(List<AccessRight> accessRights) {}
}
