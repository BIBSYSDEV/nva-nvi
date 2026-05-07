package no.sikt.nva.nvi.index.apigateway;

import java.net.URI;
import nva.commons.core.paths.UriWrapper;

final class CristinOrgUriUtil {

  private static final String CRISTIN_PATH = "cristin";
  private static final String ORGANIZATION_PATH = "organization";

  private CristinOrgUriUtil() {}

  static URI toCristinOrgUri(String apiHost, String identifier) {
    return UriWrapper.fromHost(apiHost)
        .addChild(CRISTIN_PATH, ORGANIZATION_PATH)
        .addChild(identifier)
        .getUri();
  }
}
