package no.sikt.nva.nvi.test;

import static no.sikt.nva.nvi.test.DynamoDbTestUtils.eventWithCandidate;
import static no.sikt.nva.nvi.test.DynamoDbTestUtils.eventWithCandidateIdentifier;
import static no.sikt.nva.nvi.test.DynamoDbTestUtils.mapToString;
import com.amazonaws.services.lambda.runtime.events.SQSEvent;
import com.amazonaws.services.lambda.runtime.events.SQSEvent.SQSMessage;
import java.util.List;
import java.util.UUID;
import no.sikt.nva.nvi.common.db.CandidateDao;
import no.sikt.nva.nvi.common.service.model.Candidate;

public final class QueueServiceTestUtils {

    private QueueServiceTestUtils() {
    }

    public static SQSEvent createEvent(UUID candidateIdentifier) {
        var sqsEvent = new SQSEvent();
        var message = createMessage(candidateIdentifier);
        sqsEvent.setRecords(List.of(message));
        return sqsEvent;
    }

    public static SQSEvent createEvent(CandidateDao candidate) {
        var sqsEvent = new SQSEvent();
        var message = createMessage(candidate);
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

    public static SQSEvent createEventWithOneInvalidRecord(UUID candidateIdentifier) {
        var sqsEvent = new SQSEvent();
        var message = createMessage(candidateIdentifier);
        sqsEvent.setRecords(List.of(message, invalidSqsMessage()));
        return sqsEvent;
    }

    private static SQSMessage createMessage(UUID candidateIdentifier) {
        var message = new SQSMessage();
        message.setBody(generateSingleDynamoDbEventRecord(candidateIdentifier));
        return message;
    }

    private static SQSMessage createMessage(CandidateDao candidate) {
        var message = new SQSMessage();
        message.setBody(generateSingleDynamoDbEventRecord(candidate));
        return message;
    }

    private static String generateSingleDynamoDbEventRecord(UUID candidateIdentifier) {
        return mapToString(eventWithCandidateIdentifier(candidateIdentifier).getRecords().get(0));
    }

    private static String generateSingleDynamoDbEventRecord(CandidateDao candidate) {
        return mapToString(eventWithCandidate(candidate).getRecords().get(0));
    }

    private static SQSMessage invalidSqsMessage() {
        var message = new SQSMessage();
        message.setBody("invalid indexDocument");
        return message;
    }
}
