package no.sikt.nva.nvi.events.db;

import static no.sikt.nva.nvi.test.QueueServiceTestUtils.createEvent;
import static no.sikt.nva.nvi.test.TestUtils.randomApproval;
import static no.sikt.nva.nvi.test.TestUtils.randomCandidate;
import static no.sikt.nva.nvi.test.TestUtils.randomUsername;
import static no.unit.nva.testutils.RandomDataGenerator.randomElement;
import static no.unit.nva.testutils.RandomDataGenerator.randomInstant;
import static no.unit.nva.testutils.RandomDataGenerator.randomString;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;
import com.amazonaws.services.lambda.runtime.Context;
import com.amazonaws.services.lambda.runtime.events.SQSEvent;
import java.util.UUID;
import java.util.stream.Stream;
import no.sikt.nva.nvi.common.db.ApprovalStatusDao;
import no.sikt.nva.nvi.common.db.CandidateDao;
import no.sikt.nva.nvi.common.db.Dao;
import no.sikt.nva.nvi.common.db.NoteDao;
import no.sikt.nva.nvi.common.db.NoteDao.DbNote;
import no.sikt.nva.nvi.common.db.NviPeriodDao;
import no.sikt.nva.nvi.common.db.NviPeriodDao.DbNviPeriod;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import software.amazon.awssdk.services.dynamodb.model.OperationType;
import software.amazon.awssdk.services.sns.model.PublishRequest;

public class DataEntryUpdateHandlerTest {

    private static final Context CONTEXT = mock(Context.class);
    private static final String CANDIDATE_INSERT_TOPIC = "Candidate.Insert";
    private static final String CANDIDATE_REMOVED_TOPIC = "Candidate.Removed";
    private static final String APPROVAL_INSERT_TOPIC = "Approval.Insert";
    private static final String APPROVAL_UPDATE_TOPIC = "Approval.Update";
    private static final String APPROVAL_REMOVE_TOPIC = "Approval.Remove";
    private static final String CANDIDATE_UPDATE_TOPIC = "Candidate.Update.Applicable";
    private FakeNotificationClient snsClient;
    private DataEntryUpdateHandler handler;

    public static Stream<Arguments> dynamoDbEventProvider() {
        var randomCandidate = randomCandidateDao();
        var randomApproval = generateRandomApproval();
        return Stream.of(Arguments.of(null, randomCandidate, CANDIDATE_INSERT_TOPIC, OperationType.INSERT),
                         Arguments.of(randomCandidate, randomCandidate, CANDIDATE_UPDATE_TOPIC, OperationType.MODIFY),
                         Arguments.of(randomCandidate, null, CANDIDATE_REMOVED_TOPIC, OperationType.REMOVE),
                         Arguments.of(null, randomApproval, APPROVAL_INSERT_TOPIC, OperationType.INSERT),
                         Arguments.of(randomApproval, randomApproval, APPROVAL_UPDATE_TOPIC, OperationType.MODIFY),
                         Arguments.of(randomApproval, null, APPROVAL_REMOVE_TOPIC, OperationType.REMOVE));
    }

    public static Stream<Arguments> otherDaoTypesProvider() {
        return Stream.of(Arguments.of(randomPeriodDao()),
                         Arguments.of(randomNoteDao()));
    }

    @BeforeEach
    void setUp() {
        snsClient = new FakeNotificationClient();
        handler = new DataEntryUpdateHandler(snsClient);
    }

    @ParameterizedTest
    @MethodSource("dynamoDbEventProvider")
    void shouldConvertDynamoDbEventToDataEntryUpdateEvent(Dao oldImage, Dao newImage, String expectedTopic,
                                                          OperationType operationType) {
        var event = createEvent(oldImage, newImage, operationType);

        handler.handleRequest(event, CONTEXT);
        var expectedPublishedMessage = createExpectedPublishedMessage(extractFirstMessage(event),
                                                                      expectedTopic);
        assertEquals(expectedPublishedMessage, snsClient.getPublishedMessages().get(0));
    }

    @ParameterizedTest
    @MethodSource("otherDaoTypesProvider")
    void shouldDoNothingWhenReceivingEventWithOtherDaoTypes(Dao dao) {
        var event = createEvent(dao, dao, randomElement(OperationType.values()));

        handler.handleRequest(event, CONTEXT);
        assertEquals(0, snsClient.getPublishedMessages().size());
    }

    private static NoteDao randomNoteDao() {
        return new NoteDao(UUID.randomUUID(),
                           new DbNote(UUID.randomUUID(), randomUsername(), randomString(), randomInstant()),
                           UUID.randomUUID().toString());
    }

    private static NviPeriodDao randomPeriodDao() {
        return new NviPeriodDao(UUID.randomUUID().toString(),
                                new DbNviPeriod(randomString(), randomInstant(), randomInstant(),
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
        return new CandidateDao(UUID.randomUUID(), randomCandidate(), UUID.randomUUID().toString());
    }
}
