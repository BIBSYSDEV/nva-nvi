package no.sikt.nva.nvi.events.batch;

import static no.unit.nva.testutils.RandomDataGenerator.randomString;
import static nva.commons.core.attempt.Try.attempt;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.core.Is.is;
import static org.hamcrest.core.IsEqual.equalTo;
import com.amazonaws.services.lambda.runtime.Context;
import com.amazonaws.services.lambda.runtime.RequestStreamHandler;
import com.fasterxml.jackson.core.JsonProcessingException;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import no.sikt.nva.nvi.events.model.ScanDatabaseRequest;
import no.unit.nva.commons.json.JsonUtils;
import no.unit.nva.stubs.FakeContext;
import no.unit.nva.stubs.FakeEventBridgeClient;
import nva.commons.core.Environment;
import nva.commons.core.JacocoGenerated;
import nva.commons.core.ioutils.IoUtils;
import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import software.amazon.awssdk.http.urlconnection.UrlConnectionHttpClient;
import software.amazon.awssdk.services.eventbridge.EventBridgeClient;
import software.amazon.awssdk.services.eventbridge.model.PutEventsRequest;
import software.amazon.awssdk.services.eventbridge.model.PutEventsRequestEntry;
import software.amazon.awssdk.services.eventbridge.model.PutEventsResponse;

public class BatchScanStartHandlerTest {

    public static final String TOPIC = "OUTPUT_EVENT_TOPIC";

    public static final int NOT_SET_PAGE_SIZE = 0;
    private final FakeContext context = new FakeContext() {
        @Override
        public String getInvokedFunctionArn() {
            return randomString();
        }
    };

    @Test
    void shouldSendInitialScanMessageWithDefaultPageSizeWhenPageSizeIsNotSet() throws IOException {
        var client = new FakeEventBridgeClient();
        var handler = new BatchScanStartHandler(client);
        var scanDatabaseRequest = new ScanDatabaseRequest(NOT_SET_PAGE_SIZE, null, TOPIC);
        var request = IoUtils.stringToStream(scanDatabaseRequest.toJsonString());
        handler.handleRequest(request, null, context);
        assertThat(client.getRequestEntries(), hasSize(1));
        var eventDetail = client.getRequestEntries().get(0).detail();
        var sentRequest = JsonUtils.dtoObjectMapper.readValue(eventDetail, ScanDatabaseRequest.class);
        assertThat(sentRequest.getPageSize(), is(equalTo(ScanDatabaseRequest.DEFAULT_PAGE_SIZE)));
    }

    @Test
    void shouldSendInitialScanMessageForInitiatingBatchScanning() {
        FakeEventBridgeClient client = new FakeEventBridgeClient();
        BatchScanStartHandler handler = new BatchScanStartHandler(client);
        ScanDatabaseRequest scanDatabaseRequest = new ScanDatabaseRequest(1, null, TOPIC);
        var request = IoUtils.stringToStream(scanDatabaseRequest.toJsonString());
        handler.handleRequest(request, null, context);
        assertThat(client.getRequestEntries(), hasSize(1));
    }
}
