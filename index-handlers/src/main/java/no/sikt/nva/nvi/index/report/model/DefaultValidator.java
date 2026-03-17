package no.sikt.nva.nvi.index.report.model;

import static java.util.function.Predicate.not;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

public class DefaultValidator implements RowValidator {

  private static final String DUPLICATED_HEADERS_ERROR_MESSAGE =
      "Duplicate headers found in a row: ";

  public static RowValidator create() {
    return new DefaultValidator();
  }

  @Override
  public void validate(Row row) {
    validateForDuplicates(row.headers());
    validateAllHeadersPresent(row.headers());
  }

  private static boolean appearsMoreThanOnce(List<String> headers, String header) {
    return Collections.frequency(headers, header) > 1;
  }

  private static void validateForDuplicates(List<String> headers) {
    var duplicates = findDuplicates(headers);
    if (!duplicates.isEmpty()) {
      throw new IllegalArgumentException(
          DUPLICATED_HEADERS_ERROR_MESSAGE + String.join(", ", duplicates));
    }
  }

  private static List<String> findDuplicates(List<String> headers) {
    return headers.stream()
        .filter(header -> appearsMoreThanOnce(headers, header))
        .distinct()
        .toList();
  }

  private static void validateAllHeadersPresent(List<String> headers) {
    var missing =
        Arrays.stream(ReportHeader.values())
            .map(ReportHeader::name)
            .filter(not(headers::contains))
            .toList();
    if (!missing.isEmpty()) {
      throw new IllegalStateException("Missing headers: " + String.join(", ", missing));
    }
  }
}
