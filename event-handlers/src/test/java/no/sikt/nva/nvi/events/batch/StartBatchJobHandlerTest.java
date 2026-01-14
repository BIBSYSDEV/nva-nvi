package no.sikt.nva.nvi.events.batch;

import static no.sikt.nva.nvi.common.EnvironmentFixtures.BATCH_JOB_QUEUE_URL;
import static no.sikt.nva.nvi.common.EnvironmentFixtures.getStartBatchJobHandlerEnvironment;
import static no.sikt.nva.nvi.common.db.PeriodRepositoryFixtures.setupClosedPeriod;
import static no.sikt.nva.nvi.common.db.PeriodRepositoryFixtures.setupFuturePeriod;
import static no.sikt.nva.nvi.common.db.PeriodRepositoryFixtures.setupOpenPeriod;
import static no.sikt.nva.nvi.events.RequestFixtures.refreshAllPeriods;
import static no.sikt.nva.nvi.test.TestUtils.CURRENT_YEAR;
import static org.assertj.core.api.Assertions.assertThat;

import com.amazonaws.services.lambda.runtime.Context;
import com.fasterxml.jackson.core.JsonProcessingException;
import java.util.ArrayList;
import no.sikt.nva.nvi.common.FakeEnvironment;
import no.sikt.nva.nvi.common.TestScenario;
import no.sikt.nva.nvi.common.queue.FakeSqsClient;
import no.sikt.nva.nvi.events.batch.model.StartBatchJobRequest;
import no.unit.nva.commons.json.JsonUtils;
import no.unit.nva.stubs.FakeContext;
import no.unit.nva.stubs.FakeEventBridgeClient;
import nva.commons.core.Environment;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import software.amazon.awssdk.services.eventbridge.model.PutEventsRequestEntry;

class StartBatchJobHandlerTest {

  private static final Context CONTEXT = new FakeContext();
  private static final String LAST_YEAR = String.valueOf(CURRENT_YEAR - 1);
  private static final String NEXT_YEAR = String.valueOf(CURRENT_YEAR + 1);

  private FakeEnvironment environment;
  private FakeEventBridgeClient eventBridgeClient;
  private FakeSqsClient queueClient;
  private StartBatchJobHandler handler;

  @Nested
  class CustomizedDatasetTests {

    @BeforeEach
    void setUp() {
      var scenario = new TestScenario();
      queueClient = new FakeSqsClient();
      eventBridgeClient = new FakeEventBridgeClient();
      environment = getStartBatchJobHandlerEnvironment();
      handler = getHandler(environment, scenario);

      setupClosedPeriod(scenario, LAST_YEAR);
      setupOpenPeriod(scenario, CURRENT_YEAR);
      setupFuturePeriod(scenario, NEXT_YEAR);
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
        eventBridgeClient,
        environment,
        queueClient);
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

  private StartBatchJobRequest parseStartBatchJobRequest(PutEventsRequestEntry entry) {
    try {
      return JsonUtils.dtoObjectMapper.readValue(entry.detail(), StartBatchJobRequest.class);
    } catch (JsonProcessingException e) {
      throw new RuntimeException(e);
    }
  }
}
