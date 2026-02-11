package no.sikt.nva.nvi.events.batch;

import static java.util.UUID.randomUUID;
import static no.sikt.nva.nvi.common.db.PeriodRepositoryFixtures.setupClosedPeriod;
import static no.sikt.nva.nvi.common.db.PeriodRepositoryFixtures.setupFuturePeriod;
import static no.sikt.nva.nvi.common.db.PeriodRepositoryFixtures.setupOpenPeriod;
import static no.sikt.nva.nvi.common.model.CandidateFixtures.setupNumberOfCandidatesForYear;
import static no.sikt.nva.nvi.test.TestConstants.LAST_YEAR;
import static no.sikt.nva.nvi.test.TestConstants.NEXT_YEAR;
import static no.sikt.nva.nvi.test.TestConstants.THIS_YEAR;
import static no.unit.nva.testutils.RandomDataGenerator.randomString;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import com.amazonaws.services.lambda.runtime.Context;
import com.amazonaws.services.lambda.runtime.events.SQSBatchResponse;
import com.amazonaws.services.lambda.runtime.events.SQSEvent;
import java.util.Collection;
import java.util.List;
import no.sikt.nva.nvi.common.CreatorCandidateMigrationService;
import no.sikt.nva.nvi.common.QueueServiceTestUtils;
import no.sikt.nva.nvi.common.TestScenario;
import no.sikt.nva.nvi.common.service.CandidateService;
import no.sikt.nva.nvi.common.service.NviPeriodService;
import no.sikt.nva.nvi.common.service.model.Candidate;
import no.sikt.nva.nvi.events.batch.message.BatchJobMessage;
import no.sikt.nva.nvi.events.batch.message.MigrateCandidateMessage;
import no.sikt.nva.nvi.events.batch.message.RefreshCandidateMessage;
import no.sikt.nva.nvi.events.batch.message.RefreshPeriodMessage;
import no.unit.nva.commons.json.JsonSerializable;
import no.unit.nva.stubs.FakeContext;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class ProcessBatchJobHandlerTest {

  private static final Context CONTEXT = new FakeContext();
  private ProcessBatchJobHandler handler;
  private CandidateService candidateService;
  private NviPeriodService periodService;
  private TestScenario scenario;
  private List<Candidate> candidates;

  @BeforeEach
  void setUp() {
    scenario = new TestScenario();
    candidateService = scenario.getCandidateService();
    periodService = scenario.getPeriodService();
    var candidateMigrationService = mock(CreatorCandidateMigrationService.class);
    doNothing().when(candidateMigrationService).migrateCandidate(any());
    handler =
        new ProcessBatchJobHandler(candidateService, candidateMigrationService, periodService);

    setupClosedPeriod(scenario, LAST_YEAR);
    setupOpenPeriod(scenario, THIS_YEAR);
    setupFuturePeriod(scenario, NEXT_YEAR);

    candidates = setupNumberOfCandidatesForYear(scenario, THIS_YEAR, 3);
  }

  @Test
  void shouldReturnFailedItems() {
    var failingMessage = createMessage(new RefreshCandidateMessage(randomUUID()));
    var input = QueueServiceTestUtils.createEvent(failingMessage);
    var response = handleRequest(input);

    assertThat(response.getBatchItemFailures())
        .singleElement()
        .extracting(SQSBatchResponse.BatchItemFailure::getItemIdentifier)
        .isEqualTo(failingMessage.getMessageId());
  }

  @Test
  void shouldNotFailForWholeBatchIfSingleItemFails() {
    var okMessage = new MigrateCandidateMessage(candidates.getFirst().identifier());
    var successfulMessage = createMessage(okMessage);
    var failingMessage = createMessage(new RefreshCandidateMessage(randomUUID()));

    var input = QueueServiceTestUtils.createEvent(successfulMessage, failingMessage);
    var response = handleRequest(input);

    assertThat(response.getBatchItemFailures())
        .singleElement()
        .extracting(SQSBatchResponse.BatchItemFailure::getItemIdentifier)
        .isEqualTo(failingMessage.getMessageId());
  }

  @Test
  void shouldHandleRefreshCandidateMessage() {
    var input = toRefreshCandidateMessages(candidates);
    var response = handleRequest(input);

    assertThat(response.getBatchItemFailures()).isEmpty();
    for (var candidate : candidates) {
      var updatedCandidate = candidateService.getCandidateByIdentifier(candidate.identifier());
      assertThat(updatedCandidate.revision()).isEqualTo(candidate.revision() + 1);
    }
  }

  @Test
  void shouldPassMigrateCandidateMessageToService() {
    var mockedCandidateMigrationService = mock(CreatorCandidateMigrationService.class);
    handler =
        new ProcessBatchJobHandler(
            candidateService, mockedCandidateMigrationService, periodService);

    var migrateCandidateMessage = new MigrateCandidateMessage(candidates.getFirst().identifier());

    var input = QueueServiceTestUtils.createEvent(createMessage(migrateCandidateMessage));
    var response = handleRequest(input);

    assertThat(response.getBatchItemFailures()).isEmpty();
    verify(mockedCandidateMigrationService, times(1))
        .migrateCandidate(migrateCandidateMessage.candidateIdentifier());
  }

  @Test
  void shouldHandleRefreshPeriodMessage() {
    var initialPeriod = scenario.getPeriodService().getByPublishingYear(THIS_YEAR);

    var input = List.of(new RefreshPeriodMessage(THIS_YEAR));
    var response = handleRequest(input);
    var updatedPeriod = scenario.getPeriodService().getByPublishingYear(THIS_YEAR);

    assertThat(response.getBatchItemFailures()).isEmpty();
    assertThat(updatedPeriod.revision()).isEqualTo(initialPeriod.revision() + 1);
  }

  @Test
  void shouldHandleEventWithMixedMessageTypes() {
    var migrateCandidate = new MigrateCandidateMessage(candidates.getFirst().identifier());
    var refreshCandidate = new RefreshCandidateMessage(candidates.get(1).identifier());
    var refreshPeriod = new RefreshPeriodMessage(LAST_YEAR);

    var input =
        QueueServiceTestUtils.createEvent(
            createMessage(migrateCandidate),
            createMessage(refreshCandidate),
            createMessage(refreshPeriod));
    var response = handleRequest(input);

    assertThat(response.getBatchItemFailures()).isEmpty();
  }

  private SQSBatchResponse handleRequest(SQSEvent sqsEvent) {
    return handler.handleRequest(sqsEvent, CONTEXT);
  }

  private SQSBatchResponse handleRequest(Collection<? extends BatchJobMessage> batchJobMessages) {
    var messageBatch = createEvent(batchJobMessages);
    return handler.handleRequest(messageBatch, CONTEXT);
  }

  private static List<RefreshCandidateMessage> toRefreshCandidateMessages(
      Collection<Candidate> candidates) {
    return candidates.stream()
        .map(Candidate::identifier)
        .map(RefreshCandidateMessage::new)
        .toList();
  }

  private static SQSEvent.SQSMessage createMessage(JsonSerializable message) {
    var queueMessage = new SQSEvent.SQSMessage();
    queueMessage.setBody(message.toJsonString());
    queueMessage.setMessageId(randomString());
    queueMessage.setReceiptHandle(randomString());
    return queueMessage;
  }

  private SQSEvent createEvent(Collection<? extends JsonSerializable> messages) {
    var queueMessages = messages.stream().map(ProcessBatchJobHandlerTest::createMessage).toList();
    var sqsEvent = new SQSEvent();
    sqsEvent.setRecords(queueMessages);
    return sqsEvent;
  }
}
