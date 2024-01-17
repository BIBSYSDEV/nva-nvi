package no.sikt.nva.nvi.test;

import static no.sikt.nva.nvi.test.DynamoDbTestUtils.dynamoDbEventWithEmptyPayload;
import static no.sikt.nva.nvi.test.DynamoDbTestUtils.eventWithCandidate;
import static no.sikt.nva.nvi.test.DynamoDbTestUtils.eventWithCandidateIdentifier;
import static no.sikt.nva.nvi.test.DynamoDbTestUtils.mapToString;
import com.amazonaws.services.lambda.runtime.events.DynamodbEvent.DynamodbStreamRecord;
import com.amazonaws.services.lambda.runtime.events.SQSEvent;
import com.amazonaws.services.lambda.runtime.events.SQSEvent.SQSMessage;
import java.util.List;
import java.util.UUID;
import no.sikt.nva.nvi.common.db.CandidateDao;
import no.sikt.nva.nvi.common.db.Dao;
import software.amazon.awssdk.services.dynamodb.model.OperationType;

public final class QueueServiceTestUtils {

    private QueueServiceTestUtils() {
    }

    public static SQSEvent createEvent(UUID candidateIdentifier) {
        var sqsEvent = new SQSEvent();
        var message = createMessage(candidateIdentifier);
        sqsEvent.setRecords(List.of(message));
        return sqsEvent;
    }

    public static SQSEvent createEvent(List<UUID> candidateIdentifiers) {
        var sqsEvent = new SQSEvent();
        var records = candidateIdentifiers.stream().map(
            QueueServiceTestUtils::createMessage).toList();
        sqsEvent.setRecords(records);
        return sqsEvent;
    }

    public static SQSEvent createEvent(Dao oldImage, Dao newImage, OperationType operationType) {
        var sqsEvent = new SQSEvent();
        var message = createMessage(oldImage, newImage, operationType);
        sqsEvent.setRecords(List.of(message));
        return sqsEvent;
    }

    public static SQSEvent createEvent(DynamodbStreamRecord streamRecord) {
        var sqsEvent = new SQSEvent();
        var message = new SQSMessage();
        message.setBody(mapToString(streamRecord));
        sqsEvent.setRecords(List.of(message));
        return sqsEvent;
    }

    public static SQSEvent createEventWithDynamodbRecords(List<DynamodbStreamRecord> streamRecords) {
        var sqsEvent = new SQSEvent();
        var messages = streamRecords.stream().map(QueueServiceTestUtils::createMessage).toList();
        sqsEvent.setRecords(messages);
        return sqsEvent;
    }

    public static SQSEvent createEventWithMessages(List<SQSMessage> messages) {
        var sqsEvent = new SQSEvent();
        sqsEvent.setRecords(messages);
        return sqsEvent;
    }

    public static SQSEvent createEventWithOneInvalidRecord(UUID candidateIdentifier) {
        var sqsEvent = new SQSEvent();
        var message = createMessage(candidateIdentifier);
        sqsEvent.setRecords(List.of(message, invalidSqsMessage()));
        return sqsEvent;
    }

    public static SQSEvent createEventWithOneInvalidRecord(CandidateDao dao) {
        var sqsEvent = new SQSEvent();
        var message = createMessage(null, dao, OperationType.INSERT);
        sqsEvent.setRecords(List.of(message, invalidSqsMessage()));
        return sqsEvent;
    }

    public static SQSEvent createEventWithDynamoEventMissingIdentifier(CandidateDao candidate) {
        var sqsEvent = new SQSEvent();
        var message = createMessage(null, candidate, OperationType.INSERT);
        sqsEvent.setRecords(List.of(message, messageWithoutIdentifier()));
        return sqsEvent;
    }

    public static SQSMessage invalidSqsMessage() {
        var message = new SQSMessage();
        message.setBody("invalid indexDocument");
        return message;
    }

    public static SQSMessage createMessage(UUID candidateIdentifier) {
        var message = new SQSMessage();
        message.setBody(generateSingleDynamoDbEventRecord(candidateIdentifier));
        return message;
    }

    public static SQSMessage createMessage(Dao oldImage, Dao newImage, OperationType operationType) {
        var message = new SQSMessage();
        message.setBody(generateSingleDynamoDbEventRecordWithEmptyPayload(oldImage, newImage, operationType));
        return message;
    }

    private static SQSMessage createMessage(DynamodbStreamRecord record) {
        var message = new SQSMessage();
        message.setBody(mapToString(record));
        return message;
    }

    private static SQSMessage messageWithoutIdentifier() {
        var message = new SQSMessage();
        message.setBody(generateSingleDynamoDbEventRecordWithEmptyPayload());
        return message;
    }

    private static String generateSingleDynamoDbEventRecord(UUID candidateIdentifier) {
        return mapToString(eventWithCandidateIdentifier(candidateIdentifier).getRecords().get(0));
    }

    private static String generateSingleDynamoDbEventRecordWithEmptyPayload() {
        return mapToString(dynamoDbEventWithEmptyPayload().getRecords().get(0));
    }

    private static String generateSingleDynamoDbEventRecordWithEmptyPayload(Dao oldImage, Dao newImage,
                                                                            OperationType operationType) {
        return mapToString(eventWithCandidate(oldImage, newImage,
                                              operationType).getRecords().get(0));
    }
}
