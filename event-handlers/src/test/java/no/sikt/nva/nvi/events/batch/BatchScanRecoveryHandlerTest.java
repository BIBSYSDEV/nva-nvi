package no.sikt.nva.nvi.events.batch;

import static no.sikt.nva.nvi.common.EnvironmentFixtures.BATCH_SCAN_RECOVERY_QUEUE;
import static no.sikt.nva.nvi.common.EnvironmentFixtures.getBatchScanRecoveryHandlerEnvironment;
import static no.sikt.nva.nvi.common.db.CandidateDaoFixtures.createNumberOfCandidatesForYear;
import static no.sikt.nva.nvi.test.TestUtils.randomYear;
import static no.unit.nva.testutils.RandomDataGenerator.randomString;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.amazonaws.services.lambda.runtime.events.SQSEvent.SQSMessage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.List;
import no.sikt.nva.nvi.common.TestScenario;
import no.sikt.nva.nvi.common.db.CandidateDao;
import no.sikt.nva.nvi.common.db.CandidateRepository;
import no.sikt.nva.nvi.common.queue.FakeSqsClient;
import no.sikt.nva.nvi.common.utils.BatchScanUtil;
import no.unit.nva.stubs.FakeContext;
import nva.commons.core.Environment;
import nva.commons.core.ioutils.IoUtils;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class BatchScanRecoveryHandlerTest {

  protected static final FakeContext CONTEXT = new FakeContext();
  private CandidateRepository candidateRepository;
  private TestScenario scenario;
  private FakeSqsClient queueClient;
  private BatchScanRecoveryHandler handler;
  private OutputStream output;
  private Environment environment;

  @BeforeEach
  public void setup() {
    scenario = new TestScenario();
    candidateRepository = scenario.getCandidateRepository();
    output = new ByteArrayOutputStream();
    queueClient = new FakeSqsClient();
    environment = getBatchScanRecoveryHandlerEnvironment();
    var batchScanUtil =
        new BatchScanUtil(
            scenario.getCandidateRepository(),
            scenario.getS3StorageReaderForExpandedResourcesBucket(),
            queueClient,
            environment);
    handler = new BatchScanRecoveryHandler(queueClient, batchScanUtil, environment);
  }

  @Test
  void shouldRerunEntriesOnRecoveryQueue() throws IOException {
    var candidates =
        createNumberOfCandidatesForYear(randomYear(), 2, candidateRepository).stream()
            .map(this::placeOnQueue)
            .toList();

    var messagesOnQueue =
        queueClient.getAllSentSqsEvents(environment.readEnv(BATCH_SCAN_RECOVERY_QUEUE.getKey()));

    assertMessagesExistOnQueue(candidates, messagesOnQueue);

    handler.handleRequest(createEvent(new RecoveryEvent(10)), output, CONTEXT);

    candidates.forEach(
        candidate ->
            assertFalse(getMigratedCandidate(candidate).version().equals(candidate.version())));
  }

  @Test
  void shouldKeepNotProcessedMessagesOnQueue() throws IOException {
    var candidates =
        createNumberOfCandidatesForYear(randomYear(), 5, candidateRepository).stream()
            .map(this::placeOnQueue)
            .toList();

    var messagesOnQueue =
        queueClient.getAllSentSqsEvents(environment.readEnv(BATCH_SCAN_RECOVERY_QUEUE.getKey()));

    assertMessagesExistOnQueue(candidates, messagesOnQueue);

    handler.handleRequest(createEvent(new RecoveryEvent(2)), output, CONTEXT);

    assertEquals(
        3,
        queueClient
            .getAllSentSqsEvents(environment.readEnv(BATCH_SCAN_RECOVERY_QUEUE.getKey()))
            .size());
  }

  private static void assertMessagesExistOnQueue(
      List<CandidateDao> candidates, List<SQSMessage> messagesOnQueue) {
    candidates.forEach(
        candidate ->
            assertTrue(
                messagesOnQueue.stream()
                    .anyMatch(
                        message ->
                            message
                                .getMessageAttributes()
                                .get("candidateIdentifier")
                                .getStringValue()
                                .equals(candidate.identifier().toString()))));
  }

  private CandidateDao getMigratedCandidate(CandidateDao candidate) {
    return candidateRepository.findCandidateById(candidate.identifier()).orElseThrow();
  }

  private CandidateDao placeOnQueue(CandidateDao candidateDao) {
    queueClient.sendMessage(
        randomString(), BATCH_SCAN_RECOVERY_QUEUE.getValue(), candidateDao.identifier());
    return candidateDao;
  }

  private InputStream createEvent(RecoveryEvent event) {
    return IoUtils.stringToStream(event.toJsonString());
  }
}
