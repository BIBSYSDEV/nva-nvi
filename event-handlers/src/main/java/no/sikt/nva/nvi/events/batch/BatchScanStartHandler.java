package no.sikt.nva.nvi.events.batch;

import static nva.commons.core.attempt.Try.attempt;
import com.amazonaws.services.lambda.runtime.Context;
import com.amazonaws.services.lambda.runtime.RequestStreamHandler;
import com.fasterxml.jackson.core.JsonProcessingException;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import no.sikt.nva.nvi.events.model.ScanDatabaseRequest;
import no.unit.nva.commons.json.JsonUtils;
import nva.commons.core.Environment;
import nva.commons.core.JacocoGenerated;
import nva.commons.core.ioutils.IoUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import software.amazon.awssdk.http.urlconnection.UrlConnectionHttpClient;
import software.amazon.awssdk.services.eventbridge.EventBridgeClient;
import software.amazon.awssdk.services.eventbridge.model.PutEventsRequest;
import software.amazon.awssdk.services.eventbridge.model.PutEventsRequestEntry;
import software.amazon.awssdk.services.eventbridge.model.PutEventsResponse;

public class BatchScanStartHandler implements RequestStreamHandler {

    public static final String INFORMATION_MESSAGE =
        "Starting scanning with pageSize equal to: %s. Set 'pageSize' between [1,1000] "
        + "if you want a different pageSize value.";
    public static final String EVENT_BUS_NAME = new Environment().readEnv("EVENT_BUS_NAME");
    public static final String OUTPUT_EVENT_TOPIC = "OUTPUT_EVENT_TOPIC";
    public static final String DETAIL_TYPE = "NO_DETAIL_TYPE";
    private static final Logger logger = LoggerFactory.getLogger(BatchScanStartHandler.class);
    private final EventBridgeClient client;

    @JacocoGenerated
    public BatchScanStartHandler() {
        this(defaultClient());
    }

    public BatchScanStartHandler(EventBridgeClient client) {
        this.client = client;
    }

    @Override
    public void handleRequest(InputStream input, OutputStream output, Context context) {
        attempt(() -> IoUtils.streamToString(input))
            .map(BatchScanStartHandler::toScanDatabaseRequest)
            .map(request -> toNewEvent(context, request))
            .map(this::sendEvent)
            .orElseThrow();
    }

    private static PutEventsRequestEntry toNewEvent(Context context, ScanDatabaseRequest request) {
        logger.info(String.format(INFORMATION_MESSAGE, request.getPageSize()));
        return request.createNewEventEntry(EVENT_BUS_NAME, DETAIL_TYPE, context.getInvokedFunctionArn());
    }

    private static ScanDatabaseRequest toScanDatabaseRequest(String input) throws JsonProcessingException {
        var request = JsonUtils.dtoObjectMapper.readValue(input, ScanDatabaseRequest.class);
        request.setTopic(new Environment().readEnv(OUTPUT_EVENT_TOPIC));
        return request;
    }

    @JacocoGenerated
    private static EventBridgeClient defaultClient() {
        return EventBridgeClient.builder().httpClientBuilder(UrlConnectionHttpClient.builder()).build();
    }

    private PutEventsResponse sendEvent(PutEventsRequestEntry event) {
        PutEventsRequest putEventsRequest = PutEventsRequest.builder().entries(event).build();
        var response = client.putEvents(putEventsRequest);
        logger.info(response.toString());
        return response;
    }
}
