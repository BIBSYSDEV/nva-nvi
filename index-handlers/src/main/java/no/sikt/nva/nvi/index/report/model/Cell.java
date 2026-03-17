package no.sikt.nva.nvi.index.report.model;

import static nva.commons.core.StringUtils.EMPTY_STRING;

import java.math.BigDecimal;
import java.util.Optional;

public sealed interface Cell permits NumericCell, StringCell {

  static Cell of(Header header, String value) {
    return new StringCell(header, value);
  }

  static Cell of(Header header, BigDecimal value) {
    return new NumericCell(header, value);
  }

  Header header();

  default boolean isNumeric() {
    return this instanceof NumericCell;
  }

  default BigDecimal numericValue() {
    return this instanceof NumericCell numericCell ? numericCell.value() : null;
  }

  default String stringValue() {
    return switch (this) {
      case StringCell stringCell ->
          Optional.of(stringCell).map(StringCell::value).orElse(EMPTY_STRING);
      case NumericCell numericCell ->
          Optional.of(numericCell)
              .map(NumericCell::value)
              .map(BigDecimal::toPlainString)
              .orElse(EMPTY_STRING);
    };
  }
}
