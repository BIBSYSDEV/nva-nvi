package no.sikt.nva.nvi.index;

import static no.sikt.nva.nvi.test.QueueServiceTestUtils.createEvent;
import static no.sikt.nva.nvi.test.QueueServiceTestUtils.createEventWithDynamoEventMissingIdentifier;
import static no.sikt.nva.nvi.test.QueueServiceTestUtils.createEventWithOneInvalidRecord;
import static no.sikt.nva.nvi.test.TestUtils.randomCandidate;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import java.util.List;
import java.util.UUID;
import no.sikt.nva.nvi.common.db.CandidateDao;
import no.sikt.nva.nvi.index.aws.OpenSearchClient;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import software.amazon.awssdk.services.dynamodb.model.OperationType;

class RemoveIndexDocumentHandlerTest {

    private OpenSearchClient openSearchClient;
    private RemoveIndexDocumentHandler handler;

    @BeforeEach
    void setUp() {
        openSearchClient = mock(OpenSearchClient.class);
        handler = new RemoveIndexDocumentHandler(openSearchClient);
    }

    @Test
    void shouldRemoveIndexDocumentWhenReceivingEvent() {
        var candidate = randomCandidateDao();
        var event = createEvent(candidate, candidate, OperationType.REMOVE);
        handler.handleRequest(event, null);
        verify(openSearchClient, times(1)).removeDocumentFromIndex(candidate.identifier());
    }

    @Test
    void shouldNotFailForWholeBatchFailingToRemoveOneIndexDocument() {
        var candidateToSucceed = randomCandidateDao();
        var candidateToFail = randomCandidateDao();
        var event = createEvent(
            List.of(candidateToSucceed.identifier(), candidateToFail.identifier()));
        mockOpenSearchFailure(candidateToFail);
        handler.handleRequest(event, null);
        verify(openSearchClient, times(1)).removeDocumentFromIndex(candidateToSucceed.identifier());
    }

    @Test
    void shouldNotFailForWholeBatchWhenParsingOneEventFails() {
        var candidate = randomCandidateDao();
        var eventWithOneInvalidRecord = createEventWithOneInvalidRecord(candidate);
        handler.handleRequest(eventWithOneInvalidRecord, null);
        verify(openSearchClient, times(1)).removeDocumentFromIndex(candidate.identifier());
    }

    @Test
    void shouldNotFailForWholeBatchFailingToExtractOneIdentifier() {
        var candidate = randomCandidateDao();
        var eventWithOneInvalidRecord = createEventWithDynamoEventMissingIdentifier(candidate);
        handler.handleRequest(eventWithOneInvalidRecord, null);
        verify(openSearchClient, times(1)).removeDocumentFromIndex(candidate.identifier());
    }

    private static CandidateDao randomCandidateDao() {
        return new CandidateDao(UUID.randomUUID(), randomCandidate(), UUID.randomUUID().toString());
    }

    private void mockOpenSearchFailure(CandidateDao candidateToFail) {
        when(openSearchClient.removeDocumentFromIndex(candidateToFail.identifier()))
            .thenThrow(RuntimeException.class);
    }
}