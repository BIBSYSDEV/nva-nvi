package no.sikt.nva.nvi.common.service;

import java.net.URI;
import java.util.UUID;
import nva.commons.core.Environment;
import nva.commons.core.paths.UriWrapper;

public final class CandidateUriUtil {

  private static final String API_HOST = "API_HOST";
  private static final String CUSTOM_DOMAIN_BASE_PATH = "CUSTOM_DOMAIN_BASE_PATH";
  private static final String CANDIDATE_PATH = "candidate";
  private static final String CONTEXT_PATH = "context";

  private CandidateUriUtil() {}

  public static URI toCandidateUri(Environment environment, UUID identifier) {
    return toCandidateUri(
        environment.readEnv(API_HOST), environment.readEnv(CUSTOM_DOMAIN_BASE_PATH), identifier);
  }

  public static URI toContextUri(Environment environment) {
    return toContextUri(
        environment.readEnv(API_HOST), environment.readEnv(CUSTOM_DOMAIN_BASE_PATH));
  }

  static URI toCandidateUri(String apiHost, String basePath, UUID identifier) {
    return UriWrapper.fromHost(apiHost)
        .addChild(basePath, CANDIDATE_PATH)
        .addChild(identifier.toString())
        .getUri();
  }

  static URI toContextUri(String apiHost, String basePath) {
    return UriWrapper.fromHost(apiHost).addChild(basePath, CONTEXT_PATH).getUri();
  }
}
