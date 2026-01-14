package no.sikt.nva.nvi.events.batch;

import static no.sikt.nva.nvi.common.EnvironmentFixtures.BATCH_JOB_QUEUE_URL;
import static no.sikt.nva.nvi.common.EnvironmentFixtures.PROCESSING_ENABLED;
import static no.sikt.nva.nvi.common.EnvironmentFixtures.getStartBatchJobHandlerEnvironment;
import static no.sikt.nva.nvi.common.db.PeriodRepositoryFixtures.setupClosedPeriod;
import static no.sikt.nva.nvi.common.db.PeriodRepositoryFixtures.setupFuturePeriod;
import static no.sikt.nva.nvi.common.db.PeriodRepositoryFixtures.setupOpenPeriod;
import static no.sikt.nva.nvi.common.model.CandidateFixtures.setupNumberOfCandidatesForYear;
import static no.sikt.nva.nvi.events.RequestFixtures.migrateCandidatesForCurrentYear;
import static no.sikt.nva.nvi.events.RequestFixtures.refreshAllCandidates;
import static no.sikt.nva.nvi.events.RequestFixtures.refreshAllPeriods;
import static no.sikt.nva.nvi.events.RequestFixtures.refreshCandidatesForYear;
import static no.sikt.nva.nvi.test.TestUtils.CURRENT_YEAR;
import static org.assertj.core.api.Assertions.assertThat;

import com.amazonaws.services.lambda.runtime.Context;
import com.fasterxml.jackson.core.JsonProcessingException;
import java.util.ArrayList;
import java.util.List;
import no.sikt.nva.nvi.common.FakeEnvironment;
import no.sikt.nva.nvi.common.TestScenario;
import no.sikt.nva.nvi.common.queue.FakeSqsClient;
import no.sikt.nva.nvi.events.batch.model.BatchJobMessage;
import no.sikt.nva.nvi.events.batch.model.MigrateCandidateMessage;
import no.sikt.nva.nvi.events.batch.model.RefreshCandidateMessage;
import no.sikt.nva.nvi.events.batch.model.StartBatchJobRequest;
import no.unit.nva.commons.json.JsonUtils;
import no.unit.nva.stubs.FakeContext;
import no.unit.nva.stubs.FakeEventBridgeClient;
import nva.commons.core.Environment;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import software.amazon.awssdk.services.eventbridge.model.PutEventsRequestEntry;

class StartBatchJobHandlerTest {

  private static final Context CONTEXT = new FakeContext();
  private static final String THIS_YEAR = String.valueOf(CURRENT_YEAR);
  private static final String LAST_YEAR = String.valueOf(CURRENT_YEAR - 1);

  private FakeEnvironment environment;
  private FakeEventBridgeClient eventBridgeClient;
  private FakeSqsClient queueClient;
  private StartBatchJobHandler handler;

  @Nested
  class StaticDatasetTests {
    private static final int CANDIDATES_PER_YEAR = 1000;
    private static final int TOTAL_CANDIDATE_COUNT = CANDIDATES_PER_YEAR * 2;
    private static final TestScenario scenario = new TestScenario();

    @BeforeAll
    static void init() {
      setupNumberOfCandidatesForYear(scenario, LAST_YEAR, CANDIDATES_PER_YEAR);
      setupNumberOfCandidatesForYear(scenario, THIS_YEAR, CANDIDATES_PER_YEAR);
    }

    @BeforeEach
    void setUp() {
      queueClient = new FakeSqsClient();
      eventBridgeClient = new FakeEventBridgeClient();
      environment = getStartBatchJobHandlerEnvironment();
      handler = getHandler(environment, scenario);
    }

    @Test
    void shouldQueueNothingWhenProcessingDisabled() {
      environment.setEnv(PROCESSING_ENABLED.getKey(), "false");
      handler = getHandler(environment, scenario);
      var request = refreshCandidatesForYear(String.valueOf(CURRENT_YEAR));

      runToCompletion(request);

      assertThat(getQueuedMessageCount()).isZero();
    }

    @Test
    void shouldQueueAllCandidatesForYear() {
      var request = refreshCandidatesForYear(THIS_YEAR);

      runToCompletion(request);

      assertThat(getQueuedMessageCount()).isEqualTo(CANDIDATES_PER_YEAR);
      assertThat(getQueuedMessages(RefreshCandidateMessage.class)).hasSize(CANDIDATES_PER_YEAR);
    }

    @Test
    void shouldQueueAllCandidatesAcrossMultipleYears() {
      var request = refreshCandidatesForYear(LAST_YEAR, THIS_YEAR);
      runToCompletion(request);
      assertThat(getQueuedMessageCount()).isEqualTo(TOTAL_CANDIDATE_COUNT);
    }

    @Test
    void shouldQueueAllCandidatesAcrossAllYears() {
      var request = refreshAllCandidates();
      runToCompletion(request);
      assertThat(getQueuedMessageCount()).isEqualTo(TOTAL_CANDIDATE_COUNT);
    }

    @Test
    void shouldCreateMigrateCandidateMessagesForMigrateJobType() {
      var request = migrateCandidatesForCurrentYear();

      runToCompletion(request);

      assertThat(getQueuedMessageCount()).isEqualTo(CANDIDATES_PER_YEAR);
      assertThat(getQueuedMessages(MigrateCandidateMessage.class)).hasSize(CANDIDATES_PER_YEAR);
    }

    @Test
    void shouldNotExceedMaxItemsLimit() {
      var request =
          refreshAllCandidates()
              .copy()
              .withMaxItemsPerSegment(5)
              .withMaxParallelSegments(3)
              .build();

      runToCompletion(request);

      assertThat(getQueuedMessageCount()).isEqualTo(15);
    }

    @Test
    void shouldGenerateMaxEventsPerYear() {
      var maxItemsToQueue = 4;
      var request =
          refreshCandidatesForYear(LAST_YEAR, THIS_YEAR)
              .copy()
              .withMaxItemsPerSegment(maxItemsToQueue)
              .build();

      runToCompletion(request);

      assertThat(getQueuedMessageCount()).isEqualTo(maxItemsToQueue);
    }
  }

  @Nested
  class CustomizedDatasetTests {

    @BeforeEach
    void setUp() {
      var scenario = new TestScenario();
      queueClient = new FakeSqsClient();
      eventBridgeClient = new FakeEventBridgeClient();
      environment = getStartBatchJobHandlerEnvironment();
      handler = getHandler(environment, scenario);

      setupClosedPeriod(scenario, CURRENT_YEAR - 1);
      setupOpenPeriod(scenario, CURRENT_YEAR);
      setupFuturePeriod(scenario, CURRENT_YEAR + 1);
    }

    @Test
    void shouldQueueNothingWhenNoCandidatesExist() {
      var request = refreshCandidatesForYear(String.valueOf(CURRENT_YEAR));
      runToCompletion(request);
      assertThat(getQueuedMessageCount()).isZero();
    }

    @Test
    void shouldQueueAllPeriods() {
      var request = refreshAllPeriods();
      runToCompletion(request);
      assertThat(getQueuedMessageCount()).isEqualTo(3);
    }
  }

  private StartBatchJobHandler getHandler(Environment environment, TestScenario scenario) {
    return new StartBatchJobHandler(
        scenario.getCandidateService(),
        scenario.getPeriodService(),
        queueClient,
        eventBridgeClient,
        environment);
  }

  private void runToCompletion(StartBatchJobRequest initialRequest) {
    handler.handleRequest(initialRequest, CONTEXT);
    processAllPendingEvents();
  }

  private void processAllPendingEvents() {
    while (!eventBridgeClient.getRequestEntries().isEmpty()) {
      var pendingEvents = new ArrayList<>(eventBridgeClient.getRequestEntries());
      eventBridgeClient.getRequestEntries().clear();

      for (var event : pendingEvents) {
        var request = parseStartBatchJobRequest(event);
        handler.handleRequest(request, CONTEXT);
      }
    }
  }

  private int getQueuedMessageCount() {
    return queueClient.getAllSentSqsEvents(BATCH_JOB_QUEUE_URL.getValue()).size();
  }

  private <T extends BatchJobMessage> List<T> getQueuedMessages(Class<T> messageType) {
    return queueClient.getAllSentSqsEvents(BATCH_JOB_QUEUE_URL.getValue()).stream()
        .map(msg -> parseMessage(msg.getBody()))
        .filter(messageType::isInstance)
        .map(messageType::cast)
        .toList();
  }

  private StartBatchJobRequest parseStartBatchJobRequest(PutEventsRequestEntry entry) {
    try {
      return JsonUtils.dtoObjectMapper.readValue(entry.detail(), StartBatchJobRequest.class);
    } catch (JsonProcessingException e) {
      throw new RuntimeException(e);
    }
  }

  private BatchJobMessage parseMessage(String json) {
    try {
      return JsonUtils.dtoObjectMapper.readValue(json, BatchJobMessage.class);
    } catch (JsonProcessingException e) {
      throw new RuntimeException(e);
    }
  }
}
