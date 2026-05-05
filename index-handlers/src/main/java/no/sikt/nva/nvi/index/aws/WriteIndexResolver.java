package no.sikt.nva.nvi.index.aws;

import static no.sikt.nva.nvi.index.utils.SearchConstants.NVI_CANDIDATES_INDEX;

import java.util.Arrays;
import java.util.List;
import nva.commons.core.Environment;

public final class WriteIndexResolver {

  public static final String NVI_WRITE_INDEX_NAMES = "NVI_WRITE_INDEX_NAMES";
  private static final Environment ENVIRONMENT = new Environment();

  private WriteIndexResolver() {}

  public static List<String> resolveWriteIndices() {
    var envValue = ENVIRONMENT.readEnvOpt(NVI_WRITE_INDEX_NAMES);
    return envValue
        .filter(value -> !value.isBlank())
        .map(WriteIndexResolver::splitAndTrim)
        .orElse(List.of(NVI_CANDIDATES_INDEX));
  }

  private static List<String> splitAndTrim(String value) {
    return Arrays.stream(value.split(",")).map(String::trim).filter(s -> !s.isEmpty()).toList();
  }
}
