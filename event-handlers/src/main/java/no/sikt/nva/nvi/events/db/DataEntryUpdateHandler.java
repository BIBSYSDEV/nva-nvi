package no.sikt.nva.nvi.events.db;

import static no.sikt.nva.nvi.events.db.DynamoDbUtils.getImage;
import static no.unit.nva.commons.json.JsonUtils.dynamoObjectMapper;
import static nva.commons.core.attempt.Try.attempt;
import com.amazonaws.services.lambda.runtime.Context;
import com.amazonaws.services.lambda.runtime.RequestHandler;
import com.amazonaws.services.lambda.runtime.events.DynamodbEvent.DynamodbStreamRecord;
import com.amazonaws.services.lambda.runtime.events.SQSEvent;
import com.amazonaws.services.lambda.runtime.events.SQSEvent.SQSMessage;
import java.util.Objects;
import java.util.StringJoiner;
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

    private static final Logger LOGGER = LoggerFactory.getLogger(DataEntryUpdateHandler.class);
    private static final String APPLICABLE = "Applicable";
    private static final String NOT_APPLICABLE = "NotApplicable";
    private static final String TOPIC_DELIMITER = ".";
    private static final String TOPIC_PREFIX = joinStrings("Nvi", "EntryUpdate");
    private static final String PUBLISHED_MESSAGE = "Published message with id: {} to topic {}";
    private static final String FAILED_TO_PUBLISH_MESSAGE = "Failed to publish message for record {}";
    private static final String FAILED_TO_PARSE_EVENT_MESSAGE = "Failed to map body to DynamodbStreamRecord: {}";
    private static final String SKIPPING_EVENT_MESSAGE = "Skipping event with operation type {} for dao type {}";
    private static final String ERROR_MESSAGE = "Error message: {}";
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

    private static boolean isNotCandidateOrApproval(Dao dao) {
        return !(dao instanceof CandidateDao || dao instanceof ApprovalStatusDao);
    }

    private static String getTopic(OperationType operationType, Dao dao) {
        return switch (operationType) {
            case INSERT, REMOVE -> joinStrings(TOPIC_PREFIX, dao.type(), operationType.toString());
            case MODIFY -> getUpdateTopic(dao);
            default -> throw new IllegalArgumentException("Illegal operation type: " + operationType);
        };
    }

    private static String getUpdateTopic(Dao dao) {
        switch (dao.type()) {
            case CandidateDao.TYPE -> {
                return getCandidateUpdateTopic(dao);
            }
            case ApprovalStatusDao.TYPE -> {
                return joinStrings(TOPIC_PREFIX, dao.type(), OperationType.MODIFY.toString());
            }
            default -> throw new IllegalArgumentException("Illegal dao type: " + dao.type());
        }
    }

    private static String getCandidateUpdateTopic(Dao dao) {
        var candidateDao = (CandidateDao) dao;
        var applicableTopicString = candidateDao.candidate().applicable() ? APPLICABLE : NOT_APPLICABLE;
        return joinStrings(TOPIC_PREFIX, dao.type(), OperationType.MODIFY.toString(), applicableTopicString);
    }

    private static String joinStrings(String... args) {
        var joiner = new StringJoiner(TOPIC_DELIMITER);
        for (String arg : args) {
            joiner.add(arg);
        }
        return joiner.toString();
    }

    private static boolean isUnknownOperationType(OperationType operationType) {
        return OperationType.UNKNOWN_TO_SDK_VERSION.equals(operationType);
    }

    private void publishToTopic(DynamodbStreamRecord record) {
        attempt(() -> extractDaoAndPublish(record)).orElse(failure -> {
            handleFailure(failure, FAILED_TO_PUBLISH_MESSAGE, record.toString());
            return null;
        });
    }

    private NviPublishMessageResponse extractDaoAndPublish(DynamodbStreamRecord record) {
        var operationType = OperationType.fromValue(record.getEventName());
        var dao = extractDao(record);
        if (isNotCandidateOrApproval(dao) || isUnknownOperationType(operationType)) {
            LOGGER.info(SKIPPING_EVENT_MESSAGE, operationType, dao.getClass());
            return null;
        }
        return publish(record, getTopic(operationType, dao));
    }

    private NviPublishMessageResponse publish(DynamodbStreamRecord record, String topic) {
        var response = snsClient.publish(writeAsString(record), topic);
        LOGGER.info(PUBLISHED_MESSAGE, response.messageId(), topic);
        return response;
    }

    private Dao extractDao(DynamodbStreamRecord record) {
        return attempt(() -> DynamoEntryWithRangeKey.parseAttributeValuesMap(getImage(record), Dao.class))
                   .orElseThrow();
    }

    private String writeAsString(DynamodbStreamRecord record) {
        return attempt(() -> dynamoObjectMapper.writeValueAsString(record)).orElseThrow();
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
