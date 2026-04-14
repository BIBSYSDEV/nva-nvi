package no.sikt.nva.nvi.common.service;

import java.net.URI;
import java.util.UUID;
import nva.commons.core.paths.UriWrapper;

public final class CandidateUriUtil {

  private static final String CANDIDATE_PATH = "candidate";
  private static final String CONTEXT_PATH = "context";

  private CandidateUriUtil() {}

  public static URI toCandidateUri(String apiHost, String basePath, UUID identifier) {
    return UriWrapper.fromHost(apiHost)
        .addChild(basePath, CANDIDATE_PATH)
        .addChild(identifier.toString())
        .getUri();
  }

  public static URI toContextUri(String apiHost, String basePath) {
    return UriWrapper.fromHost(apiHost).addChild(basePath, CONTEXT_PATH).getUri();
  }
}
