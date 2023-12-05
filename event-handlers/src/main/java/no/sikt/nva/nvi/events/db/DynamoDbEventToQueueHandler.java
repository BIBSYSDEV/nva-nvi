package no.sikt.nva.nvi.events.db;

import static no.unit.nva.commons.json.JsonUtils.dtoObjectMapper;
import static nva.commons.core.attempt.Try.attempt;
import com.amazonaws.services.lambda.runtime.Context;
import com.amazonaws.services.lambda.runtime.RequestHandler;
import com.amazonaws.services.lambda.runtime.events.DynamodbEvent;
import com.amazonaws.services.lambda.runtime.events.DynamodbEvent.DynamodbStreamRecord;
import java.util.List;
import no.sikt.nva.nvi.common.queue.NviSendMessageResponse;
import no.sikt.nva.nvi.common.queue.QueueClient;

public class DynamoDbEventToQueueHandler implements RequestHandler<DynamodbEvent, Void> {

    private final QueueClient<NviSendMessageResponse> queueClient;

    public DynamoDbEventToQueueHandler(QueueClient<NviSendMessageResponse> queueClient) {
        this.queueClient = queueClient;
    }

    @Override
    public Void handleRequest(DynamodbEvent input, Context context) {
        queueClient.sendMessageBatch(mapToJsonStrings(input), "queueUrl");
        return null;
    }

    private static List<String> mapToJsonStrings(DynamodbEvent input) {
        return input.getRecords()
                   .stream()
                   .map(DynamoDbEventToQueueHandler::writeAsJsonString)
                   .toList();
    }

    private static String writeAsJsonString(DynamodbStreamRecord record) {
        return attempt(() -> dtoObjectMapper.writeValueAsString(record)).orElseThrow();
    }
}
