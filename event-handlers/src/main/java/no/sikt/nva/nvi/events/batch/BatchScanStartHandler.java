package no.sikt.nva.nvi.events.batch;

import com.amazonaws.services.lambda.runtime.Context;
import com.amazonaws.services.lambda.runtime.RequestStreamHandler;
import java.io.InputStream;
import java.io.OutputStream;
import no.sikt.nva.nvi.events.model.EventDetail;
import no.sikt.nva.nvi.events.model.ScanDatabaseRequest;
import nva.commons.core.Environment;
import nva.commons.core.JacocoGenerated;
import software.amazon.awssdk.http.urlconnection.UrlConnectionHttpClient;
import software.amazon.awssdk.services.eventbridge.EventBridgeClient;

public class BatchScanStartHandler implements RequestStreamHandler {

    private static final String EVENT_BUS_NAME = new Environment().readEnv("EVENT_BUS_NAME");
    private static final String OUTPUT_EVENT_TOPIC = "OUTPUT_EVENT_TOPIC";
    private static final String TOPIC = new Environment().readEnv(OUTPUT_EVENT_TOPIC);
    private static final String DETAIL_TYPE = "NO_DETAIL_TYPE";
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

        ScanDatabaseRequest.builder()
            .fromInputStream(input)
            .withTopic(TOPIC)
            .build()
            .sendEvent(client, new EventDetail(EVENT_BUS_NAME, DETAIL_TYPE, context.getInvokedFunctionArn()));
    }

    @JacocoGenerated
    private static EventBridgeClient defaultClient() {
        return EventBridgeClient.builder().httpClientBuilder(UrlConnectionHttpClient.builder()).build();
    }
}
