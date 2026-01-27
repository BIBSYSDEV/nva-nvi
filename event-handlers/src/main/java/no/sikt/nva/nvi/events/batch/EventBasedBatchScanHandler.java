package no.sikt.nva.nvi.events.batch;

import static no.sikt.nva.nvi.common.utils.BatchScanUtil.defaultNviService;

import com.amazonaws.services.lambda.runtime.Context;
import no.sikt.nva.nvi.common.db.Dao;
import no.sikt.nva.nvi.common.model.ListingResult;
import no.sikt.nva.nvi.common.utils.BatchScanUtil;
import no.sikt.nva.nvi.events.model.ScanDatabaseRequest;
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

/**
 * @deprecated Replaced by new BatchJob system.
 */
@Deprecated(since = "2026-01-27", forRemoval = true)
public class EventBasedBatchScanHandler
    extends EventHandler<ScanDatabaseRequest, ListingResult<Dao>> {

  private static final String DETAIL_TYPE = "NO_DETAIL_TYPE";
  private static final String EVENT_BUS_NAME = new Environment().readEnv("EVENT_BUS_NAME");
  private final BatchScanUtil batchScanUtil;
  private final EventBridgeClient eventBridgeClient;
  private final Logger logger = LoggerFactory.getLogger(EventBasedBatchScanHandler.class);

  @JacocoGenerated
  public EventBasedBatchScanHandler() {
    this(defaultNviService(), defaultEventBridgeClient());
  }

  public EventBasedBatchScanHandler(
      BatchScanUtil batchScanUtil, EventBridgeClient eventBridgeClient) {
    super(ScanDatabaseRequest.class);
    this.batchScanUtil = batchScanUtil;
    this.eventBridgeClient = eventBridgeClient;
  }

  @Override
  protected ListingResult<Dao> processInput(
      ScanDatabaseRequest input, AwsEventBridgeEvent<ScanDatabaseRequest> event, Context context) {
    logger.info("Processing request: {}", input);

    var batchResult =
        batchScanUtil.migrateAndUpdateVersion(input.pageSize(), input.startMarker(), input.types());
    logger.info("Batch result: {}", batchResult);
    if (batchResult.shouldContinueScan()) {
      logger.info("Sending event to trigger scan of next batch");
      sendEventToInvokeNewRefreshRowVersionExecution(input, context, batchResult);
    }
    return batchResult;
  }

  @JacocoGenerated
  private static EventBridgeClient defaultEventBridgeClient() {
    return EventBridgeClient.builder().httpClientBuilder(UrlConnectionHttpClient.builder()).build();
  }

  private void sendEventToInvokeNewRefreshRowVersionExecution(
      ScanDatabaseRequest input, Context context, ListingResult<Dao> result) {
    var newEvent =
        input
            .newScanDatabaseRequest(result.getStartMarker())
            .createNewEventEntry(EVENT_BUS_NAME, DETAIL_TYPE, context.getInvokedFunctionArn());
    sendEvent(newEvent);
  }

  private void sendEvent(PutEventsRequestEntry putEventRequestEntry) {
    var putEventRequest = PutEventsRequest.builder().entries(putEventRequestEntry).build();
    eventBridgeClient.putEvents(putEventRequest);
  }
}
