package no.sikt.nva.nvi.events.db;

import static no.sikt.nva.nvi.test.QueueServiceTestUtils.createEvent;
import static no.sikt.nva.nvi.test.TestUtils.randomCandidate;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;
import com.amazonaws.services.lambda.runtime.Context;
import com.amazonaws.services.lambda.runtime.events.SQSEvent;
import java.util.UUID;
import no.sikt.nva.nvi.common.db.CandidateDao;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import software.amazon.awssdk.services.sns.model.PublishRequest;

public class DataEntryUpdateHandlerTest {

    public static final Context CONTEXT = mock(Context.class);
    private static final String CANDIDATE_UPDATE_TOPIC = "Candidate.Update.Applicable";
    private FakeNotificationClient snsClient;
    private DataEntryUpdateHandler handler;

    @BeforeEach
    void setUp() {
        snsClient = new FakeNotificationClient();
        handler = new DataEntryUpdateHandler(snsClient);
    }

    //TODO: Parameterize this test to test with all Dao types
    @Test
    void shouldConvertDynamoDbEventToDataEntryUpdateEvent() {
        var candidate = randomCandidateDao();
        var event = createEvent(candidate);

        handler.handleRequest(event, CONTEXT);
        var expectedPublishedMessage = createExpectedPublishedMessage(extractFirstMessage(event)
        );
        assertEquals(expectedPublishedMessage, snsClient.getPublishedMessages().get(0));
    }

    private static String extractFirstMessage(SQSEvent event) {
        return event.getRecords().get(0).getBody();
    }

    private static PublishRequest createExpectedPublishedMessage(String message) {
        return PublishRequest.builder()
                   .message(message)
                   .topicArn(DataEntryUpdateHandlerTest.CANDIDATE_UPDATE_TOPIC)
                   .build();
    }

    private CandidateDao randomCandidateDao() {
        return new CandidateDao(UUID.randomUUID(), randomCandidate(), UUID.randomUUID().toString());
    }
}
