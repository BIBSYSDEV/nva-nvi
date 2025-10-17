package no.sikt.nva.nvi.common.service.exception;

public class PeriodAlreadyExistsException extends IllegalArgumentException {

  public PeriodAlreadyExistsException(String message) {
    super(message);
  }

  public static PeriodAlreadyExistsException forYear(String publishingYear) {
    return new PeriodAlreadyExistsException(
        String.format("Period with publishing year %s already exists!", publishingYear));
  }
}
