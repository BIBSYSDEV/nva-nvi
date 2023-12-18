package no.sikt.nva.nvi.index;

import static no.sikt.nva.nvi.test.QueueServiceTestUtils.createEvent;
import static no.sikt.nva.nvi.test.TestUtils.randomCandidate;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import java.util.UUID;
import no.sikt.nva.nvi.common.db.CandidateDao;
import no.sikt.nva.nvi.index.aws.OpenSearchClient;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import software.amazon.awssdk.services.dynamodb.model.OperationType;

class RemoveIndexDocumentHandlerTest {

    @BeforeEach
    void setUp() {
    }

    @Test
    void shouldRemoveIndexDocumentWhenReceivingEvent() {
        var candidate = randomCandidateDao();
        var event = createEvent(candidate, candidate, OperationType.REMOVE);
        var openSearchClient = mock(OpenSearchClient.class);
        var handler = new RemoveIndexDocumentHandler(openSearchClient);
        handler.handleRequest(event, null);
        verify(openSearchClient, times(1)).removeDocumentFromIndex(any());
    }

    private static CandidateDao randomCandidateDao() {
        return new CandidateDao(UUID.randomUUID(), randomCandidate(), UUID.randomUUID().toString());
    }
}