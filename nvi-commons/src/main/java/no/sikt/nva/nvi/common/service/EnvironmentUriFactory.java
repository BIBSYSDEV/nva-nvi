package no.sikt.nva.nvi.common.service;

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
    var apiHost = environment.readEnv(API_HOST);
    var basePath = environment.readEnv(CUSTOM_DOMAIN_BASE_PATH);
    return UriWrapper.fromHost(apiHost)
        .addChild(basePath, CANDIDATE_PATH)
        .addChild(candidateIdentifier.toString())
        .getUri();
  }

  public static URI contextUri(Environment environment) {
    var apiHost = environment.readEnv(API_HOST);
    var basePath = environment.readEnv(CUSTOM_DOMAIN_BASE_PATH);
    return UriWrapper.fromHost(apiHost).addChild(basePath, CONTEXT_PATH).getUri();
  }
}
