package no.sikt.nva.nvi.common.service.exception;

public class UnauthorizedOperationException extends RuntimeException {

  public UnauthorizedOperationException(String message) {
    super(message);
  }
}
