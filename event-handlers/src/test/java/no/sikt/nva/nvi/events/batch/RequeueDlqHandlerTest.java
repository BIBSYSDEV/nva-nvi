package no.sikt.nva.nvi.events.batch;

import static java.util.Collections.emptyList;
import static java.util.UUID.randomUUID;
import static no.sikt.nva.nvi.common.db.DbCandidateFixtures.randomCandidateBuilder;
import static no.sikt.nva.nvi.common.db.DbPointCalculationFixtures.randomPointCalculationBuilder;
import static no.sikt.nva.nvi.common.db.DbPublicationDetailsFixtures.randomPublicationBuilder;
import static no.sikt.nva.nvi.common.model.CandidateFixtures.setupRandomApplicableCandidate;
import static no.sikt.nva.nvi.events.batch.RequeueDlqTestUtils.generateMessageForCandidate;
import static no.sikt.nva.nvi.events.batch.RequeueDlqTestUtils.setupSqsClient;
import static no.unit.nva.testutils.RandomDataGenerator.randomString;
import static no.unit.nva.testutils.RandomDataGenerator.randomUri;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.amazonaws.services.lambda.runtime.Context;
import com.fasterxml.jackson.core.JsonProcessingException;
import java.util.Collection;
import java.util.List;
import java.util.stream.IntStream;
import no.sikt.nva.nvi.common.TestScenario;
import no.sikt.nva.nvi.common.db.CandidateDao;
import no.sikt.nva.nvi.common.db.model.DbPublicationChannel;
import no.sikt.nva.nvi.common.model.ScientificValue;
import no.sikt.nva.nvi.common.queue.NviQueueClient;
import no.sikt.nva.nvi.common.service.CandidateService;
import no.unit.nva.commons.json.JsonUtils;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import software.amazon.awssdk.services.sqs.SqsClient;
import software.amazon.awssdk.services.sqs.model.Message;
import software.amazon.awssdk.services.sqs.model.ReceiveMessageRequest;
import software.amazon.awssdk.services.sqs.model.ReceiveMessageResponse;

class RequeueDlqHandlerTest {

  public static final Context CONTEXT = mock(Context.class);
  private static final String DLQ_URL = "https://some-sqs-url";
  private static final String FIRST_BATCH = "firstBatch";
  private static final int DEFAULT_BATCH_SIZE = 10;
  private static final int MAX_RETRY_ATTEMPTS = 5;
  private RequeueDlqHandler handler;
  private SqsClient sqsClient;
  private CandidateService candidateService;
  private TestScenario scenario;

  @BeforeEach
  void setUp() {
    scenario = new TestScenario();
    sqsClient = setupSqsClient();
    candidateService = scenario.getCandidateService();
    handler = new RequeueDlqHandler(new NviQueueClient(sqsClient), DLQ_URL, candidateService);
  }

  @Test
  void shouldRequeueSingleBatch() {
    var batch = createCandidateMessages(FIRST_BATCH, 1);
    setupMockEvents(batch);

    var response = handler.handleRequest(new RequeueDlqInput(1), CONTEXT);

    assertThat(getSuccessCount(response)).isOne();
    assertThat(response.failedBatchesCount()).isZero();
  }

  @Test
  void shouldWriteToDynamodbWhenProcessing() {
    var candidate = setupRandomApplicableCandidate(scenario);
    var message = generateMessageForCandidate(randomString(), candidate.identifier());
    setupMockEvents(List.of(message));

    handler.handleRequest(new RequeueDlqInput(1), CONTEXT);

    var updatedCandidate = candidateService.getByIdentifier(candidate.identifier());
    assertThat(updatedCandidate.revision()).isNotEqualTo(candidate.revision());
  }

  @Test
  void shouldRequeueMultipleBatches() {
    var firstBatch = createCandidateMessages("batch1", DEFAULT_BATCH_SIZE);
    var secondBatch = createCandidateMessages("batch2", DEFAULT_BATCH_SIZE);
    var thirdBatch = createCandidateMessages("batch3", 3);
    setupMockEvents(firstBatch, secondBatch, thirdBatch);

    var response = handler.handleRequest(new RequeueDlqInput(23), CONTEXT);

    assertThat(getSuccessCount(response)).isEqualTo(23);
    assertThat(response.failedBatchesCount()).isZero();
  }

  @Test
  void shouldHandleEmptyResult() {
    setupMockEvents(emptyList());

    var response = handler.handleRequest(new RequeueDlqInput(100), CONTEXT);

    assertThat(getSuccessCount(response)).isZero();
    assertThat(response.failedBatchesCount()).isZero();
  }

  @Test
  void emptyInputShouldDefaultTo10() {
    var batch = createCandidateMessages(FIRST_BATCH, DEFAULT_BATCH_SIZE);
    setupMockEvents(batch);

    var response = handler.handleRequest(new RequeueDlqInput(), CONTEXT);

    assertThat(getSuccessCount(response)).isEqualTo(DEFAULT_BATCH_SIZE);
    assertThat(response.failedBatchesCount()).isZero();
  }

  @Test
  void shouldIgnoreDuplicates() {
    var candidate = setupRandomApplicableCandidate(scenario);
    var candidateMessage = generateMessageForCandidate(FIRST_BATCH + 1, candidate.identifier());
    setupMockEvents(List.of(candidateMessage), List.of(candidateMessage));

    var response = handler.handleRequest(new RequeueDlqInput(100), CONTEXT);

    assertThat(getSuccessCount(response)).isEqualTo(1);
    assertThat(response.failedBatchesCount()).isEqualTo(MAX_RETRY_ATTEMPTS);
  }

  @Test
  void missingCustomAttributeShouldFail() {
    var messageId = randomUUID().toString();
    var message = Message.builder().messageId(messageId).receiptHandle(messageId).build();
    setupMockEvents(List.of(message), emptyList());

    var response = handler.handleRequest(new RequeueDlqInput(100), CONTEXT);

    assertThat(response.messages())
        .filteredOn(result -> !result.success())
        .hasSize(1)
        .first()
        .extracting(NviProcessMessageResult::error)
        .asString()
        .contains("Could not process message");
  }

  @Test
  void shouldStopRepeatedErrors() {
    var messageId = randomUUID().toString();
    var message = Message.builder().messageId(messageId).receiptHandle(messageId).build();
    setupMockEvents(List.of(message));

    var response = handler.handleRequest(new RequeueDlqInput(100), CONTEXT);

    assertThat(getSuccessCount(response)).isZero();
    assertThat(response.failedBatchesCount()).isEqualTo(MAX_RETRY_ATTEMPTS);
  }

  @Test
  void outputShouldPassMessage() {
    var messages = createCandidateMessages("myInput", 1);
    setupMockEvents(messages);

    var response = handler.handleRequest(new RequeueDlqInput(100), CONTEXT);

    assertThat(response.messages())
        .filteredOn(NviProcessMessageResult::success)
        .extracting(result -> result.message().messageId())
        .anySatisfy(messageId -> assertThat(messageId).contains("myInput"));
  }

  @Test
  void shouldHandleFailureToWriteUpdatedCandidate() {
    var nonExistentCandidateId = randomUUID();
    var message = generateMessageForCandidate(FIRST_BATCH + 1, nonExistentCandidateId);
    setupMockEvents(List.of(message), emptyList());

    var response = handler.handleRequest(new RequeueDlqInput(100), CONTEXT);
    assertThat(response.failedBatchesCount()).isOne();
  }

  @Test
  void shouldUseDefaultBatchSizeForEmptyInput() throws JsonProcessingException {
    var input = JsonUtils.dtoObjectMapper.readValue("{}", RequeueDlqInput.class);
    assertThat(input.count()).isEqualTo(DEFAULT_BATCH_SIZE);
  }

  @Test
  void shouldHandleCandidateWithoutChannelType() {
    // This is not a valid state for candidates created in nva-nvi, but it may occur for candidates
    // imported via Cristin.
    var candidate = candidateMissingChannelType();
    var repository = scenario.getCandidateRepository();
    var candidateDao = repository.create(candidate, emptyList());
    var candidateMessage = generateMessageForCandidate(FIRST_BATCH + 1, candidateDao.identifier());
    setupMockEvents(List.of(candidateMessage), emptyList());

    var response = handler.handleRequest(new RequeueDlqInput(100), CONTEXT);

    assertThat(getSuccessCount(response)).isOne();
    assertThat(response.failedBatchesCount()).isZero();
  }

  @Test
  void shouldRequeueCandidateWithoutLossOfInformation() {
    var candidate = setupRandomApplicableCandidate(scenario);
    var message = generateMessageForCandidate(randomString(), candidate.identifier());
    setupMockEvents(List.of(message), emptyList());

    handler.handleRequest(new RequeueDlqInput(1), CONTEXT);
    var actualCandidate = candidateService.getByIdentifier(candidate.identifier());
    assertThat(actualCandidate)
        .usingRecursiveComparison()
        .ignoringFields("version", "revision", "lastWrittenAt")
        .ignoringCollectionOrder()
        .isEqualTo(candidate);
  }

  private Message createCandidateMessage(String messageId) {
    var candidate = setupRandomApplicableCandidate(scenario);
    return generateMessageForCandidate(messageId, candidate.identifier());
  }

  private List<Message> createCandidateMessages(String batchPrefix, int count) {
    return IntStream.rangeClosed(1, count)
        .mapToObj(i -> createCandidateMessage(batchPrefix + i))
        .toList();
  }

  @SafeVarargs
  private void setupMockEvents(Collection<Message>... batches) {
    var stub = when(sqsClient.receiveMessage(any(ReceiveMessageRequest.class)));
    for (var batch : batches) {
      stub = stub.thenReturn(ReceiveMessageResponse.builder().messages(batch).build());
    }
  }

  private static long getSuccessCount(RequeueDlqOutput response) {
    return response.messages().stream().filter(NviProcessMessageResult::success).count();
  }

  private static CandidateDao.DbCandidate candidateMissingChannelType() {
    var organizationId = randomUri();
    var channel = new DbPublicationChannel(randomUri(), null, ScientificValue.LEVEL_ONE.getValue());
    var publicationDetails = randomPublicationBuilder(organizationId).build();
    var pointCalculation =
        randomPointCalculationBuilder(randomUri(), organizationId)
            .publicationChannel(channel)
            .build();
    return randomCandidateBuilder(organizationId, publicationDetails, pointCalculation)
        .applicable(true)
        .build();
  }
}
