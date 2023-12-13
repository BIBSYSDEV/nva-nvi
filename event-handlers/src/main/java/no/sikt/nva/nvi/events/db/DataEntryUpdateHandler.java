package no.sikt.nva.nvi.events.db;

import com.amazonaws.services.lambda.runtime.Context;
import com.amazonaws.services.lambda.runtime.RequestHandler;
import com.amazonaws.services.lambda.runtime.events.SQSEvent;
import no.sikt.nva.nvi.common.notification.NotificationClient;
import no.sikt.nva.nvi.common.notification.NviNotificationClient;
import no.sikt.nva.nvi.common.notification.NviPublishMessageResponse;
import nva.commons.core.JacocoGenerated;

public class DataEntryUpdateHandler implements RequestHandler<SQSEvent, Void> {

    public static final String CANDIDATE_UPDATE_APPLICABLE = "Candidate.Update.Applicable";
    private final NotificationClient<NviPublishMessageResponse> snsClient;

    @JacocoGenerated
    public DataEntryUpdateHandler() {
        this(new NviNotificationClient());
    }

    public DataEntryUpdateHandler(NotificationClient<NviPublishMessageResponse> snsClient) {
        this.snsClient = snsClient;
    }

    //TODO: Handle all dao types
    @Override
    public Void handleRequest(SQSEvent input, Context context) {
        input.getRecords().stream()
            .map(SQSEvent.SQSMessage::getBody)
            .forEach(message -> snsClient.publish(message, CANDIDATE_UPDATE_APPLICABLE));
        return null;
    }
}
