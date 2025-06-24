package no.sikt.nva.nvi.events.batch;

import static java.util.stream.Collectors.toSet;
import static no.sikt.nva.nvi.common.EnvironmentFixtures.BATCH_SCAN_RECOVERY_QUEUE;
import static no.sikt.nva.nvi.common.EnvironmentFixtures.getBatchScanRecoveryHandlerEnvironment;
import static no.sikt.nva.nvi.common.db.CandidateDaoFixtures.createNumberOfCandidatesForYear;
import static no.sikt.nva.nvi.test.TestUtils.randomYear;
import static no.unit.nva.testutils.RandomDataGenerator.randomString;
import static org.assertj.core.api.Assertions.assertThat;

import com.amazonaws.services.lambda.runtime.events.SQSEvent.SQSMessage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.List;
import java.util.UUID;
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
  private FakeSqsClient queueClient;
  private BatchScanRecoveryHandler handler;
  private OutputStream output;
  private Environment environment;

  @BeforeEach
  void setup() {
    var scenario = new TestScenario();
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
  void shouldRerunEntriesOnRecoveryQueue() {
    var candidates = createCandidatesOnDlq(2);
    var originalVersions = candidates.stream().map(CandidateDao::version).toList();

    processHandlerRequest(new RecoveryEvent(10));

    assertThat(candidates)
        .extracting(this::getMigratedCandidate)
        .extracting(CandidateDao::version)
        .doesNotContainAnyElementsOf(originalVersions);
  }

  @Test
  void shouldDeleteProcessedEventsFromQueue() {
    createCandidatesOnDlq(5);

    processHandlerRequest(new RecoveryEvent(2));

    assertThat(getAllMessagesFromDlq()).hasSize(3);
  }

  private void processHandlerRequest(RecoveryEvent request) {
    try {
      handler.handleRequest(createEvent(request), output, CONTEXT);
    } catch (IOException e) {
      throw new RuntimeException(e);
    }
  }

  private List<CandidateDao> createCandidatesOnDlq(int count) {
    var candidates =
        createNumberOfCandidatesForYear(randomYear(), count, candidateRepository).stream()
            .map(this::placeOnQueue)
            .toList();
    var messagesOnQueue = getAllMessagesFromDlq();

    assertMessagesExistOnQueue(candidates, messagesOnQueue);
    return candidates;
  }

  private List<SQSMessage> getAllMessagesFromDlq() {
    return queueClient.getAllSentSqsEvents(environment.readEnv(BATCH_SCAN_RECOVERY_QUEUE.getKey()));
  }

  private static void assertMessagesExistOnQueue(
      List<CandidateDao> candidates, List<SQSMessage> messagesOnQueue) {
    var expectedCandidates =
        candidates.stream().map(CandidateDao::identifier).map(UUID::toString).collect(toSet());
    assertThat(messagesOnQueue)
        .extracting(
            message -> message.getMessageAttributes().get("candidateIdentifier").getStringValue())
        .containsAll(expectedCandidates);
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
