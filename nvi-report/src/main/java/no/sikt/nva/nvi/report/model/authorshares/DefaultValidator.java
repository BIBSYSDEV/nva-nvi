package no.sikt.nva.nvi.report.model.authorshares;

import static java.util.function.Predicate.not;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import no.sikt.nva.nvi.report.model.Header;
import no.sikt.nva.nvi.report.model.Row;
import no.sikt.nva.nvi.report.model.RowValidator;

public class DefaultValidator implements RowValidator {

  private static final String DUPLICATED_HEADERS_ERROR_MESSAGE =
      "Duplicate headers found in a row: ";
  private static final String DELIMITER = ", ";
  private static final String MISSING_HEADERS_MESSAGE = "Missing headers: ";

  public static RowValidator create() {
    return new DefaultValidator();
  }

  @Override
  public void validate(Row row) {
    validateForDuplicates(row.headers());
    validateAllHeadersPresent(row);
  }

  private static boolean appearsMoreThanOnce(List<String> headers, String header) {
    return Collections.frequency(headers, header) > 1;
  }

  private static void validateForDuplicates(List<String> headers) {
    var duplicates = findDuplicates(headers);
    if (!duplicates.isEmpty()) {
      throw new IllegalArgumentException(
          DUPLICATED_HEADERS_ERROR_MESSAGE + String.join(DELIMITER, duplicates));
    }
  }

  private static List<String> findDuplicates(List<String> headers) {
    return headers.stream()
        .filter(header -> appearsMoreThanOnce(headers, header))
        .distinct()
        .toList();
  }

  private static void validateAllHeadersPresent(Row row) {
    var headers = row.headers();
    var missing =
        Arrays.stream(row.cells().getFirst().header().getClass().getEnumConstants())
            .map(Header::name)
            .filter(not(headers::contains))
            .toList();
    if (!missing.isEmpty()) {
      throw new IllegalStateException(MISSING_HEADERS_MESSAGE + String.join(DELIMITER, missing));
    }
  }
}
