package no.sikt.nva.nvi.common.utils;

import java.net.URI;
import java.util.UUID;
import nva.commons.core.Environment;
import nva.commons.core.paths.UriWrapper;

public final class EnvironmentUriFactory {

  private static final String API_HOST = "API_HOST";
  private static final String CUSTOM_DOMAIN_BASE_PATH = "CUSTOM_DOMAIN_BASE_PATH";
  private static final String CANDIDATE_PATH = "candidate";
  private static final String CONTEXT_PATH = "context";

  private EnvironmentUriFactory() {}

  public static URI candidateId(Environment environment, UUID candidateIdentifier) {
    return baseUrl(environment)
        .addChild(CANDIDATE_PATH)
        .addChild(candidateIdentifier.toString())
        .getUri();
  }

  public static URI context(Environment environment) {
    return baseUrl(environment).addChild(CONTEXT_PATH).getUri();
  }

  private static UriWrapper baseUrl(Environment environment) {
    return UriWrapper.fromHost(environment.readEnv(API_HOST))
        .addChild(environment.readEnv(CUSTOM_DOMAIN_BASE_PATH));
  }
}
