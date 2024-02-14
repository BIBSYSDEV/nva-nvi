package no.sikt.nva.nvi.events.cristin;

import nva.commons.core.attempt.Failure;

public class CristinConversionException extends RuntimeException {

    public static final String EXCEPTION_MESSAGE = "Could not create nvi candidate:";

    public CristinConversionException(String message) {
        super(message);
    }

    public static CristinConversionException fromFailure(Failure<?> failure) {
        var message = failure.getException().getMessage();
        return new CristinConversionException(String.format("%s %s", EXCEPTION_MESSAGE, message));
    }
}
