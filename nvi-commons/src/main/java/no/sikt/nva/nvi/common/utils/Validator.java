package no.sikt.nva.nvi.common.utils;

import static java.util.Objects.isNull;
import static java.util.Objects.nonNull;
import static nva.commons.core.StringUtils.isBlank;

import java.time.Instant;
import java.time.Year;
import java.time.format.DateTimeParseException;
import java.util.Collection;
import java.util.Map;
import java.util.Objects;
import no.sikt.nva.nvi.common.exceptions.ValidationException;
import no.sikt.nva.nvi.common.service.model.UpdatePeriodRequest;
import no.sikt.nva.nvi.common.service.requests.CreatePeriodRequest;
import no.sikt.nva.nvi.common.service.requests.UpsertPeriodRequest;

public final class Validator {

  private static final Year MIN_ACCEPTABLE_YEAR = Year.of(1800);
  private static final Year MAX_ACCEPTABLE_YEAR = Year.of(2100);

  private Validator() {}

  public static void hasValidLength(Integer year, int length) {
    if (year.toString().length() != length) {
      throw new IllegalArgumentException(
          "Provided period has invalid length! Expected length: " + length);
    }
  }

  public static void isBefore(Instant startDate, Instant endDate) {
    if (startDate.isAfter(endDate)) {
      throw new IllegalArgumentException("Start date can not be after end date!");
    }
  }

  public static void shouldNotBeNull(Object object, String errorMessage) {
    if (isNull(object)) {
      throw new ValidationException(errorMessage);
    }
  }

  public static void shouldNotBeBlank(String input, String errorMessage) {
    if (isBlank(input)) {
      throw new ValidationException(errorMessage);
    }
  }

  public static void shouldNotBeEmpty(Collection<?> collection, String errorMessage) {
    if (isNull(collection) || collection.stream().filter(Objects::nonNull).toList().isEmpty()) {
      throw new ValidationException(errorMessage);
    }
  }

  public static void shouldBeTrue(boolean isValid, String errorMessage) {
    if (!isValid) {
      throw new ValidationException(errorMessage);
    }
  }

  public static void doesNotHaveNullValues(UpsertPeriodRequest upsertPeriodRequest) {
    if (isNull(upsertPeriodRequest.publishingYear())) {
      throw new IllegalArgumentException("Publishing year can not be null!");
    } else if (isNull(upsertPeriodRequest.startDate())) {
      throw new IllegalArgumentException("Start date can not be null!");
    } else if (isNull(upsertPeriodRequest.reportingDate())) {
      throw new IllegalArgumentException("Reporting date can not be null!");
    } else if (upsertPeriodRequest instanceof UpdatePeriodRequest updateRequest
        && isNull(updateRequest.modifiedBy())) {
      throw new IllegalArgumentException("Modified by can not be null!");
    } else if (upsertPeriodRequest instanceof CreatePeriodRequest createRequest
        && isNull(createRequest.createdBy())) {
      throw new IllegalArgumentException("Created by can not be null!");
    }
  }

  public static <T> boolean hasElements(Collection<T> collection) {
    return nonNull(collection) && !collection.isEmpty();
  }

  public static <K, V> boolean hasElements(Map<K, V> collection) {
    return nonNull(collection) && !collection.isEmpty();
  }

  public static boolean isMissing(Object object) {
    return isNull(object) || (object instanceof Collection<?> collection && collection.isEmpty());
  }

  public static void validateYear(String yearString) {
    try {
      var year = Year.parse(yearString);
      if (year.isBefore(MIN_ACCEPTABLE_YEAR) || year.isAfter(MAX_ACCEPTABLE_YEAR)) {
        throw new ValidationException(
            String.format(
                "Invalid year: Must be in range %d to %d",
                MIN_ACCEPTABLE_YEAR.getValue(), MAX_ACCEPTABLE_YEAR.getValue()));
      }
    } catch (DateTimeParseException exception) {
      throw new ValidationException("Invalid year: Failed to parse year parameter");
    }
  }

  public static void validateValueIsNonNegative(Integer value) {
    if (nonNull(value) && value <= 0) {
      throw new ValidationException("Value cannot be negative");
    }
  }
}
