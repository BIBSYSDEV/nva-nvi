package no.sikt.nva.nvi.index;

import static no.sikt.nva.nvi.common.QueueServiceTestUtils.createEvent;
import static no.sikt.nva.nvi.common.QueueServiceTestUtils.createEventWithOneInvalidRecord;
import static no.sikt.nva.nvi.common.db.CandidateDaoFixtures.randomApplicableCandidateDao;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;
import no.sikt.nva.nvi.common.QueueServiceTestUtils;
import no.sikt.nva.nvi.common.db.CandidateDao;
import no.sikt.nva.nvi.index.aws.CandidateSearchClient;
import no.sikt.nva.nvi.index.aws.MultiIndexWriter;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import software.amazon.awssdk.services.dynamodb.model.OperationType;

class RemoveIndexDocumentHandlerTest {

  private CandidateSearchClient searchClient;
  private RemoveIndexDocumentHandler handler;

  @BeforeEach
  void setUp() {
    searchClient = mock(CandidateSearchClient.class);
    var multiIndexWriter = new MultiIndexWriter(List.of(searchClient));
    handler = new RemoveIndexDocumentHandler(multiIndexWriter);
  }

  @Test
  void shouldRemoveIndexDocumentWhenReceivingEvent() {
    var candidate = randomApplicableCandidateDao();
    var event = createEvent(candidate, candidate, OperationType.REMOVE);
    handler.handleRequest(event, null);
    verify(searchClient, times(1)).removeDocumentFromIndex(candidate.identifier());
  }

  @Test
  void shouldNotFailForWholeBatchWhenFailingToRemoveOneIndexDocument() {
    var candidateToSucceed = randomApplicableCandidateDao();
    var candidateToFail = randomApplicableCandidateDao();
    var event = createEvent(candidateToSucceed.identifier(), candidateToFail.identifier());
    mockOpenSearchFailure(candidateToFail);
    handler.handleRequest(event, null);
    verify(searchClient, times(1)).removeDocumentFromIndex(candidateToSucceed.identifier());
  }

  @Test
  void shouldNotFailForWholeBatchWhenParsingOneEventFails() {
    var candidate = randomApplicableCandidateDao();
    var eventWithOneInvalidRecord = createEventWithOneInvalidRecord(candidate);
    handler.handleRequest(eventWithOneInvalidRecord, null);
    verify(searchClient, times(1)).removeDocumentFromIndex(candidate.identifier());
  }

  @Test
  void shouldNotFailForWholeBatchWhenFailingToExtractOneIdentifier() {
    var candidate = randomApplicableCandidateDao();
    var eventWithOneInvalidRecord =
        QueueServiceTestUtils.createEventWithOneRecordMissingIdentifier(candidate);
    handler.handleRequest(eventWithOneInvalidRecord, null);
    verify(searchClient, times(1)).removeDocumentFromIndex(candidate.identifier());
  }

  private void mockOpenSearchFailure(CandidateDao candidateToFail) {
    when(searchClient.removeDocumentFromIndex(candidateToFail.identifier()))
        .thenThrow(RuntimeException.class);
  }
}
