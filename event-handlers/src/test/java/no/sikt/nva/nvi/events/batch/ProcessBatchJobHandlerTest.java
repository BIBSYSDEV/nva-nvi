package no.sikt.nva.nvi.events.batch;

import static java.util.UUID.randomUUID;
import static no.sikt.nva.nvi.common.db.PeriodRepositoryFixtures.setupOpenPeriod;
import static no.sikt.nva.nvi.test.TestUtils.CURRENT_YEAR;
import static no.sikt.nva.nvi.test.TestUtils.randomYear;
import static no.unit.nva.testutils.RandomDataGenerator.objectMapper;
import static nva.commons.core.attempt.Try.attempt;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.junit.jupiter.params.provider.Arguments.argumentSet;

import com.amazonaws.services.lambda.runtime.Context;
import com.amazonaws.services.lambda.runtime.events.SQSEvent;
import java.util.List;
import java.util.stream.Stream;
import no.sikt.nva.nvi.common.CandidateMigrationService;
import no.sikt.nva.nvi.common.TestScenario;
import no.sikt.nva.nvi.common.service.CandidateService;
import no.sikt.nva.nvi.events.batch.message.BatchJobMessage;
import no.sikt.nva.nvi.events.batch.message.MigrateCandidateMessage;
import no.sikt.nva.nvi.events.batch.message.RefreshCandidateMessage;
import no.sikt.nva.nvi.events.batch.message.RefreshPeriodMessage;
import no.unit.nva.stubs.FakeContext;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

// FIXME: Temporary suppression to split up PRs
@SuppressWarnings("PMD.SingularField")
class ProcessBatchJobHandlerTest {

  private static final Context CONTEXT = new FakeContext();
  private ProcessBatchJobHandler handler;
  private CandidateService candidateService;
  private CandidateMigrationService candidateMigrationService;
  private TestScenario scenario;

  @BeforeEach
  void setUp() {
    scenario = new TestScenario();
    candidateService = scenario.getCandidateService();
    var storageReader = scenario.getS3StorageReaderForExpandedResourcesBucket();
    candidateMigrationService = new CandidateMigrationService(candidateService, storageReader);
    handler = new ProcessBatchJobHandler(candidateService, candidateMigrationService);
    setupOpenPeriod(scenario, CURRENT_YEAR);
  }

  @ParameterizedTest
  @MethodSource("batchJobMessageProvider")
  void placeholderTestThatShouldFail(BatchJobMessage batchJobMessage) {
    var event = createEvent(batchJobMessage);
    assertThatThrownBy(() -> handler.handleRequest(event, CONTEXT))
        .isInstanceOf(UnsupportedOperationException.class);
  }

  private static Stream<Arguments> batchJobMessageProvider() {
    return Stream.of(
        argumentSet("RefreshCandidateMessage", new RefreshCandidateMessage(randomUUID())),
        argumentSet("MigrateCandidateMessage", new MigrateCandidateMessage(randomUUID())),
        argumentSet("RefreshPeriodMessage", new RefreshPeriodMessage(randomYear())));
  }

  private SQSEvent createEvent(BatchJobMessage batchJobMessage) {
    var sqsEvent = new SQSEvent();
    var message = new SQSEvent.SQSMessage();
    var body = attempt(() -> objectMapper.writeValueAsString(batchJobMessage)).orElseThrow();
    message.setBody(body);
    sqsEvent.setRecords(List.of(message));
    return sqsEvent;
  }
}
