package no.sikt.nva.nvi.common.model;

import static nva.commons.core.attempt.Try.attempt;

import java.util.Optional;

public enum Sector {
  UHI,
  HEALTH,
  INSTITUTE,
  ABM,
  OTHER,
  UNKNOWN;

  public static Optional<Sector> fromString(String value) {
    return attempt(() -> Sector.valueOf(value)).toOptional();
  }
}
