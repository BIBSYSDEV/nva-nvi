package no.sikt.nva.nvi.events;

import static java.util.Objects.isNull;
import static nva.commons.core.attempt.Try.attempt;
import com.amazonaws.services.lambda.runtime.Context;
import com.amazonaws.services.lambda.runtime.events.SQSEvent;
import com.amazonaws.services.lambda.runtime.events.SQSEvent.SQSMessage;
import java.util.List;
import no.sikt.nva.nvi.events.model.PersistedResourceMessage;
import no.unit.nva.events.handlers.DestinationsEventBridgeEventHandler;
import no.unit.nva.events.models.AwsEventBridgeDetail;
import no.unit.nva.events.models.AwsEventBridgeEvent;
import no.unit.nva.events.models.EventReference;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class QueuePersistedResourceHandler extends DestinationsEventBridgeEventHandler<EventReference, SQSEvent> {

    private static final Logger LOGGER = LoggerFactory.getLogger(QueuePersistedResourceHandler.class);
    private static final String ERROR_MSG = "Invalid EventReference, missing uri: %s";

    public QueuePersistedResourceHandler() {
        super(EventReference.class);
    }

    @Override
    protected SQSEvent processInputPayload(EventReference input,
                                           AwsEventBridgeEvent<AwsEventBridgeDetail<EventReference>> event,
                                           Context context) {
        return createEvent(createMessageBody(input));
    }

    private static SQSEvent createEvent(String messageBody) {
        var event = new SQSEvent();
        event.setRecords(List.of(createMessage(messageBody)));
        return event;
    }

    private static SQSMessage createMessage(String messageBody) {
        var message = new SQSMessage();
        message.setBody(messageBody);
        return message;
    }

    private static void validateInput(EventReference input) {
        if (isNull(input.getUri())) {
            LOGGER.error(String.format(ERROR_MSG, input));
            throw new RuntimeException();
        }
    }

    private String createMessageBody(EventReference input) {
        validateInput(input);
        return attempt(
            () -> objectMapper.writeValueAsString(new PersistedResourceMessage(input.getUri()))).orElseThrow();
    }
}
