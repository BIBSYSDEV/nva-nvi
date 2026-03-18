package no.sikt.nva.nvi.report.model;

import static java.util.Objects.nonNull;
import static nva.commons.core.StringUtils.EMPTY_STRING;

import java.math.BigDecimal;

public record NumericCell(Header header, BigDecimal value) implements Cell {

  @Override
  public String string() {
    return nonNull(value) ? value.toPlainString() : EMPTY_STRING;
  }
}
