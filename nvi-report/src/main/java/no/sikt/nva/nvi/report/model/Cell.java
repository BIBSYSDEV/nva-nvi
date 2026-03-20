package no.sikt.nva.nvi.report.model;

import java.math.BigDecimal;

public sealed interface Cell permits NumericCell, StringCell {

  static Cell of(Header header, String value) {
    return new StringCell(header, value);
  }

  static Cell of(Header header, BigDecimal value) {
    return new NumericCell(header, value);
  }

  Header header();

  String string();
}
