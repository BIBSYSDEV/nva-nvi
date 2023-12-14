package no.sikt.nva.nvi.events.db;

import static no.sikt.nva.nvi.test.QueueServiceTestUtils.createEvent;
import static no.sikt.nva.nvi.test.TestUtils.randomCandidate;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;
import com.amazonaws.services.lambda.runtime.Context;
import com.amazonaws.services.lambda.runtime.events.SQSEvent;
import java.util.UUID;
import java.util.stream.Stream;
import no.sikt.nva.nvi.common.db.CandidateDao;
import no.sikt.nva.nvi.common.db.Dao;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import software.amazon.awssdk.services.dynamodb.model.OperationType;
import software.amazon.awssdk.services.sns.model.PublishRequest;

public class DataEntryUpdateHandlerTest {

    public static final Context CONTEXT = mock(Context.class);
    public static final String CANDIDATE_INSERT_TOPIC = "Candidate.Insert";
    public static final String CANDIDATE_REMOVED_TOPIC = "Candidate.Removed";
    private static final String CANDIDATE_UPDATE_TOPIC = "Candidate.Update.Applicable";
    private FakeNotificationClient snsClient;
    private DataEntryUpdateHandler handler;

    public static Stream<Arguments> dynamoDbEventProvider() {
        var randomCandidate = randomCandidateDao();
        return Stream.of(Arguments.of(randomCandidate, randomCandidate, CANDIDATE_UPDATE_TOPIC, OperationType.MODIFY),
                         Arguments.of(null, randomCandidate, CANDIDATE_INSERT_TOPIC, OperationType.INSERT),
                         Arguments.of(randomCandidate, null, CANDIDATE_REMOVED_TOPIC, OperationType.REMOVE));
    }

    @BeforeEach
    void setUp() {
        snsClient = new FakeNotificationClient();
        handler = new DataEntryUpdateHandler(snsClient);
    }

    //TODO: Parameterize this test to test with all Dao types
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
