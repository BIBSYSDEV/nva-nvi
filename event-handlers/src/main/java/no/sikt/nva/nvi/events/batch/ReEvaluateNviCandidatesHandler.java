package no.sikt.nva.nvi.events.batch;

import static java.util.Objects.isNull;
import static nva.commons.core.attempt.Try.attempt;

import com.amazonaws.services.lambda.runtime.Context;
import java.net.URI;
import java.util.Collection;
import java.util.List;
import java.util.stream.IntStream;
import java.util.stream.Stream;
import no.sikt.nva.nvi.common.db.CandidateDao;
import no.sikt.nva.nvi.common.db.CandidateDao.DbCandidate;
import no.sikt.nva.nvi.common.model.ListingResult;
import no.sikt.nva.nvi.common.queue.NviQueueClient;
import no.sikt.nva.nvi.common.queue.QueueClient;
import no.sikt.nva.nvi.common.utils.BatchScanUtil;
import no.sikt.nva.nvi.events.model.PersistedResourceMessage;
import no.sikt.nva.nvi.events.model.ReEvaluateRequest;
import no.unit.nva.events.handlers.EventHandler;
import no.unit.nva.events.models.AwsEventBridgeEvent;
import nva.commons.core.Environment;
import nva.commons.core.JacocoGenerated;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import software.amazon.awssdk.http.urlconnection.UrlConnectionHttpClient;
import software.amazon.awssdk.services.eventbridge.EventBridgeClient;
import software.amazon.awssdk.services.eventbridge.model.PutEventsRequest;
import software.amazon.awssdk.services.eventbridge.model.PutEventsRequestEntry;

public class ReEvaluateNviCandidatesHandler extends EventHandler<ReEvaluateRequest, Void> {

  private static final Logger LOGGER =
      LoggerFactory.getLogger(ReEvaluateNviCandidatesHandler.class);
  private static final String EVENT_BUS_NAME = "EVENT_BUS_NAME";
  private static final String OUTPUT_EVENT_TOPIC = "TOPIC_REEVALUATE_CANDIDATES";
  private static final String PERSISTED_RESOURCE_QUEUE_URL = "PERSISTED_RESOURCE_QUEUE_URL";
  private static final int BATCH_SIZE = 10;
  private static final String INVALID_INPUT_MESSAGE = "Invalid request. Field year is required";
  private static final String QUERY_STARTING_POINT_MESSAGE = "Query starting point: {}";
  private static final String RESULT_MESSAGE =
      "Batch result startMarker: {}, totalItemCount: {}, " + "shouldContinueScan: {}";
  private static final String PUT_EVENT_RESPONSE_MESSAGE = "Put event response: {}";
  private static final String MESSAGES_SENT_MESSAGE = "Sent {} messages to queue. Failures: {}";
  private final QueueClient queueClient;
  private final BatchScanUtil batchScanUtil;
  private final String queueUrl;
  private final String eventBusName;
  private final String topic;
  private final EventBridgeClient eventBridgeClient;

  @JacocoGenerated
  public ReEvaluateNviCandidatesHandler() {
    this(
        BatchScanUtil.defaultNviService(),
        new NviQueueClient(),
        new Environment(),
        defaultEventBridgeClient());
  }

  public ReEvaluateNviCandidatesHandler(
      BatchScanUtil batchScanUtil,
      QueueClient queueClient,
      Environment environment,
      EventBridgeClient eventBridgeClient) {
    super(ReEvaluateRequest.class);
    this.batchScanUtil = batchScanUtil;
    this.queueClient = queueClient;
    this.queueUrl = environment.readEnv(PERSISTED_RESOURCE_QUEUE_URL);
    this.eventBusName = environment.readEnv(EVENT_BUS_NAME);
    this.topic = environment.readEnv(OUTPUT_EVENT_TOPIC);
    this.eventBridgeClient = eventBridgeClient;
  }

  @Override
  protected Void processInput(
      ReEvaluateRequest input, AwsEventBridgeEvent<ReEvaluateRequest> event, Context context) {
    validateInput(input);
    LOGGER.info(QUERY_STARTING_POINT_MESSAGE, input.startMarker());
    var result = getListingResultWithNonReportedCandidates(input);
    logResult(result);
    splitIntoBatches(mapToFileUris(result))
        .forEach(fileUriList -> sendBatch(createMessages(fileUriList)));
    if (result.shouldContinueScan()) {
      sendEventToInvokeNewReEvaluateExecution(input, context, result);
    }
    return null;
  }

  private static void logResult(ListingResult<CandidateDao> result) {
    LOGGER.info(
        RESULT_MESSAGE,
        result.getStartMarker(),
        result.getTotalItemCount(),
        result.shouldContinueScan());
  }

  @JacocoGenerated
  private static EventBridgeClient defaultEventBridgeClient() {
    return EventBridgeClient.builder().httpClientBuilder(UrlConnectionHttpClient.builder()).build();
  }

  private static List<URI> mapToFileUris(ListingResult<CandidateDao> result) {
    return result.getDatabaseEntries().stream()
        .map(CandidateDao::candidate)
        .map(DbCandidate::publicationBucketUri)
        .toList();
  }

  private void sendEventToInvokeNewReEvaluateExecution(
      ReEvaluateRequest request, Context context, ListingResult<CandidateDao> result) {
    var newEvent =
        request
            .newReEvaluateRequest(result.getStartMarker(), topic)
            .createNewEventEntry(eventBusName, context.getInvokedFunctionArn());
    sendEvent(newEvent);
  }

  private void sendEvent(PutEventsRequestEntry newEvent) {
    var putEventRequest = PutEventsRequest.builder().entries(newEvent).build();
    var response = eventBridgeClient.putEvents(putEventRequest);
    LOGGER.info(PUT_EVENT_RESPONSE_MESSAGE, response.toString());
  }

  private ListingResult<CandidateDao> getListingResultWithNonReportedCandidates(
      ReEvaluateRequest input) {
    var includeReportedCandidates = false;
    return batchScanUtil.fetchCandidatesByYear(
        input.year(), includeReportedCandidates, input.pageSize(), input.startMarker());
  }

  private Stream<List<URI>> splitIntoBatches(List<URI> fileUris) {
    var count = fileUris.size();
    return IntStream.range(0, (count + BATCH_SIZE - 1) / BATCH_SIZE)
        .mapToObj(i -> fileUris.subList(i * BATCH_SIZE, Math.min((i + 1) * BATCH_SIZE, count)));
  }

  private void sendBatch(Collection<String> messages) {
    var response = queueClient.sendMessageBatch(messages, queueUrl);
    LOGGER.info(MESSAGES_SENT_MESSAGE, messages.size(), response.failed().size());
  }

  private Collection<String> createMessages(List<URI> uris) {
    return uris.stream().map(this::createMessage).toList();
  }

  private String createMessage(URI fileUri) {
    return attempt(() -> objectMapper.writeValueAsString(new PersistedResourceMessage(fileUri)))
        .orElseThrow();
  }

  private void validateInput(ReEvaluateRequest input) {
    if (isNull(input) || isNull(input.year())) {
      throw new IllegalArgumentException(INVALID_INPUT_MESSAGE);
    }
  }
}
