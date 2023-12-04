package no.sikt.nva.nvi.events.db;

import com.amazonaws.services.lambda.runtime.Context;
import com.amazonaws.services.lambda.runtime.RequestHandler;
import com.amazonaws.services.lambda.runtime.events.DynamodbEvent;
import no.sikt.nva.nvi.common.queue.NviSendMessageResponse;
import no.sikt.nva.nvi.common.queue.QueueClient;

public class DynamoDbEventToQueueHandler implements RequestHandler<DynamodbEvent, Void> {

    private final QueueClient<NviSendMessageResponse> queueClient;

    public DynamoDbEventToQueueHandler(QueueClient<NviSendMessageResponse> queueClient) {
        this.queueClient = queueClient;
    }

    @Override
    public Void handleRequest(DynamodbEvent input, Context context) {
        return null;
    }
}
