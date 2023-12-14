package no.sikt.nva.nvi.events.db;

import static no.sikt.nva.nvi.events.db.DynamoDbUtils.getImage;
import static no.sikt.nva.nvi.events.db.TopicConstants.APPROVAL_INSERT_TOPIC;
import static no.sikt.nva.nvi.events.db.TopicConstants.APPROVAL_REMOVE_TOPIC;
import static no.sikt.nva.nvi.events.db.TopicConstants.APPROVAL_UPDATE_TOPIC;
import static no.sikt.nva.nvi.events.db.TopicConstants.CANDIDATE_INSERT_TOPIC;
import static no.sikt.nva.nvi.events.db.TopicConstants.CANDIDATE_REMOVED_TOPIC;
import static no.sikt.nva.nvi.events.db.TopicConstants.CANDIDATE_UPDATE_APPLICABLE_TOPIC;
import static no.sikt.nva.nvi.events.db.TopicConstants.CANDIDATE_UPDATE_NOT_APPLICABLE_TOPIC;
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
import nva.commons.core.JacocoGenerated;
import nva.commons.core.attempt.Failure;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import software.amazon.awssdk.services.dynamodb.model.OperationType;

public class DataEntryUpdateHandler implements RequestHandler<SQSEvent, Void> {

    public static final Logger LOGGER = LoggerFactory.getLogger(DataEntryUpdateHandler.class);
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

    @Override
    public Void handleRequest(SQSEvent input, Context context) {
        input.getRecords().stream()
            .map(SQSMessage::getBody)
            .map(this::mapToDynamoDbRecord)
            .filter(Objects::nonNull)
            .forEach(this::publishToTopic);
        return null;
    }

    private static String getTopic(OperationType operationType, Dao dao) {
        return switch (operationType) {
            case INSERT -> getInsertTopic(dao);
            case MODIFY -> getUpdateTopic(dao);
            case REMOVE -> getCandidateRemovedTopic(dao);
            case UNKNOWN_TO_SDK_VERSION -> null;
        };
    }

    private static String getCandidateRemovedTopic(Dao dao) {
        if (dao instanceof CandidateDao) {
            return CANDIDATE_REMOVED_TOPIC;
        } else if (dao instanceof ApprovalStatusDao) {
            return APPROVAL_REMOVE_TOPIC;
        } else {
            return null;
        }
    }

    private static String getUpdateTopic(Dao dao) {
        if (dao instanceof CandidateDao candidateDao) {
            return candidateDao.candidate().applicable()
                       ? CANDIDATE_UPDATE_APPLICABLE_TOPIC
                       : CANDIDATE_UPDATE_NOT_APPLICABLE_TOPIC;
        } else if (dao instanceof ApprovalStatusDao) {
            return APPROVAL_UPDATE_TOPIC;
        } else {
            return null;
        }
    }

    private static String getInsertTopic(Dao dao) {
        if (dao instanceof CandidateDao) {
            return CANDIDATE_INSERT_TOPIC;
        } else if (dao instanceof ApprovalStatusDao) {
            return APPROVAL_INSERT_TOPIC;
        } else {
            return null;
        }
    }

    private static boolean isNotCandidateOrApproval(Dao dao, OperationType operationType) {
        if (!(dao instanceof CandidateDao || dao instanceof ApprovalStatusDao)) {
            LOGGER.info("Skipping event with operation type {} for dao type {}", operationType, dao.getClass());
            return true;
        }
        return false;
    }

    private void publishToTopic(DynamodbStreamRecord record) {
        var operationType = OperationType.fromValue(record.getEventName());
        var dao = extractDao(record);
        if (isNotCandidateOrApproval(dao, operationType)) {
            return;
        }
        publish(record, getTopic(operationType, dao));
    }

    private void publish(DynamodbStreamRecord record, String topic) {
        var response = snsClient.publish(writeAsString(record), topic);
        LOGGER.info("Published message with id: {} to topic {}", response.messageId(), topic);
    }

    private Dao extractDao(DynamodbStreamRecord record) {
        return attempt(() -> DynamoEntryWithRangeKey.parseAttributeValuesMap(getImage(record), Dao.class)).orElse(daoFailure -> {
            handleFailure(daoFailure, "Failed to parse dao from dynamodb record: {}", record.toString());
            return null;
        });
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
