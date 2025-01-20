package no.sikt.nva.nvi.common.service.exception;

public class PeriodNotFoundException extends RuntimeException {

  public PeriodNotFoundException(String message) {
    super(message);
  }

  public static PeriodNotFoundException withMessage(String message) {
    return new PeriodNotFoundException(message);
  }
}
