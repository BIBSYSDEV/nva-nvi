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
import static org.junit.jupiter.params.provider.Arguments.argumentSet;

import com.amazonaws.services.lambda.runtime.Context;
import com.amazonaws.services.lambda.runtime.events.SQSBatchResponse;
import com.amazonaws.services.lambda.runtime.events.SQSEvent;
import java.util.Collection;
import java.util.List;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Stream;
import no.sikt.nva.nvi.common.QueueServiceTestUtils;
import no.sikt.nva.nvi.common.TestScenario;
import no.sikt.nva.nvi.common.service.CandidateService;
import no.sikt.nva.nvi.common.service.model.ApprovalStatus;
import no.sikt.nva.nvi.common.service.model.Candidate;
import no.sikt.nva.nvi.events.batch.message.BackfillCreatorDataMessage;
import no.sikt.nva.nvi.events.batch.message.BatchJobMessage;
import no.sikt.nva.nvi.events.batch.message.RefreshCandidateMessage;
import no.sikt.nva.nvi.events.batch.message.RefreshPeriodMessage;
import no.sikt.nva.nvi.events.batch.message.ReportCandidateMessage;
import no.unit.nva.commons.json.JsonSerializable;
import no.unit.nva.stubs.FakeContext;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

class ProcessBatchJobHandlerTest {

  private static final Context CONTEXT = new FakeContext();
  private ProcessBatchJobHandler handler;
  private CandidateService candidateService;
  private TestScenario scenario;
  private List<Candidate> candidates;

  @BeforeEach
  void setUp() {
    scenario = new TestScenario();
    candidateService = scenario.getCandidateService();
    handler =
        new ProcessBatchJobHandler(
            candidateService,
            scenario.getPeriodService(),
            scenario.getS3StorageReaderForExpandedResourcesBucket());

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
    var okMessage = new BackfillCreatorDataMessage(candidates.getFirst().identifier());
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
  void shouldFailMessageWithUnknownType() {
    var legacyBody =
        """
        { "type": "MIGRATE_CANDIDATE", "candidateIdentifier": "%s" }
        """
            .formatted(candidates.getFirst().identifier());
    var legacyMessage = createMessage(legacyBody);

    var response = handleRequest(QueueServiceTestUtils.createEvent(legacyMessage));

    assertThat(response.getBatchItemFailures())
        .singleElement()
        .extracting(SQSBatchResponse.BatchItemFailure::getItemIdentifier)
        .isEqualTo(legacyMessage.getMessageId());
  }

  @ParameterizedTest
  @MethodSource("candidateWriteBackMessages")
  void shouldWriteCandidateBackForRefreshAndBackfillMessages(
      Function<UUID, BatchJobMessage> messageFactory) {
    var input = candidates.stream().map(Candidate::identifier).map(messageFactory).toList();
    var response = handleRequest(input);

    assertThat(response.getBatchItemFailures()).isEmpty();
    for (var candidate : candidates) {
      var updatedCandidate = candidateService.getCandidateByIdentifier(candidate.identifier());
      assertThat(updatedCandidate.revision()).isEqualTo(candidate.revision() + 1);
    }
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
    var backfillCandidate = new BackfillCreatorDataMessage(candidates.getFirst().identifier());
    var refreshCandidate = new RefreshCandidateMessage(candidates.get(1).identifier());
    var refreshPeriod = new RefreshPeriodMessage(LAST_YEAR);

    var input =
        QueueServiceTestUtils.createEvent(
            createMessage(backfillCandidate),
            createMessage(refreshCandidate),
            createMessage(refreshPeriod));
    var response = handleRequest(input);

    assertThat(response.getBatchItemFailures()).isEmpty();
  }

  @Test
  void shouldHandleReportCandidateMessage() {
    var candidate = candidates.getFirst();
    var institution = candidate.approvals().keySet().iterator().next();
    scenario.updateApprovalStatus(candidate.identifier(), ApprovalStatus.APPROVED, institution);
    setupClosedPeriod(scenario, THIS_YEAR);

    var reportMessage = new ReportCandidateMessage(candidate.identifier());
    var input = QueueServiceTestUtils.createEvent(createMessage(reportMessage));
    var response = handleRequest(input);

    assertThat(response.getBatchItemFailures()).isEmpty();
    var updatedCandidate = candidateService.getCandidateByIdentifier(candidate.identifier());
    assertThat(updatedCandidate.isReported()).isTrue();
  }

  @Test
  void shouldSkipNonApprovedCandidateWithoutFailingBatch() {
    setupClosedPeriod(scenario, THIS_YEAR);
    var candidate = candidates.getFirst();

    var reportMessage = new ReportCandidateMessage(candidate.identifier());
    var input = QueueServiceTestUtils.createEvent(createMessage(reportMessage));
    var response = handleRequest(input);

    assertThat(response.getBatchItemFailures()).isEmpty();
    var updatedCandidate = candidateService.getCandidateByIdentifier(candidate.identifier());
    assertThat(updatedCandidate.isReported()).isFalse();
  }

  private static Stream<Arguments> candidateWriteBackMessages() {
    return Stream.of(
        argumentSet("Refresh", (Function<UUID, BatchJobMessage>) RefreshCandidateMessage::new),
        argumentSet(
            "Backfill creator data",
            (Function<UUID, BatchJobMessage>) BackfillCreatorDataMessage::new));
  }

  private SQSBatchResponse handleRequest(SQSEvent sqsEvent) {
    return handler.handleRequest(sqsEvent, CONTEXT);
  }

  private SQSBatchResponse handleRequest(Collection<? extends BatchJobMessage> batchJobMessages) {
    var messageBatch = createEvent(batchJobMessages);
    return handler.handleRequest(messageBatch, CONTEXT);
  }

  private static SQSEvent.SQSMessage createMessage(JsonSerializable message) {
    return createMessage(message.toJsonString());
  }

  private static SQSEvent.SQSMessage createMessage(String body) {
    var queueMessage = new SQSEvent.SQSMessage();
    queueMessage.setBody(body);
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
