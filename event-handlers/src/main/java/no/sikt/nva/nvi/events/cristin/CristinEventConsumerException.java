package no.sikt.nva.nvi.events.cristin;

public class CristinEventConsumerException extends RuntimeException {

    public static final String CRISTIN_EVENT_ENTRY_ERROR_MESSAGE = "Could not consume cristin entry: ";

    public CristinEventConsumerException(String message) {
        super(message);
    }

    public static CristinEventConsumerException withMessage(String message) {
        return new CristinEventConsumerException(String.format("%s%s", CRISTIN_EVENT_ENTRY_ERROR_MESSAGE, message));
    }
}