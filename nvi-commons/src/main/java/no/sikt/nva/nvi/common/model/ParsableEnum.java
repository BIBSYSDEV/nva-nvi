package no.sikt.nva.nvi.common.model;

import java.util.Arrays;
import java.util.function.Supplier;

@FunctionalInterface
public interface ParsableEnum {
  String NO_MATCH_MESSAGE = "No constant in '%s' matching input '%s'";

  String getValue();

  /**
   * Checks if the enum matches the given string. This method checks both the enum name and the enum
   * value (the string returned by getValue()), ignoring case.
   *
   * @param candidateString A candidate string that may match the enum
   * @return true if the enum matches the given string, false otherwise
   */
  default boolean matches(String candidateString) {
    return ((Enum<?>) this).name().equalsIgnoreCase(candidateString)
        || getValue().equalsIgnoreCase(candidateString);
  }

  /**
   * Parses a string value to an enum, ignoring case and allowing for both enum name and enum value.
   * This is because of inconsistent persistence of enum values in the database, where some values
   * may have been stored as enum names and others as enum values.
   *
   * @param candidate A candidate string to be converted to matching enum value
   * @param defaultValue Default value to return if no match is found
   * @return Matching enum value or default value
   */
  static <E extends Enum<E> & ParsableEnum> E parse(
      Class<E> enumClass, String candidate, E defaultValue) {
    return Arrays.stream(enumClass.getEnumConstants())
        .filter(e -> e.matches(candidate))
        .findFirst()
        .orElse(defaultValue);
  }

  static <E extends Enum<E> & ParsableEnum> E parse(Class<E> enumClass, String candidate) {

    return Arrays.stream(enumClass.getEnumConstants())
        .filter(e -> e.matches(candidate))
        .findFirst()
        .orElseThrow(exceptionSupplier(enumClass, candidate));
  }

  private static <E extends Enum<E> & ParsableEnum>
      Supplier<IllegalArgumentException> exceptionSupplier(Class<E> enumClass, String candidate) {

    return () ->
        new IllegalArgumentException(
            String.format(NO_MATCH_MESSAGE, enumClass.getSimpleName(), candidate));
  }
}
