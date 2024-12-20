package no.sikt.nva.nvi.events.db;

import static java.util.Collections.emptyList;
import static no.sikt.nva.nvi.test.DynamoDbTestUtils.randomDynamoDbEvent;
import static no.sikt.nva.nvi.test.QueueServiceTestUtils.createEvent;
import static no.sikt.nva.nvi.test.QueueServiceTestUtils.createEventWithMessages;
import static no.sikt.nva.nvi.test.QueueServiceTestUtils.createEventWithOneInvalidRecord;
import static no.sikt.nva.nvi.test.QueueServiceTestUtils.createMessage;
import static no.sikt.nva.nvi.test.TestUtils.randomApproval;
import static no.sikt.nva.nvi.test.TestUtils.randomCandidate;
import static no.sikt.nva.nvi.test.TestUtils.randomCandidateBuilder;
import static no.sikt.nva.nvi.test.TestUtils.randomUsername;
import static no.sikt.nva.nvi.test.TestUtils.randomYear;
import static no.unit.nva.testutils.RandomDataGenerator.randomElement;
import static no.unit.nva.testutils.RandomDataGenerator.randomInstant;
import static no.unit.nva.testutils.RandomDataGenerator.randomString;
import static no.unit.nva.testutils.RandomDataGenerator.randomUri;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import com.amazonaws.services.lambda.runtime.Context;
import com.amazonaws.services.lambda.runtime.events.SQSEvent;
import java.util.List;
import java.util.UUID;
import java.util.stream.Stream;
import no.sikt.nva.nvi.common.db.ApprovalStatusDao;
import no.sikt.nva.nvi.common.db.ApprovalStatusDao.DbApprovalStatus;
import no.sikt.nva.nvi.common.db.ApprovalStatusDao.DbStatus;
import no.sikt.nva.nvi.common.db.CandidateDao;
import no.sikt.nva.nvi.common.db.CandidateUniquenessEntryDao;
import no.sikt.nva.nvi.common.db.Dao;
import no.sikt.nva.nvi.common.db.NoteDao;
import no.sikt.nva.nvi.common.db.NoteDao.DbNote;
import no.sikt.nva.nvi.common.db.NviPeriodDao;
import no.sikt.nva.nvi.common.db.NviPeriodDao.DbNviPeriod;
import no.sikt.nva.nvi.test.FakeSqsClient;
import nva.commons.core.Environment;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import software.amazon.awssdk.services.dynamodb.model.OperationType;
import software.amazon.awssdk.services.sns.model.PublishRequest;
import software.amazon.awssdk.services.sns.model.SnsException;

class DataEntryUpdateHandlerTest {

    public static final String CANDIDATE_IDENTIFIER_MESSAGE_ATTRIBUTE = "candidateIdentifier";
    private static final Environment ENVIRONMENT = new Environment();
    private static final Context CONTEXT = mock(Context.class);
    private static final String CANDIDATE_INSERT_TOPIC = ENVIRONMENT.readEnv("TOPIC_CANDIDATE_INSERT");
    private static final String CANDIDATE_UPDATE_TOPIC = ENVIRONMENT.readEnv("TOPIC_CANDIDATE_APPLICABLE_UPDATE");
    private static final String CANDIDATE_UPDATE_NOT_APPLICABLE_TOPIC =
        ENVIRONMENT.readEnv("TOPIC_CANDIDATE_NOT_APPLICABLE_UPDATE");
    private static final String CANDIDATE_REMOVED_TOPIC = ENVIRONMENT.readEnv("TOPIC_CANDIDATE_REMOVE");
    private static final String APPROVAL_INSERT_TOPIC = ENVIRONMENT.readEnv("TOPIC_APPROVAL_INSERT");
    private static final String APPROVAL_UPDATE_TOPIC = ENVIRONMENT.readEnv("TOPIC_APPROVAL_UPDATE");
    private static final String APPROVAL_REMOVE_TOPIC = ENVIRONMENT.readEnv("TOPIC_APPROVAL_REMOVE");
    private FakeNotificationClient snsClient;
    private DataEntryUpdateHandler handler;
    private FakeSqsClient queueClient;

    public static Stream<Arguments> dynamoDbEventProvider() {
        var randomApplicableCandidate = randomCandidateDao();
        var nonApplicableCandidate = nonApplicableCandidateDao();
        var randomApproval = generateRandomApproval();
        return Stream.of(Arguments.of(null, randomApplicableCandidate, CANDIDATE_INSERT_TOPIC, OperationType.INSERT),
                         Arguments.of(randomApplicableCandidate, randomApplicableCandidate, CANDIDATE_UPDATE_TOPIC,
                                      OperationType.MODIFY),
                         Arguments.of(randomApplicableCandidate, null, CANDIDATE_REMOVED_TOPIC, OperationType.REMOVE),
                         Arguments.of(randomApplicableCandidate, nonApplicableCandidate,
                                      CANDIDATE_UPDATE_NOT_APPLICABLE_TOPIC, OperationType.MODIFY),
                         Arguments.of(nonApplicableCandidate, randomApplicableCandidate, CANDIDATE_UPDATE_TOPIC,
                                      OperationType.MODIFY),
                         Arguments.of(nonApplicableCandidate, null,
                                      CANDIDATE_REMOVED_TOPIC, OperationType.REMOVE),
                         Arguments.of(null, generatePendingApproval(), APPROVAL_INSERT_TOPIC, OperationType.INSERT),
                         Arguments.of(randomApproval, randomApproval, APPROVAL_UPDATE_TOPIC, OperationType.MODIFY),
                         Arguments.of(randomApproval, null, APPROVAL_REMOVE_TOPIC, OperationType.REMOVE));
    }

    public static Stream<Arguments> otherDaoTypesProvider() {
        return Stream.of(Arguments.of(randomPeriodDao()),
                         Arguments.of(randomNoteDao()),
                         Arguments.of(candidateUniquenessEntryDao()));
    }

    @BeforeEach
    void setUp() {
        snsClient = new FakeNotificationClient();
        queueClient = new FakeSqsClient();
        handler = new DataEntryUpdateHandler(snsClient, ENVIRONMENT, queueClient);
    }

    @ParameterizedTest(name="shouldConvertDynamoDbEventToDataEntryUpdateEvent {index}")
    @MethodSource("dynamoDbEventProvider")
    void shouldConvertDynamoDbEventToDataEntryUpdateEvent(Dao oldImage, Dao newImage, String expectedTopic,
                                                          OperationType operationType) {
        var event = createEvent(oldImage, newImage, operationType);

        handler.handleRequest(event, CONTEXT);
        var expectedPublishedMessage = createExpectedPublishedMessage(extractFirstMessage(event),
                                                                      expectedTopic);
        assertEquals(expectedPublishedMessage, snsClient.getPublishedMessages().get(0));
    }

    @ParameterizedTest(name="shouldDoNothingWhenReceivingEventWithOtherDaoTypes {index}")
    @MethodSource("otherDaoTypesProvider")
    void shouldDoNothingWhenReceivingEventWithOtherDaoTypes(Dao dao) {
        var event = createEvent(dao, dao, randomElement(OperationType.values()));

        handler.handleRequest(event, CONTEXT);
        assertEquals(0, snsClient.getPublishedMessages().size());
        assertEquals(0, queueClient.getSentMessages().size());
    }

    @Test
    void shouldSendMessageToDlqWhenFailingToPublishEvent() {
        var failingUuid = UUID.randomUUID();
        var eventWithOneInvalidRecord = createEventWithMessages(List.of(createMessage(failingUuid)));
        handler.handleRequest(eventWithOneInvalidRecord, CONTEXT);
        assertEquals(1, queueClient.getSentMessages().size());
        var dlqSendMessageRequest = queueClient.getSentMessages().get(0);
        assertEquals(failingUuid.toString(), dlqSendMessageRequest.messageAttributes().get(
            CANDIDATE_IDENTIFIER_MESSAGE_ATTRIBUTE).stringValue());
        assertEquals("String", dlqSendMessageRequest.messageAttributes().get(
            CANDIDATE_IDENTIFIER_MESSAGE_ATTRIBUTE).dataType());
    }

    @Test
    void shouldSendMessageToDlqWithoutCandidateIdentifierFailingToPublishEventAndUnableToExtractRecordIdentifier() {
        var event = createEvent(randomDynamoDbEvent().getRecords().get(0));
        var fakeSnsClient = mock(FakeNotificationClient.class);
        when(fakeSnsClient.publish(any(), any())).thenThrow(SnsException.class);
        var handler = new DataEntryUpdateHandler(fakeSnsClient, new Environment(), queueClient);
        handler.handleRequest(event, CONTEXT);
        assertEquals(1, queueClient.getSentMessages().size());
    }

    @Test
    void shouldSendMessageToDlqWhenFailingToParseDynamoDbEvent() {
        var eventWithOneInvalidRecord = createEventWithOneInvalidRecord(randomCandidateDao());
        handler.handleRequest(eventWithOneInvalidRecord, CONTEXT);
        assertEquals(1, queueClient.getSentMessages().size());
    }

    @Test
    void shouldNotFailForWholeBatchWhenFailingToParseOneDynamoDbEvent() {
        var eventWithOneInvalidRecord = createEventWithOneInvalidRecord(randomCandidateDao());
        handler.handleRequest(eventWithOneInvalidRecord, CONTEXT);
        assertEquals(1, snsClient.getPublishedMessages().size());
    }

    @Test
    void shouldNotFailForWholeBatchWhenFailingExtractDaoForOneRecord() {
        var dao = randomCandidateDao();
        var eventWithOneInvalidRecord = createEventWithMessages(
            List.of(createMessage(dao, dao, OperationType.INSERT), createMessage(UUID.randomUUID())));
        handler.handleRequest(eventWithOneInvalidRecord, CONTEXT);
        assertEquals(1, snsClient.getPublishedMessages().size());
    }

    @Test
    void shouldNotFailWhenParsingEventWithEmptyList() {
        var validDao = randomCandidateDao();
        var invalidDbCandidate = validDao.candidate()
                                         .copy()
                                         .points(emptyList())
                                         .build();
        var dao = validDao.copy()
                          .candidate(invalidDbCandidate)
                          .build();
        var eventWithOneInvalidRecord = createEventWithMessages(
            List.of(createMessage(dao, dao, OperationType.INSERT), createMessage(UUID.randomUUID())));
        handler.handleRequest(eventWithOneInvalidRecord, CONTEXT);
        assertEquals(1,
                     snsClient.getPublishedMessages()
                              .size());
    }

    private static CandidateUniquenessEntryDao candidateUniquenessEntryDao() {
        return new CandidateUniquenessEntryDao(UUID.randomUUID().toString());
    }

    private static NoteDao randomNoteDao() {
        return new NoteDao(UUID.randomUUID(),
                           new DbNote(UUID.randomUUID(), randomUsername(), randomString(), randomInstant()),
                           UUID.randomUUID().toString());
    }

    private static NviPeriodDao randomPeriodDao() {
        return new NviPeriodDao(UUID.randomUUID().toString(),
                                new DbNviPeriod(randomUri(), randomString(), randomInstant(), randomInstant(),
                                                randomUsername(), randomUsername()),
                                UUID.randomUUID().toString());
    }

    private static ApprovalStatusDao generateRandomApproval() {
        return new ApprovalStatusDao(UUID.randomUUID(), randomApproval(), UUID.randomUUID().toString());
    }

    private static String extractFirstMessage(SQSEvent event) {
        return event.getRecords().get(0).getBody();
    }

    private static PublishRequest createExpectedPublishedMessage(String message, String topic) {
        return PublishRequest.builder()
                   .message(message)
                   .topicArn(topic)
                   .build();
    }

    private static CandidateDao randomCandidateDao() {
        return new CandidateDao(UUID.randomUUID(), randomCandidate(), UUID.randomUUID().toString(), randomYear());
    }

    private static CandidateDao nonApplicableCandidateDao() {
        return new CandidateDao(UUID.randomUUID(), randomCandidateBuilder(false).build(),
                                UUID.randomUUID().toString(), randomString());
    }

    private static ApprovalStatusDao generatePendingApproval() {
        return new ApprovalStatusDao(UUID.randomUUID(),
                                     new DbApprovalStatus(randomUri(),
                                                          DbStatus.PENDING,
                                                          null,
                                                          null,
                                                          null,
                                                          null),
                                     UUID.randomUUID().toString());
    }
}
