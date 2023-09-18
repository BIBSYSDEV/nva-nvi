package no.sikt.nva.nvi.events.model;

public class InvalidNviMessageException extends RuntimeException {

    public InvalidNviMessageException(String message) {
        super(message);
    }
}
