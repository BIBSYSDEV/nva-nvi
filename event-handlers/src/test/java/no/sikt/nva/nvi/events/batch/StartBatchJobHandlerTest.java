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
import static no.sikt.nva.nvi.test.TestConstants.LAST_YEAR;
import static no.sikt.nva.nvi.test.TestConstants.NEXT_YEAR;
import static no.sikt.nva.nvi.test.TestConstants.THIS_YEAR;
import static no.sikt.nva.nvi.test.TestUtils.CURRENT_YEAR;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.junit.jupiter.params.provider.Arguments.argumentSet;

import com.amazonaws.services.lambda.runtime.Context;
import com.fasterxml.jackson.core.JsonProcessingException;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;
import no.sikt.nva.nvi.common.FakeEnvironment;
import no.sikt.nva.nvi.common.TestScenario;
import no.sikt.nva.nvi.common.exceptions.ValidationException;
import no.sikt.nva.nvi.common.queue.FakeSqsClient;
import no.sikt.nva.nvi.events.batch.message.BatchJobMessage;
import no.sikt.nva.nvi.events.batch.message.MigrateCandidateMessage;
import no.sikt.nva.nvi.events.batch.message.RefreshCandidateMessage;
import no.sikt.nva.nvi.events.batch.message.RefreshPeriodMessage;
import no.sikt.nva.nvi.events.batch.model.ReportingYearFilter;
import no.sikt.nva.nvi.events.batch.request.BatchJobRequest;
import no.sikt.nva.nvi.events.batch.request.StartBatchJobRequest;
import no.unit.nva.commons.json.JsonUtils;
import no.unit.nva.stubs.FakeContext;
import no.unit.nva.stubs.FakeEventBridgeClient;
import nva.commons.core.Environment;
import nva.commons.core.ioutils.IoUtils;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import software.amazon.awssdk.services.eventbridge.model.PutEventsRequestEntry;

class StartBatchJobHandlerTest {

  private static final Context CONTEXT = new FakeContext();

  private FakeEnvironment environment;
  private FakeEventBridgeClient eventBridgeClient;
  private FakeSqsClient queueClient;
  private OutputStream output;
  private StartBatchJobHandler handler;

  @BeforeEach
  void setUp() {
    queueClient = new FakeSqsClient();
    output = new ByteArrayOutputStream();
    eventBridgeClient = new FakeEventBridgeClient();
    environment = getStartBatchJobHandlerEnvironment();
  }

  @Nested
  class StaticDatasetTests {
    private static final int CANDIDATES_PER_YEAR = 20;
    private static final int TOTAL_CANDIDATE_COUNT = CANDIDATES_PER_YEAR * 2;
    private static final TestScenario scenario = new TestScenario();

    @BeforeAll
    static void init() {
      setupNumberOfCandidatesForYear(scenario, LAST_YEAR, CANDIDATES_PER_YEAR);
      setupNumberOfCandidatesForYear(scenario, THIS_YEAR, CANDIDATES_PER_YEAR);
    }

    @BeforeEach
    void setUp() {
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
              .withMaxBatchSize(3)
              .withMaxParallelSegments(3)
              .withMaxItems(15)
              .build();

      runToCompletion(request);

      assertThat(getQueuedMessageCount()).isEqualTo(15);
    }

    @Test
    void shouldGenerateMaxEventsPerYear() {
      var maxItemsToQueue = 15;
      var request =
          refreshCandidatesForYear(LAST_YEAR, THIS_YEAR)
              .copy()
              .withMaxBatchSize(5)
              .withMaxItems(maxItemsToQueue)
              .build();

      runToCompletion(request);

      assertThat(getQueuedMessageCount()).isEqualTo(maxItemsToQueue);
    }

    @Test
    void shouldParseEventBridgeEvents() {
      var rawEventBridgeEvent =
          """
          {
            "version": "0",
            "id": "3f486514-7573-9de1-caa5-9de853d4fcaa",
            "detail-type": "NviService.BatchJob.StartBatchJob",
            "detail": {
              "type": "CandidatesByYearRequest",
              "jobType": "REFRESH_CANDIDATES",
              "yearFilter": {
                "type": "ReportingYearFilter",
                "reportingYears": [
                  "__YEAR__"
                ]
              },
              "paginationState": {
                "itemsProcessed": 0,
                "maxBatchSize": 700,
                "maxItems": 2000
              }
            }
          }
          """
              .replace("__YEAR__", THIS_YEAR);
      var requestInputStream = IoUtils.stringToStream(rawEventBridgeEvent);

      handler.handleRequest(requestInputStream, output, CONTEXT);
      processAllPendingEvents();

      assertThat(getQueuedMessages(RefreshCandidateMessage.class)).hasSize(CANDIDATES_PER_YEAR);
    }
  }

  @Nested
  class CustomizedDatasetTests {

    @BeforeEach
    void setUp() {
      var scenario = new TestScenario();
      handler = getHandler(environment, scenario);

      setupClosedPeriod(scenario, LAST_YEAR);
      setupOpenPeriod(scenario, THIS_YEAR);
      setupFuturePeriod(scenario, NEXT_YEAR);
    }

    @Test
    void shouldQueueNothingWhenNoCandidatesExist() {
      var request = refreshCandidatesForYear(THIS_YEAR);
      runToCompletion(request);
      assertThat(getQueuedMessageCount()).isZero();
    }

    @Test
    void shouldQueueAllPeriods() {
      var request = refreshAllPeriods();
      runToCompletion(request);
      assertThat(getQueuedMessageCount()).isEqualTo(3);
    }

    @Test
    void shouldQueueSinglePeriod() {
      var request = refreshAllPeriods().copy().withMaxItems(1).build();
      runToCompletion(request);
      assertThat(getQueuedMessageCount()).isOne();
    }

    @Test
    void shouldQueueSpecifiedPeriod() {
      var request =
          refreshAllPeriods().copy().withFilter(new ReportingYearFilter(LAST_YEAR)).build();
      runToCompletion(request);
      var actualMessages = getQueuedMessages(RefreshPeriodMessage.class);
      assertThat(actualMessages).singleElement().isEqualTo(new RefreshPeriodMessage(LAST_YEAR));
    }

    @ParameterizedTest
    @MethodSource("invalidRequestProvider")
    void shouldRejectInvalidRequestParameters(StartBatchJobRequest.Builder request) {
      assertThat(getQueuedMessageCount()).isZero();
      assertThatThrownBy(() -> runToCompletion(request.build()))
          .isInstanceOf(ValidationException.class);
    }

    @ParameterizedTest
    @MethodSource("invalidYearProvider")
    void shouldRejectInvalidYearFilters(List<String> years) {
      assertThatThrownBy(
              () -> {
                var request =
                    refreshAllCandidates()
                        .copy()
                        .withFilter(new ReportingYearFilter(years))
                        .build();
                runToCompletion(request);
              })
          .isInstanceOf(ValidationException.class);
      assertThat(getQueuedMessageCount()).isZero();
    }

    private static Stream<Arguments> invalidRequestProvider() {
      return Stream.of(
          argumentSet("Negative max items", refreshAllCandidates().copy().withMaxItems(-1)),
          argumentSet(
              "Negative segment count", refreshAllCandidates().copy().withMaxParallelSegments(-1)));
    }

    private static Stream<Arguments> invalidYearProvider() {
      return Stream.of(
          argumentSet("Empty string", List.of("")),
          argumentSet("Invalid string", List.of("this year")),
          argumentSet("Year out of range", List.of("0")));
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

  private void processHandlerRequest(BatchJobRequest request) {
    handler.handleRequest(createEvent(request), output, CONTEXT);
  }

  private InputStream createEvent(BatchJobRequest request) {
    return IoUtils.stringToStream(request.toJsonString());
  }

  private void runToCompletion(StartBatchJobRequest initialRequest) {
    processHandlerRequest(initialRequest);
    processAllPendingEvents();
  }

  private void processAllPendingEvents() {
    while (!eventBridgeClient.getRequestEntries().isEmpty()) {
      var pendingEvents = new ArrayList<>(eventBridgeClient.getRequestEntries());
      eventBridgeClient.getRequestEntries().clear();

      for (var event : pendingEvents) {
        var request = parseBatchJobRequest(event);
        processHandlerRequest(request);
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

  private BatchJobMessage parseMessage(String json) {
    try {
      return JsonUtils.dtoObjectMapper.readValue(json, BatchJobMessage.class);
    } catch (JsonProcessingException e) {
      throw new RuntimeException(e);
    }
  }

  private BatchJobRequest parseBatchJobRequest(PutEventsRequestEntry entry) {
    try {
      return JsonUtils.dtoObjectMapper.readValue(entry.detail(), BatchJobRequest.class);
    } catch (JsonProcessingException e) {
      throw new RuntimeException(e);
    }
  }
}
