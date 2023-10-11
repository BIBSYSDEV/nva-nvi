package no.sikt.nva.nvi.events.batch;

import static no.sikt.nva.nvi.common.service.NviService.defaultNviService;
import static no.sikt.nva.nvi.events.batch.BatchScanStartHandler.EVENT_BUS_NAME;
import com.amazonaws.services.lambda.runtime.Context;
import no.sikt.nva.nvi.common.model.ListingResult;
import no.sikt.nva.nvi.common.service.NviService;
import no.sikt.nva.nvi.events.model.ScanDatabaseRequest;
import no.unit.nva.events.handlers.EventHandler;
import no.unit.nva.events.models.AwsEventBridgeEvent;
import nva.commons.core.JacocoGenerated;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import software.amazon.awssdk.http.urlconnection.UrlConnectionHttpClient;
import software.amazon.awssdk.services.eventbridge.EventBridgeClient;
import software.amazon.awssdk.services.eventbridge.model.PutEventsRequest;
import software.amazon.awssdk.services.eventbridge.model.PutEventsRequestEntry;

public class EventBasedBatchScanHandler extends EventHandler<ScanDatabaseRequest, ListingResult> {

    public static final String DETAIL_TYPE = "NO_DETAIL_TYPE";

    private final NviService nviService;
    private final EventBridgeClient eventBridgeClient;
    private final Logger logger = LoggerFactory.getLogger(EventBasedBatchScanHandler.class);

    @JacocoGenerated
    public EventBasedBatchScanHandler() {
        this(defaultNviService(), defaultEventBridgeClient());
    }

    public EventBasedBatchScanHandler(NviService nviService, EventBridgeClient eventBridgeClient) {
        super(ScanDatabaseRequest.class);
        this.nviService = nviService;
        this.eventBridgeClient = eventBridgeClient;
    }

    @Override
    protected ListingResult processInput(ScanDatabaseRequest input, AwsEventBridgeEvent<ScanDatabaseRequest> event,
                                         Context context) {
        logger.info("Query starting point: {}", input.startMarker());

        var batchResult = nviService.refresh(input.pageSize(), input.startMarker());
        logger.info("Batch result: {}", batchResult);
        if (batchResult.shouldContinueScan()) {
            sendEventToInvokeNewRefreshRowVersionExecution(input, context, batchResult);
        }
        return batchResult;
    }

    @JacocoGenerated
    private static EventBridgeClient defaultEventBridgeClient() {
        return EventBridgeClient.builder().httpClientBuilder(UrlConnectionHttpClient.builder()).build();
    }

    private void sendEventToInvokeNewRefreshRowVersionExecution(ScanDatabaseRequest input, Context context,
                                                                ListingResult result) {
        var newEvent = input.newScanDatabaseRequest(result.startMarker())
                           .createNewEventEntry(EVENT_BUS_NAME, DETAIL_TYPE, context.getInvokedFunctionArn());
        sendEvent(newEvent);
    }

    private void sendEvent(PutEventsRequestEntry putEventRequestEntry) {
        var putEventRequest = PutEventsRequest.builder().entries(putEventRequestEntry).build();
        eventBridgeClient.putEvents(putEventRequest);
    }
}
