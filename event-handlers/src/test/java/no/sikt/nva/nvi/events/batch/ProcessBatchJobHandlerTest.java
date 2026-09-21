package no.sikt.nva.nvi.events.batch;

import static java.util.UUID.randomUUID;
import static no.sikt.nva.nvi.common.db.PeriodRepositoryFixtures.setupClosedPeriod;
import static no.sikt.nva.nvi.common.db.PeriodRepositoryFixtures.setupFuturePeriod;
import static no.sikt.nva.nvi.common.db.PeriodRepositoryFixtures.setupOpenPeriod;
import static no.sikt.nva.nvi.common.model.CandidateFixtures.setupNumberOfCandidatesForYear;
import static no.sikt.nva.nvi.events.batch.request.BatchJobType.BACKFILL_CREATOR_DATA;
import static no.sikt.nva.nvi.events.batch.request.BatchJobType.REFRESH_CANDIDATES;
import static no.sikt.nva.nvi.events.batch.request.BatchJobType.REFRESH_PERIODS;
import static no.sikt.nva.nvi.events.batch.request.BatchJobType.REPORT_APPROVED_CANDIDATES;
import static no.sikt.nva.nvi.test.TestConstants.LAST_YEAR;
import static no.sikt.nva.nvi.test.TestConstants.NEXT_YEAR;
import static no.sikt.nva.nvi.test.TestConstants.THIS_YEAR;
import static no.unit.nva.testutils.RandomDataGenerator.randomString;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.amazonaws.services.lambda.runtime.Context;
import com.amazonaws.services.lambda.runtime.events.SQSBatchResponse;
import com.amazonaws.services.lambda.runtime.events.SQSEvent;
import java.time.Instant;
import java.util.Collection;
import java.util.List;
import no.sikt.nva.nvi.common.QueueServiceTestUtils;
import no.sikt.nva.nvi.common.TestScenario;
import no.sikt.nva.nvi.common.client.PublicationChannelRetriever;
import no.sikt.nva.nvi.common.service.CandidateService;
import no.sikt.nva.nvi.common.service.model.ApprovalStatus;
import no.sikt.nva.nvi.common.service.model.Candidate;
import no.sikt.nva.nvi.events.batch.message.BatchJobMessage;
import no.sikt.nva.nvi.events.batch.message.CandidateJobMessage;
import no.sikt.nva.nvi.events.batch.message.RefreshPeriodMessage;
import no.sikt.nva.nvi.events.batch.request.BatchJobType;
import no.sikt.nva.nvi.test.uriretriever.FakeUriRetriever;
import no.unit.nva.commons.json.JsonSerializable;
import no.unit.nva.stubs.FakeContext;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

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
            scenario.getS3StorageReaderForExpandedResourcesBucket(),
            new PublicationChannelRetriever(FakeUriRetriever.newInstance()));

    setupClosedPeriod(scenario, LAST_YEAR);
    setupOpenPeriod(scenario, THIS_YEAR);
    setupFuturePeriod(scenario, NEXT_YEAR);

    candidates = setupNumberOfCandidatesForYear(scenario, THIS_YEAR, 3);
  }

  @Test
  void shouldReturnFailedItems() {
    var failingMessage = createMessage(new CandidateJobMessage(randomUUID(), REFRESH_CANDIDATES));
    var input = QueueServiceTestUtils.createEvent(failingMessage);
    var response = handleRequest(input);

    assertThat(response.getBatchItemFailures())
        .singleElement()
        .extracting(SQSBatchResponse.BatchItemFailure::getItemIdentifier)
        .isEqualTo(failingMessage.getMessageId());
  }

  @Test
  void shouldNotFailForWholeBatchIfSingleItemFails() {
    var okMessage = new CandidateJobMessage(candidates.getFirst().identifier(), REFRESH_CANDIDATES);
    var successfulMessage = createMessage(okMessage);
    var failingMessage = createMessage(new CandidateJobMessage(randomUUID(), REFRESH_CANDIDATES));

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

  @Test
  void shouldFailForNonCandidateJobType() {
    var message = new CandidateJobMessage(candidates.getFirst().identifier(), REFRESH_PERIODS);
    var input = QueueServiceTestUtils.createEvent(createMessage(message));

    assertThatThrownBy(() -> handleRequest(input))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining(REFRESH_PERIODS.name());
  }

  @ParameterizedTest
  @EnumSource(names = {"REFRESH_CANDIDATES", "BACKFILL_CREATOR_DATA", "BACKFILL_CHANNEL_METADATA"})
  void shouldWriteCandidateBackForRefreshAndBackfillJobs(BatchJobType jobType) {
    var input = toCandidateJobMessages(candidates, jobType);
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
    var backfillCandidate =
        new CandidateJobMessage(candidates.getFirst().identifier(), BACKFILL_CREATOR_DATA);
    var refreshCandidate =
        new CandidateJobMessage(candidates.get(1).identifier(), REFRESH_CANDIDATES);
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
  void shouldReportApprovedCandidateInClosedPeriod() {
    var candidate = approveCandidate(candidates.getFirst());
    setupClosedPeriod(scenario, THIS_YEAR);

    var startTime = Instant.now();
    var response = handleRequest(List.of(reportMessage(candidate)));
    var endTime = Instant.now();

    assertThat(response.getBatchItemFailures()).isEmpty();
    var reportedCandidate = candidateService.getCandidateByIdentifier(candidate.identifier());
    assertThat(reportedCandidate.isReported()).isTrue();
    assertThat(reportedCandidate.reportedDate()).isBetween(startTime, endTime);
  }

  @Test
  void shouldKeepReportedDateWhenReportingAgain() {
    var candidate = approveCandidate(candidates.getFirst());
    setupClosedPeriod(scenario, THIS_YEAR);

    handleRequest(List.of(reportMessage(candidate)));
    var firstReportedDate =
        candidateService.getCandidateByIdentifier(candidate.identifier()).reportedDate();
    handleRequest(List.of(reportMessage(candidate)));
    var secondReportedDate =
        candidateService.getCandidateByIdentifier(candidate.identifier()).reportedDate();

    assertThat(secondReportedDate).isEqualTo(firstReportedDate);
  }

  @Test
  void shouldNotReportNonApprovedCandidate() {
    setupClosedPeriod(scenario, THIS_YEAR);
    var candidate = candidates.getFirst();

    var response = handleRequest(List.of(reportMessage(candidate)));

    assertThat(response.getBatchItemFailures()).isEmpty();
    assertThat(candidateService.getCandidateByIdentifier(candidate.identifier()).isReported())
        .isFalse();
  }

  @Test
  void shouldNotReportCandidateInOpenPeriod() {
    var candidate = approveCandidate(candidates.getFirst());

    var response = handleRequest(List.of(reportMessage(candidate)));

    assertThat(response.getBatchItemFailures()).isEmpty();
    assertThat(candidateService.getCandidateByIdentifier(candidate.identifier()).isReported())
        .isFalse();
  }

  @Test
  void shouldNotReportNonApplicableCandidate() {
    var candidate = approveCandidate(candidates.getFirst());
    setupClosedPeriod(scenario, THIS_YEAR);
    candidateService.updateCandidate(candidate.copy().withApplicable(false).build());

    var response = handleRequest(List.of(reportMessage(candidate)));

    assertThat(response.getBatchItemFailures()).isEmpty();
    assertThat(candidateService.getCandidateByIdentifier(candidate.identifier()).isReported())
        .isFalse();
  }

  private Candidate approveCandidate(Candidate candidate) {
    var institution = candidate.approvals().keySet().iterator().next();
    scenario.updateApprovalStatus(candidate.identifier(), ApprovalStatus.APPROVED, institution);
    return candidateService.getCandidateByIdentifier(candidate.identifier());
  }

  private static CandidateJobMessage reportMessage(Candidate candidate) {
    return new CandidateJobMessage(candidate.identifier(), REPORT_APPROVED_CANDIDATES);
  }

  private SQSBatchResponse handleRequest(SQSEvent sqsEvent) {
    return handler.handleRequest(sqsEvent, CONTEXT);
  }

  private SQSBatchResponse handleRequest(Collection<? extends BatchJobMessage> batchJobMessages) {
    var messageBatch = createEvent(batchJobMessages);
    return handler.handleRequest(messageBatch, CONTEXT);
  }

  private static List<CandidateJobMessage> toCandidateJobMessages(
      Collection<Candidate> candidates, BatchJobType jobType) {
    return candidates.stream()
        .map(Candidate::identifier)
        .map(identifier -> new CandidateJobMessage(identifier, jobType))
        .toList();
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
