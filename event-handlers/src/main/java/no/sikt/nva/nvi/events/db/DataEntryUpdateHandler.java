package no.sikt.nva.nvi.events.db;

import static no.sikt.nva.nvi.events.db.DynamoDbUtils.extractIdFromRecord;
import static no.sikt.nva.nvi.events.db.DynamoDbUtils.getImage;
import static no.sikt.nva.nvi.events.db.ExceptionUtils.getStackTrace;
import static no.unit.nva.commons.json.JsonUtils.dynamoObjectMapper;
import static nva.commons.core.attempt.Try.attempt;
import com.amazonaws.services.lambda.runtime.Context;
import com.amazonaws.services.lambda.runtime.RequestHandler;
import com.amazonaws.services.lambda.runtime.events.DynamodbEvent.DynamodbStreamRecord;
import com.amazonaws.services.lambda.runtime.events.SQSEvent;
import com.amazonaws.services.lambda.runtime.events.SQSEvent.SQSMessage;
import java.util.Objects;
import no.sikt.nva.nvi.common.db.ApprovalStatusDao;
import no.sikt.nva.nvi.common.db.CandidateDao;
import no.sikt.nva.nvi.common.db.Dao;
import no.sikt.nva.nvi.common.db.DynamoEntryWithRangeKey;
import no.sikt.nva.nvi.common.notification.NotificationClient;
import no.sikt.nva.nvi.common.notification.NviNotificationClient;
import no.sikt.nva.nvi.common.notification.NviPublishMessageResponse;
import no.sikt.nva.nvi.common.queue.NviQueueClient;
import no.sikt.nva.nvi.common.queue.NviSendMessageBatchResponse;
import no.sikt.nva.nvi.common.queue.NviSendMessageResponse;
import no.sikt.nva.nvi.common.queue.QueueClient;
import nva.commons.core.Environment;
import nva.commons.core.JacocoGenerated;
import nva.commons.core.attempt.Failure;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import software.amazon.awssdk.services.dynamodb.model.OperationType;

public class DataEntryUpdateHandler implements RequestHandler<SQSEvent, Void> {

    private static final Logger LOGGER = LoggerFactory.getLogger(DataEntryUpdateHandler.class);
    private static final String PUBLISHED_MESSAGE = "Published message with id: {} to topic {}";
    private static final String FAILED_TO_PUBLISH_MESSAGE = "Failed to publish message for record {}";
    private static final String FAILED_TO_PARSE_EVENT_MESSAGE = "Failed to map body to DynamodbStreamRecord: {}";
    private static final String SKIPPING_EVENT_MESSAGE = "Skipping event with operation type {} for dao type {}";
    private static final String ERROR_MESSAGE = "Error message: {}";
    private static final String DLQ_MESSAGE = "Failed to process record %s. Exception: %s ";
    private static final String INDEX_DLQ = "INDEX_DLQ";
    private final NotificationClient<NviPublishMessageResponse> snsClient;
    private final Environment environment;
    private final QueueClient queueClient;
    private final String dlqUrl;

    @JacocoGenerated
    public DataEntryUpdateHandler() {
        this(new NviNotificationClient(), new Environment(), new NviQueueClient());
    }

    public DataEntryUpdateHandler(NotificationClient<NviPublishMessageResponse> snsClient, Environment environment,
                                  QueueClient queueClient) {
        this.snsClient = snsClient;
        this.environment = environment;
        this.queueClient = queueClient;
        this.dlqUrl = environment.readEnv(INDEX_DLQ);
    }

    @Override
    public Void handleRequest(SQSEvent input, Context context) {
        input.getRecords().stream()
            .map(SQSMessage::getBody)
            .map(this::mapToDynamoDbRecord)
            .filter(Objects::nonNull)
            .forEach(this::publishToTopic);
        return null;
    }

    private static boolean isNotCandidateOrApproval(Dao dao) {
        return !(dao instanceof CandidateDao || dao instanceof ApprovalStatusDao);
    }

    private static boolean isUnknownOperationType(OperationType operationType) {
        return OperationType.UNKNOWN_TO_SDK_VERSION.equals(operationType);
    }

    private void publishToTopic(DynamodbStreamRecord streamRecord) {
        attempt(() -> extractDaoAndPublish(streamRecord)).orElse(failure -> {
            handleFailure(failure, streamRecord);
            return null;
        });
    }

    private NviPublishMessageResponse extractDaoAndPublish(DynamodbStreamRecord streamRecord) {
        var operationType = OperationType.fromValue(streamRecord.getEventName());
        var dao = extractDao(streamRecord);
        if (isNotCandidateOrApproval(dao) || isUnknownOperationType(operationType)) {
            LOGGER.info(SKIPPING_EVENT_MESSAGE, operationType, dao.getClass());
            return null;
        }
        return publish(streamRecord, getTopic(operationType, dao));
    }

    private String getTopic(OperationType operationType, Dao dao) {
        return new DataEntryUpdateTopicProvider(environment).getTopic(operationType, dao);
    }

    private NviPublishMessageResponse publish(DynamodbStreamRecord streamRecord, String topic) {
        var response = snsClient.publish(writeAsString(streamRecord), topic);
        LOGGER.info(PUBLISHED_MESSAGE, response.messageId(), topic);
        return response;
    }

    private Dao extractDao(DynamodbStreamRecord streamRecord) {
        return attempt(() -> DynamoEntryWithRangeKey.parseAttributeValuesMap(getImage(streamRecord), Dao.class))
                   .orElseThrow();
    }

    private String writeAsString(DynamodbStreamRecord streamRecord) {
        return attempt(() -> dynamoObjectMapper.writeValueAsString(streamRecord)).orElseThrow();
    }

    private DynamodbStreamRecord mapToDynamoDbRecord(String body) {
        return attempt(() -> dynamoObjectMapper.readValue(body, DynamodbStreamRecord.class))
                   .orElse(failure -> {
                       handleFailure(failure, body);
                       return null;
                   });
    }

    private void handleFailure(Failure<?> failure, String body) {
        LOGGER.error(FAILED_TO_PARSE_EVENT_MESSAGE, body);
        LOGGER.error(ERROR_MESSAGE, failure.getException().getMessage());
        sendToDlq(body, failure.getException());
    }

    private void handleFailure(Failure<?> failure, DynamodbStreamRecord record) {
        LOGGER.error(FAILED_TO_PUBLISH_MESSAGE, DataEntryUpdateHandler.FAILED_TO_PUBLISH_MESSAGE);
        LOGGER.error(ERROR_MESSAGE, failure.getException().getMessage());
        sendToDlq(record, failure.getException());
    }

    private void sendToDlq(DynamodbStreamRecord record, Exception exception) {
        var message = String.format(DLQ_MESSAGE, record.toString(), getStackTrace(exception));
        extractIdFromRecord(record)
            .ifPresentOrElse(id -> queueClient.sendMessage(message, dlqUrl, id),
                             () -> queueClient.sendMessage(message, dlqUrl));
    }

    private void sendToDlq(String body, Exception exception) {
        var message = String.format(DLQ_MESSAGE, body, getStackTrace(exception));
        queueClient.sendMessage(message, dlqUrl);
    }
}
