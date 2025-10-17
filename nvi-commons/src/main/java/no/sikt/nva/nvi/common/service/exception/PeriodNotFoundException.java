package no.sikt.nva.nvi.common.service.exception;

import java.util.function.Supplier;

public class PeriodNotFoundException extends RuntimeException {

  public PeriodNotFoundException(String message) {
    super(message);
  }

  public static PeriodNotFoundException withMessage(String message) {
    return new PeriodNotFoundException(message);
  }

  public static Supplier<PeriodNotFoundException> forYear(String publishingYear) {
    return () ->
        new PeriodNotFoundException(
            String.format("Period for year %s does not exist!", publishingYear));
  }
}
