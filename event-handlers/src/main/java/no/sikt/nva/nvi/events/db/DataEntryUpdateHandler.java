package no.sikt.nva.nvi.events.db;

import static no.unit.nva.commons.json.JsonUtils.dynamoObjectMapper;
import static nva.commons.core.attempt.Try.attempt;
import com.amazonaws.services.lambda.runtime.Context;
import com.amazonaws.services.lambda.runtime.RequestHandler;
import com.amazonaws.services.lambda.runtime.events.DynamodbEvent.DynamodbStreamRecord;
import com.amazonaws.services.lambda.runtime.events.SQSEvent;
import com.amazonaws.services.lambda.runtime.events.SQSEvent.SQSMessage;
import java.util.Objects;
import no.sikt.nva.nvi.common.notification.NotificationClient;
import no.sikt.nva.nvi.common.notification.NviNotificationClient;
import no.sikt.nva.nvi.common.notification.NviPublishMessageResponse;
import nva.commons.core.JacocoGenerated;
import nva.commons.core.attempt.Failure;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import software.amazon.awssdk.services.dynamodb.model.OperationType;

public class DataEntryUpdateHandler implements RequestHandler<SQSEvent, Void> {

    public static final Logger LOGGER = LoggerFactory.getLogger(DataEntryUpdateHandler.class);
    public static final String CANDIDATE_UPDATE_APPLICABLE_TOPIC = "Candidate.Update.Applicable";
    public static final String CANDIDATE_INSERT_TOPIC = "Candidate.Insert";
    public static final String CANDIDATE_REMOVED_TOPIC = "Candidate.Removed";
    private static final String ERROR_MESSAGE = "Error message: {}";
    private static final String FAILED_TO_PARSE_EVENT_MESSAGE = "Failed to map body to DynamodbStreamRecord: {}";
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
            .map(SQSMessage::getBody)
            .map(this::mapToDynamoDbRecord)
            .filter(Objects::nonNull)
            .forEach(this::publishToTopic);
        return null;
    }

    private static String getTopic(OperationType operationType) {
        return switch (operationType) {
            case INSERT -> CANDIDATE_INSERT_TOPIC;
            case MODIFY -> CANDIDATE_UPDATE_APPLICABLE_TOPIC;
            case REMOVE -> CANDIDATE_REMOVED_TOPIC;
            case UNKNOWN_TO_SDK_VERSION -> null;
        };
    }

    private void publishToTopic(DynamodbStreamRecord record) {
        var operationType = OperationType.fromValue(record.getEventName());
        var message = writeAsString(record);
        var topic = getTopic(operationType);
        var response = snsClient.publish(message, topic);
        LOGGER.info("Published message with id: {} to topic {}", response.messageId(), topic);
    }

    private String writeAsString(DynamodbStreamRecord record) {
        return attempt(() -> dynamoObjectMapper.writeValueAsString(record))
                   .orElse(failure -> {
                       handleFailure(failure, FAILED_TO_PARSE_EVENT_MESSAGE, record.toString());
                       return null;
                   });
    }

    private DynamodbStreamRecord mapToDynamoDbRecord(String body) {
        return attempt(() -> dynamoObjectMapper.readValue(body, DynamodbStreamRecord.class))
                   .orElse(failure -> {
                       handleFailure(failure, FAILED_TO_PARSE_EVENT_MESSAGE, body);
                       return null;
                   });
    }

    private void handleFailure(Failure<?> failure, String message, String messageArgument) {
        LOGGER.error(message, messageArgument);
        LOGGER.error(ERROR_MESSAGE, failure.getException().getMessage());
        //TODO: Send message to DLQ
    }
}
