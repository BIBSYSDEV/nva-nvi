package no.sikt.nva.nvi.events.batch;

import static no.sikt.nva.nvi.common.service.CandidateService.defaultCandidateService;
import static no.sikt.nva.nvi.common.service.NviPeriodService.defaultNviPeriodService;
import static no.sikt.nva.nvi.common.utils.CollectionUtils.splitIntoBatches;
import static no.unit.nva.commons.json.JsonUtils.dtoObjectMapper;

import com.amazonaws.services.lambda.runtime.Context;
import com.amazonaws.services.lambda.runtime.RequestStreamHandler;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.Collection;
import no.sikt.nva.nvi.common.queue.NviQueueClient;
import no.sikt.nva.nvi.common.queue.QueueClient;
import no.sikt.nva.nvi.common.service.CandidateService;
import no.sikt.nva.nvi.common.service.NviPeriodService;
import no.sikt.nva.nvi.events.batch.message.BatchJobMessage;
import no.sikt.nva.nvi.events.batch.request.BatchJobRequest;
import nva.commons.core.Environment;
import nva.commons.core.JacocoGenerated;
import nva.commons.core.ioutils.IoUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import software.amazon.awssdk.http.urlconnection.UrlConnectionHttpClient;
import software.amazon.awssdk.services.eventbridge.EventBridgeClient;
import software.amazon.awssdk.services.eventbridge.model.PutEventsRequest;
import software.amazon.awssdk.services.eventbridge.model.PutEventsRequestEntry;

public class StartBatchJobHandler implements RequestStreamHandler {

  private static final Logger LOGGER = LoggerFactory.getLogger(StartBatchJobHandler.class);
  private static final String PROCESSING_ENABLED = "PROCESSING_ENABLED";
  private static final String BATCH_JOB_QUEUE_URL = "BATCH_JOB_QUEUE_URL";
  private static final String EVENT_BUS_NAME = "EVENT_BUS_NAME";
  private static final String DETAIL_TYPE = "NviService.BatchJob.StartBatchJob";
  private static final String SOURCE = "nva.nvi.batch";
  private static final int SQS_BATCH_SIZE = 10;
  private static final String EVENTBRIDGE_FIELD_DETAIL = "detail";
  private static final String EVENTBRIDGE_FIELD_ID = "id";

  private final BatchJobFactory batchJobFactory;
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
    this.batchJobFactory = new BatchJobFactory(candidateService, periodService);
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
  public void handleRequest(InputStream input, OutputStream output, Context context) {
    if (!processingEnabled) {
      LOGGER.warn("Processing disabled, aborting batch job");
    } else {
      var request = parseRequest(input);
      LOGGER.info("Processing batch job: {}", request);
      var batchJobResult = batchJobFactory.from(request).execute();
      sendMessagesToQueue(batchJobResult.messages());
      sendContinuationEvents(batchJobResult.continuationEvents());
    }
  }

  private BatchJobRequest parseRequest(InputStream input) {
    var jsonString = IoUtils.streamToString(input);
    try {
      var requestBody = extractRequestBody(jsonString);
      return dtoObjectMapper.treeToValue(requestBody, BatchJobRequest.class);
    } catch (IOException exception) {
      LOGGER.error("Failed to parse BatchJobRequest {}", jsonString, exception);
      throw new RuntimeException("Failed to parse BatchJobRequest", exception);
    }
  }

  private static JsonNode extractRequestBody(String jsonString) throws JsonProcessingException {
    var jsonNode = dtoObjectMapper.readTree(jsonString);
    return isEventBridgeEvent(jsonNode) ? jsonNode.get(EVENTBRIDGE_FIELD_DETAIL) : jsonNode;
  }

  private static boolean isEventBridgeEvent(JsonNode jsonNode) {
    return jsonNode.has(EVENTBRIDGE_FIELD_DETAIL) && jsonNode.has(EVENTBRIDGE_FIELD_ID);
  }

  private void sendMessagesToQueue(Collection<BatchJobMessage> messages) {
    splitIntoBatches(messages, SQS_BATCH_SIZE).forEach(this::sendBatch);
    LOGGER.info("Processed {} items", messages.size());
  }

  private void sendBatch(Collection<BatchJobMessage> messages) {
    var jsonMessages = messages.stream().map(BatchJobMessage::toJsonString).toList();
    var response = queueClient.sendMessageBatch(jsonMessages, queueUrl);
    if (!response.failed().isEmpty()) {
      throw new BatchJobException("Failed to send work items to queue");
    }
    LOGGER.info("Sent {} messages to queue", messages.size());
  }

  private void sendContinuationEvents(Collection<? extends BatchJobRequest> requests) {
    if (requests.isEmpty()) {
      LOGGER.info("No continuation events to send");
      return;
    }
    var entries = requests.stream().map(this::toEventEntry).toList();
    var putEventsRequest = PutEventsRequest.builder().entries(entries).build();
    var response = eventBridgeClient.putEvents(putEventsRequest);
    if (response.failedEntryCount() > 0) {
      throw new BatchJobException("Failed to send continuation events");
    }
    LOGGER.info("Sent {} continuation events", entries.size());
  }

  private PutEventsRequestEntry toEventEntry(BatchJobRequest request) {
    return PutEventsRequestEntry.builder()
        .eventBusName(eventBusName)
        .source(SOURCE)
        .detailType(DETAIL_TYPE)
        .detail(request.toJsonString())
        .build();
  }
}
