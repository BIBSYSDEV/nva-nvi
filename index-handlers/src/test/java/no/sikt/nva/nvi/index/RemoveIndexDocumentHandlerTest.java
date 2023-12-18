package no.sikt.nva.nvi.index;

import static no.sikt.nva.nvi.test.TestUtils.randomCandidate;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import java.util.UUID;
import no.sikt.nva.nvi.common.db.CandidateDao;
import no.sikt.nva.nvi.index.aws.OpenSearchClient;
import no.sikt.nva.nvi.test.QueueServiceTestUtils;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import software.amazon.awssdk.services.dynamodb.model.OperationType;

class RemoveIndexDocumentHandlerTest {

    @BeforeEach
    void setUp() {
    }

    @Test
    void shouldRemoveIndexDocumentWhenReceivingEvent() {
        var candidate = randomCandidateDao();
        var event = QueueServiceTestUtils.createEvent(candidate, candidate, OperationType.REMOVE);
        var handler = new RemoveIndexDocumentHandler();
        handler.handleRequest(event, null);
        verify(mock(OpenSearchClient.class), Mockito.times(1)).removeDocumentFromIndex(any());
    }

    private static CandidateDao randomCandidateDao() {
        return new CandidateDao(UUID.randomUUID(), randomCandidate(), UUID.randomUUID().toString());
    }
}