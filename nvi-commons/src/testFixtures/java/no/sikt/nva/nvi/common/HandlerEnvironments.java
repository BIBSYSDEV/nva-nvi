package no.sikt.nva.nvi.common;

import static java.util.Objects.isNull;
import static no.sikt.nva.nvi.common.EnvironmentFixtures.getHandlerEnvironment;

import java.util.Map;
import java.util.function.Supplier;

/**
 * Shared logic for the per-module handler environment registries (e.g. RestHandlerEnvironments),
 * which map each handler class to its fake environment variables.
 */
public final class HandlerEnvironments {

  private HandlerEnvironments() {}

  public static FakeEnvironment forHandler(
      Map<Class<?>, Supplier<FakeEnvironment>> handlerEnvironments, Class<?> handlerClass) {
    var environmentSupplier = handlerEnvironments.get(handlerClass);
    if (isNull(environmentSupplier)) {
      throw new IllegalArgumentException(
          "No test environment defined for " + handlerClass.getSimpleName());
    }
    return environmentSupplier.get();
  }

  public static Map.Entry<Class<?>, Supplier<FakeEnvironment>> entry(
      Class<?> handlerClass, EnvironmentFixtures... environmentVariables) {
    return Map.entry(handlerClass, () -> getHandlerEnvironment(environmentVariables));
  }
}
