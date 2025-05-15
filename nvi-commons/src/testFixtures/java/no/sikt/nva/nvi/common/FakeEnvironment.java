package no.sikt.nva.nvi.common;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import nva.commons.core.Environment;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class FakeEnvironment extends Environment {

  private static final Logger logger = LoggerFactory.getLogger(FakeEnvironment.class);
  private final Map<String, String> environmentVariables;

  public FakeEnvironment(Map<String, String> environmentVariables) {
    super();
    this.environmentVariables = environmentVariables;
  }

  public static Builder builder() {
    return new Builder();
  }

  @Override
  public Optional<String> readEnvOpt(String variableName) {
    return Optional.ofNullable(environmentVariables.get(variableName))
        .filter(value -> !value.isBlank());
  }

  @Override
  public String readEnv(String variableName) {
    return readEnvOpt(variableName).orElseThrow(() -> variableNotSetException(variableName));
  }

  private IllegalStateException variableNotSetException(String variableName) {
    String message = ENVIRONMENT_VARIABLE_NOT_SET + variableName;
    logger.error(message);
    return new IllegalStateException(message);
  }

  public static class Builder {
    private final Map<String, String> environmentVariables = new HashMap<>();

    public Builder with(String key, String value) {
      environmentVariables.put(key, value);
      return this;
    }

    public Builder with(EnvironmentFixtures entry) {
      environmentVariables.put(entry.getKey(), entry.getValue());
      return this;
    }

    public Builder without(String key) {
      environmentVariables.remove(key);
      return this;
    }

    public FakeEnvironment build() {
      return new FakeEnvironment(environmentVariables);
    }
  }
}
