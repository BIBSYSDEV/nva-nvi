package no.sikt.nva.nvi.events.db;

import static no.unit.nva.commons.json.JsonUtils.dtoObjectMapper;
import static nva.commons.core.attempt.Try.attempt;
import com.amazonaws.services.lambda.runtime.Context;
import com.amazonaws.services.lambda.runtime.RequestHandler;
import com.amazonaws.services.lambda.runtime.events.DynamodbEvent;
import com.amazonaws.services.lambda.runtime.events.DynamodbEvent.DynamodbStreamRecord;
import java.util.List;
import java.util.stream.IntStream;
import java.util.stream.Stream;
import no.sikt.nva.nvi.common.queue.NviSendMessageResponse;
import no.sikt.nva.nvi.common.queue.QueueClient;
import nva.commons.core.attempt.Failure;

public class DynamoDbEventToQueueHandler implements RequestHandler<DynamodbEvent, Void> {

    public static final String QUEUE_URL = "queueUrl";
    private static final int BATCH_SIZE = 10;
    private final QueueClient<NviSendMessageResponse> queueClient;

    public DynamoDbEventToQueueHandler(QueueClient<NviSendMessageResponse> queueClient) {
        this.queueClient = queueClient;
    }

    @Override
    public Void handleRequest(DynamodbEvent input, Context context) {
        attempt(() -> {
            splitIntoBatchesAndSend(input);
            return null;
        }).orElseThrow(failure -> handleFailure(failure, input.getRecords()));
        return null;
    }

    private static List<String> mapToJsonStrings(List<DynamodbStreamRecord> records) {
        return records.stream().map(DynamoDbEventToQueueHandler::writeAsJsonString).toList();
    }

    private static String writeAsJsonString(DynamodbStreamRecord record) {
        return attempt(() -> dtoObjectMapper.writeValueAsString(record)).orElseThrow();
    }

    private void splitIntoBatchesAndSend(DynamodbEvent input) {
        splitIntoBatches(input.getRecords())
            .map(DynamoDbEventToQueueHandler::mapToJsonStrings)
            .forEach(this::sendBatch);
    }

    private RuntimeException handleFailure(Failure<Object> failure, List<DynamodbStreamRecord> records) {
        return null;
    }

    private void sendBatch(List<String> messages) {
        queueClient.sendMessageBatch(messages, QUEUE_URL);
    }

    private Stream<List<DynamodbStreamRecord>> splitIntoBatches(List<DynamodbStreamRecord> records) {
        var count = records.size();
        return IntStream.range(0, (count + BATCH_SIZE - 1) / BATCH_SIZE)
                   .mapToObj(i -> records.subList(i * BATCH_SIZE, Math.min((i + 1) * BATCH_SIZE, count)));
    }
}
