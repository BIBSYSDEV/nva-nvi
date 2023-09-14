package no.sikt.nva.nvi.events.batch;

import static no.sikt.nva.nvi.common.service.NviService.defaultNviService;
import static no.sikt.nva.nvi.events.batch.BatchScanStartHandler.EVENT_BUS_NAME;
import com.amazonaws.services.lambda.runtime.Context;
import no.sikt.nva.nvi.common.db.Candidate;
import no.sikt.nva.nvi.common.service.NviService;
import no.sikt.nva.nvi.common.model.ListingResult;
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

public class EventBasedBatchScanHandler extends EventHandler<ScanDatabaseRequest, Void> {

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
    protected Void processInput(ScanDatabaseRequest input, AwsEventBridgeEvent<ScanDatabaseRequest> event,
                                Context context) {
        var batchResult = nviService.refresh(input.getPageSize(), input.getStartMarker());
        logger.info("Query starting point:" + input.getStartMarker());
//        if (result.isTruncated()) {
//            sendEventToInvokeNewRefreshRowVersionExecution(input, context, result);
//        }
        return null;
    }

    @JacocoGenerated
    private static EventBridgeClient defaultEventBridgeClient() {
        return EventBridgeClient.builder()
                   .httpClientBuilder(UrlConnectionHttpClient.builder())
                   .build();
    }

//    private void sendEventToInvokeNewRefreshRowVersionExecution(ScanDatabaseRequest input,
//                                                                Context context,
//                                                                ListingResult<Candidate> result) {
//        PutEventsRequestEntry newEvent = input
//                                             .newScanDatabaseRequest(result.getStartMarker())
//                                             .createNewEventEntry(EVENT_BUS_NAME, DETAIL_TYPE,
//                                                                  context.getInvokedFunctionArn());
//        sendEvent(newEvent);
//    }
//
//    private void sendEvent(PutEventsRequestEntry putEventRequestEntry) {
//        PutEventsRequest putEventRequest = PutEventsRequest.builder()
//                                               .entries(putEventRequestEntry)
//                                               .build();
//        eventBridgeClient.putEvents(putEventRequest);
//    }
}
