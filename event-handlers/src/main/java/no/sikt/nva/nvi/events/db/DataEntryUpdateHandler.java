package no.sikt.nva.nvi.events.db;

import static java.util.Objects.nonNull;
import static no.unit.nva.commons.json.JsonUtils.dtoObjectMapper;
import static no.unit.nva.commons.json.JsonUtils.dynamoObjectMapper;
import static nva.commons.core.attempt.Try.attempt;
import com.amazonaws.services.lambda.runtime.Context;
import com.amazonaws.services.lambda.runtime.RequestHandler;
import com.amazonaws.services.lambda.runtime.events.DynamodbEvent.DynamodbStreamRecord;
import com.amazonaws.services.lambda.runtime.events.SQSEvent;
import com.amazonaws.services.lambda.runtime.events.SQSEvent.SQSMessage;
import com.amazonaws.services.lambda.runtime.events.models.dynamodb.AttributeValue;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Objects;
import java.util.stream.Collectors;
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
    public static final String CANDIDATE_UPDATE_APPLICABLE_TOPIC = "Candidate.Update.Applicable";
    public static final String CANDIDATE_INSERT_TOPIC = "Candidate.Insert";
    public static final String CANDIDATE_REMOVED_TOPIC = "Candidate.Removed";
    private static final String APPROVAL_INSERT_TOPIC = "Approval.Insert";
    private static final String APPROVAL_UPDATE_TOPIC = "Approval.Update";
    private static final String APPROVAL_REMOVE_TOPIC = "Approval.Remove";
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
        if (dao instanceof CandidateDao) {
            return CANDIDATE_UPDATE_APPLICABLE_TOPIC;
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

    private static Map<String, software.amazon.awssdk.services.dynamodb.model.AttributeValue> getImage(
        DynamodbStreamRecord record) {
        var image = nonNull(record.getDynamodb().getNewImage())
                        ? record.getDynamodb().getNewImage()
                        : record.getDynamodb().getOldImage();
        return image.entrySet()
                   .stream()
                   .collect(Collectors.toMap(Entry::getKey,
                                             attributeValue -> mapToDynamoDbValue(attributeValue.getValue())));
    }

    private static software.amazon.awssdk.services.dynamodb.model.AttributeValue mapToDynamoDbValue(
        AttributeValue value) {
        var json = writeAsString(value);
        return attempt(() -> dtoObjectMapper.readValue(json,
                                                       software.amazon.awssdk.services.dynamodb.model.AttributeValue.serializableBuilderClass())
                                 .build()).orElseThrow();
    }

    private static String writeAsString(AttributeValue attributeValue) {
        return attempt(() -> dtoObjectMapper.writeValueAsString(attributeValue)).orElseThrow();
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
        var message = writeAsString(record);
        var topic = getTopic(operationType, dao);
        var response = snsClient.publish(message, topic);
        LOGGER.info("Published message with id: {} to topic {}", response.messageId(), topic);
    }

    private Dao extractDao(DynamodbStreamRecord record) {
        return DynamoEntryWithRangeKey.parseAttributeValuesMap(getImage(record), Dao.class);
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
