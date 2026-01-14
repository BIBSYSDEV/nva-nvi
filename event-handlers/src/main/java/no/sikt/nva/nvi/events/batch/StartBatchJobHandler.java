package no.sikt.nva.nvi.events.batch;

import static no.sikt.nva.nvi.common.service.CandidateService.defaultCandidateService;
import static no.sikt.nva.nvi.common.service.NviPeriodService.defaultNviPeriodService;

import com.amazonaws.services.lambda.runtime.Context;
import com.amazonaws.services.lambda.runtime.RequestHandler;
import java.util.Collection;
import java.util.List;
import java.util.UUID;
import java.util.stream.IntStream;
import no.sikt.nva.nvi.common.model.ListingResult;
import no.sikt.nva.nvi.common.queue.NviQueueClient;
import no.sikt.nva.nvi.common.queue.QueueClient;
import no.sikt.nva.nvi.common.service.CandidateService;
import no.sikt.nva.nvi.common.service.NviPeriodService;
import no.sikt.nva.nvi.common.service.model.NviPeriod;
import no.sikt.nva.nvi.events.batch.model.BatchJobMessage;
import no.sikt.nva.nvi.events.batch.model.BatchJobType;
import no.sikt.nva.nvi.events.batch.model.MigrateCandidateMessage;
import no.sikt.nva.nvi.events.batch.model.RefreshCandidateMessage;
import no.sikt.nva.nvi.events.batch.model.RefreshPeriodMessage;
import no.sikt.nva.nvi.events.batch.model.ReportingYearFilter;
import no.sikt.nva.nvi.events.batch.model.StartBatchJobRequest;
import no.sikt.nva.nvi.events.batch.model.TableScanState;
import no.sikt.nva.nvi.events.batch.model.YearQueryState;
import nva.commons.core.Environment;
import nva.commons.core.JacocoGenerated;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import software.amazon.awssdk.http.urlconnection.UrlConnectionHttpClient;
import software.amazon.awssdk.services.eventbridge.EventBridgeClient;
import software.amazon.awssdk.services.eventbridge.model.PutEventsRequest;
import software.amazon.awssdk.services.eventbridge.model.PutEventsRequestEntry;

public class StartBatchJobHandler implements RequestHandler<StartBatchJobRequest, Void> {

  private static final Logger LOGGER = LoggerFactory.getLogger(StartBatchJobHandler.class);
  private static final String PROCESSING_ENABLED = "PROCESSING_ENABLED";
  private static final String BATCH_JOB_QUEUE_URL = "BATCH_JOB_QUEUE_URL";
  private static final String EVENT_BUS_NAME = "EVENT_BUS_NAME";
  private static final String DETAIL_TYPE = "StartBatchJob";
  private static final String SOURCE = "nva.nvi.batch";
  private static final int DEFAULT_PAGE_SIZE = 700;
  private static final int SQS_BATCH_SIZE = 10;

  private final CandidateService candidateService;
  private final NviPeriodService periodService;
  private final QueueClient queueClient;
  private final EventBridgeClient eventBridgeClient;
  private final String queueUrl;
  private final String eventBusName;
  private final boolean processingEnabled;

  @JacocoGenerated
  public StartBatchJobHandler() {
    this(
        defaultCandidateService(),
        defaultNviPeriodService(),
        new NviQueueClient(),
        defaultEventBridgeClient(),
        new Environment());
  }

  public StartBatchJobHandler(
      CandidateService candidateService,
      NviPeriodService periodService,
      QueueClient queueClient,
      EventBridgeClient eventBridgeClient,
      Environment environment) {
    this.candidateService = candidateService;
    this.periodService = periodService;
    this.queueClient = queueClient;
    this.eventBridgeClient = eventBridgeClient;
    this.queueUrl = environment.readEnv(BATCH_JOB_QUEUE_URL);
    this.eventBusName = environment.readEnv(EVENT_BUS_NAME);
    this.processingEnabled = Boolean.parseBoolean(environment.readEnv(PROCESSING_ENABLED));
  }

  @Override
  public Void handleRequest(StartBatchJobRequest input, Context context) {
    LOGGER.info("Processing batch job: {}", input);
    if (!processingEnabled) {
      LOGGER.warn("Processing disabled, aborting batch job");
      return null;
    }

    if (input.isInitialInvocation()) {
      handleInitialInvocation(input);
    } else {
      handleSegmentProcessing(input);
    }

    return null;
  }

  private void handleInitialInvocation(StartBatchJobRequest input) {
    LOGGER.info("Processing initial invocation");
    if (BatchJobType.REFRESH_PERIODS.equals(input.jobType())) {
      generatePeriodMessages(input);
    } else if (input.hasYearFilter()) {
      generateYearQueryEvent(input);
    } else {
      generateTableScanEvents(input);
    }
  }

  private void handleSegmentProcessing(StartBatchJobRequest input) {
    LOGGER.info("Processing segment");
    var nextBatchSize = Integer.min(input.maxRemainingItems(), DEFAULT_PAGE_SIZE);
    if (nextBatchSize <= 0) {
      LOGGER.info("Item limit reached, stopping processing");
      return;
    }

    var paginationState = input.paginationState();
    switch (paginationState) {
      case YearQueryState state -> generateCandidateMessages(input, state, nextBatchSize);
      case TableScanState state -> generateCandidateMessages(input, state, nextBatchSize);
    }
  }

  private void generateYearQueryEvent(StartBatchJobRequest input) {
    var yearFilter = (ReportingYearFilter) input.filter();
    var yearQueryState = YearQueryState.forYears(yearFilter.reportingYears());
    var newRequest = input.copy().withPaginationState(yearQueryState).build();
    sendEvent(newRequest);
  }

  private void generateTableScanEvents(StartBatchJobRequest input) {
    var totalSegments = input.maxParallelSegments();
    var events = createTableScanEvents(input, totalSegments);
    sendEvents(events);
  }

  private List<StartBatchJobRequest> createTableScanEvents(
      StartBatchJobRequest input, int totalSegments) {
    return IntStream.range(0, totalSegments)
        .mapToObj(segment -> TableScanState.forSegment(segment, totalSegments))
        .map(nextState -> input.copy().withPaginationState(nextState).build())
        .toList();
  }

  /** Generates work items for candidates by year from GSI query */
  private void generateCandidateMessages(
      StartBatchJobRequest input, YearQueryState state, int nextBatchSize) {
    var year = state.currentYear();
    LOGGER.info("Processing year query for year: {}", year);

    var listingResult =
        candidateService.listCandidateIdentifiersByYear(
            year, nextBatchSize, state.lastEvaluatedKey());

    var messages =
        listingResult.getDatabaseEntries().stream()
            .map(identifier -> createMessage(input.jobType(), identifier))
            .toList();

    sendMessagesToQueue(messages);
    handleYearQueryPagination(input, state, listingResult);
  }

  /** Generates work items for candidates with parallelized table scan */
  private void generateCandidateMessages(
      StartBatchJobRequest input, TableScanState state, int nextBatchSize) {
    LOGGER.info("Processing scan query for segment: {}", state.segment());

    var listingResult =
        candidateService.listCandidateIdentifiers(
            state.segment(), state.totalSegments(), nextBatchSize, state.lastEvaluatedKey());

    var messages =
        listingResult.getDatabaseEntries().stream()
            .map(identifier -> createMessage(input.jobType(), identifier))
            .toList();

    sendMessagesToQueue(messages);
    handleTableScanPagination(input, state, listingResult);
  }

  /** Generates work items for all periods */
  private void generatePeriodMessages(StartBatchJobRequest input) {
    var messages =
        periodService.getAll().stream()
            .map(NviPeriod::publishingYear)
            .map(String::valueOf)
            .map(RefreshPeriodMessage::new)
            .map(BatchJobMessage.class::cast)
            .limit(input.maxRemainingItems())
            .toList();
    sendMessagesToQueue(messages);
  }

  private void handleYearQueryPagination(
      StartBatchJobRequest input, YearQueryState state, ListingResult<UUID> result) {
    if (result.shouldContinueScan()) {
      LOGGER.info("Continuing scan for current year");
      var nextState =
          state.withNextPage(result.getStartMarker(), result.getDatabaseEntries().size());
      sendEvent(input.copy().withPaginationState(nextState).build());
    } else if (state.hasMoreYears()) {
      LOGGER.info("Continuing scan with next year");
      var nextState = state.withNextYear();
      sendEvent(input.copy().withPaginationState(nextState).build());
    }
  }

  private void handleTableScanPagination(
      StartBatchJobRequest input, TableScanState state, ListingResult<UUID> result) {
    if (result.shouldContinueScan()) {
      LOGGER.info("Continuing table scan");
      var nextState =
          state.withNextPage(result.getStartMarker(), result.getDatabaseEntries().size());
      sendEvent(input.copy().withPaginationState(nextState).build());
    } else {
      LOGGER.info("Table scan completed for segment");
    }
  }

  private BatchJobMessage createMessage(BatchJobType jobType, UUID identifier) {
    return switch (jobType) {
      case REFRESH_CANDIDATES -> new RefreshCandidateMessage(identifier);
      case MIGRATE_CANDIDATES -> new MigrateCandidateMessage(identifier);
      case REFRESH_PERIODS ->
          throw new UnsupportedOperationException("REFRESH_PERIODS not supported yet");
    };
  }

  private void sendMessagesToQueue(List<BatchJobMessage> messages) {
    splitIntoBatches(messages).forEach(this::sendBatch);
    LOGGER.info("Processed {} items", messages.size());
  }

  private List<List<BatchJobMessage>> splitIntoBatches(List<BatchJobMessage> messages) {
    var count = messages.size();
    return IntStream.range(0, (count + SQS_BATCH_SIZE - 1) / SQS_BATCH_SIZE)
        .mapToObj(
            i -> messages.subList(i * SQS_BATCH_SIZE, Math.min((i + 1) * SQS_BATCH_SIZE, count)))
        .toList();
  }

  private void sendBatch(Collection<BatchJobMessage> messages) {
    var jsonMessages = messages.stream().map(BatchJobMessage::toJsonString).toList();
    var response = queueClient.sendMessageBatch(jsonMessages, queueUrl);
    LOGGER.info(
        "Sent {} messages to queue. Failures: {}", messages.size(), response.failed().size());
  }

  private void sendEvent(StartBatchJobRequest request) {
    sendEvents(List.of(request));
  }

  private void sendEvents(List<StartBatchJobRequest> requests) {
    var entries = requests.stream().map(this::toEventEntry).toList();
    var putEventsRequest = PutEventsRequest.builder().entries(entries).build();
    var response = eventBridgeClient.putEvents(putEventsRequest);
    LOGGER.info("EventBridge response: {} failed entries", response.failedEntryCount());
  }

  private PutEventsRequestEntry toEventEntry(StartBatchJobRequest request) {
    return PutEventsRequestEntry.builder()
        .eventBusName(eventBusName)
        .source(SOURCE)
        .detailType(DETAIL_TYPE)
        .detail(request.toJsonString())
        .build();
  }

  @JacocoGenerated
  private static EventBridgeClient defaultEventBridgeClient() {
    return EventBridgeClient.builder().httpClientBuilder(UrlConnectionHttpClient.builder()).build();
  }
}
