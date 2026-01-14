package no.sikt.nva.nvi.events.batch;

import static java.util.Objects.nonNull;
import static no.sikt.nva.nvi.common.service.CandidateService.defaultCandidateService;
import static no.sikt.nva.nvi.common.service.NviPeriodService.defaultNviPeriodService;
import static no.sikt.nva.nvi.common.utils.CollectionUtils.splitIntoBatches;

import com.amazonaws.services.lambda.runtime.Context;
import com.amazonaws.services.lambda.runtime.RequestHandler;
import java.util.Collection;
import java.util.List;
import no.sikt.nva.nvi.common.queue.NviQueueClient;
import no.sikt.nva.nvi.common.queue.QueueClient;
import no.sikt.nva.nvi.common.service.CandidateService;
import no.sikt.nva.nvi.common.service.NviPeriodService;
import no.sikt.nva.nvi.events.batch.model.BatchJobMessage;
import no.sikt.nva.nvi.events.batch.model.StartBatchJobRequest;
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
  private static final String DETAIL_TYPE = "NviService.BatchJob.StartBatchJob";
  private static final String SOURCE = "nva.nvi.batch";
  private static final int SQS_BATCH_SIZE = 10;

  private final BatchJobProcessor batchJobProcessor;
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
        defaultEventBridgeClient(),
        new Environment(),
        new NviQueueClient());
  }

  public StartBatchJobHandler(
      CandidateService candidateService,
      NviPeriodService periodService,
      EventBridgeClient eventBridgeClient,
      Environment environment,
      QueueClient queueClient) {
    this.batchJobProcessor = new BatchJobProcessor(candidateService, periodService);
    this.eventBridgeClient = eventBridgeClient;
    this.queueClient = queueClient;
    this.queueUrl = environment.readEnv(BATCH_JOB_QUEUE_URL);
    this.eventBusName = environment.readEnv(EVENT_BUS_NAME);
    this.processingEnabled = Boolean.parseBoolean(environment.readEnv(PROCESSING_ENABLED));
  }

  @JacocoGenerated
  private static EventBridgeClient defaultEventBridgeClient() {
    return EventBridgeClient.builder().httpClientBuilder(UrlConnectionHttpClient.builder()).build();
  }

  @Override
  public Void handleRequest(StartBatchJobRequest input, Context context) {
    LOGGER.info("Processing batch job: {}", input);
    if (!processingEnabled) {
      LOGGER.warn("Processing disabled, aborting batch job");
      return null;
    }

    var batchJobResult = batchJobProcessor.process(input);
    if (nonNull(batchJobResult.messages())) {
      sendMessagesToQueue(batchJobResult.messages());
    }
    if (nonNull(batchJobResult.continuationEvents())) {
      sendContinuationEvents(batchJobResult.continuationEvents());
    }

    return null;
  }

  private void sendMessagesToQueue(List<BatchJobMessage> messages) {
    splitIntoBatches(messages, SQS_BATCH_SIZE).forEach(this::sendBatch);
    LOGGER.info("Processed {} items", messages.size());
  }

  private void sendBatch(Collection<BatchJobMessage> messages) {
    var jsonMessages = messages.stream().map(BatchJobMessage::toJsonString).toList();
    var response =
        queueClient.sendMessageBatch(jsonMessages, queueUrl); // FIXME: Throw exception on failure?
    LOGGER.info(
        "Sent {} messages to queue. Failures: {}", messages.size(), response.failed().size());
  }

  private void sendContinuationEvents(List<StartBatchJobRequest> requests) {
    var entries = requests.stream().map(this::toEventEntry).toList();
    var putEventsRequest = PutEventsRequest.builder().entries(entries).build();
    var response =
        eventBridgeClient.putEvents(putEventsRequest); // FIXME: Throw exception on failure?
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
}
