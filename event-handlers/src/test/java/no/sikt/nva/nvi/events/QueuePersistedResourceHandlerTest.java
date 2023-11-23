package no.sikt.nva.nvi.events;

import static no.sikt.nva.nvi.events.evaluator.TestUtils.createS3Event;
import static no.unit.nva.testutils.RandomDataGenerator.objectMapper;
import static no.unit.nva.testutils.RandomDataGenerator.randomUri;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import com.amazonaws.services.lambda.runtime.Context;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import no.sikt.nva.nvi.events.evaluator.FakeSqsClient;
import no.sikt.nva.nvi.events.model.PersistedResourceMessage;
import nva.commons.core.Environment;
import nva.commons.logutils.LogUtils;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

public class QueuePersistedResourceHandlerTest {

    private static final Environment environment = new Environment();
    private static final String queueUrl = environment.readEnv("QUEUE_PERSISTED_RESOURCE_QUEUE_URL");
    private final Context context = mock(Context.class);
    private QueuePersistedResourceHandler handler;
    private ByteArrayOutputStream output;
    private FakeSqsClient sqsClient;

    @BeforeEach
    void setUp() {
        sqsClient = new FakeSqsClient();
        handler = new QueuePersistedResourceHandler(sqsClient, environment);
        output = new ByteArrayOutputStream();
    }

    @Test
    void shouldLogErrorAndThrowExceptionWhenInvalidEventReferenceReceived() throws IOException {
        var invalidEvent = createS3Event(null);
        var appender = LogUtils.getTestingAppenderForRootLogger();
        assertThrows(RuntimeException.class, () -> handler.handleRequest(invalidEvent, output, context));
        assertThat(appender.getMessages(), containsString("Invalid EventReference, missing uri"));
    }

    @Test
    void shouldQueuePersistedResourceToEvaluatePublicationQueueWhenValidEventReferenceReceived() throws IOException {
        var fileUri = randomUri();
        var event = createS3Event(fileUri);
        handler.handleRequest(event, output, context);
        var sentMessages = sqsClient.getSentMessages();
        assertEquals(1, sentMessages.size());
        var sentMessage = sentMessages.get(0);
        var body = objectMapper.readValue(sentMessage.messageBody(), PersistedResourceMessage.class);
        assertEquals(queueUrl, sentMessage.queueUrl());
        assertEquals(fileUri, body.resourceFileUri());
    }
}
