package no.sikt.nva.nvi.events;

import static no.sikt.nva.nvi.events.evaluator.TestUtils.createS3Event;
import static no.unit.nva.testutils.RandomDataGenerator.objectMapper;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.amazonaws.services.lambda.runtime.Context;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.net.URI;
import no.sikt.nva.nvi.common.queue.FakeSqsClient;
import no.sikt.nva.nvi.events.model.PersistedResourceMessage;
import no.unit.nva.stubs.FakeContext;
import nva.commons.core.Environment;
import nva.commons.logutils.LogUtils;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class QueuePersistedResourceHandlerTest {

  private static final Environment environment = new Environment();
  private static final String queueUrl = environment.readEnv("PERSISTED_RESOURCE_QUEUE_URL");
  private final Context context = new FakeContext();
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
    assertThrows(
        RuntimeException.class, () -> handler.handleRequest(invalidEvent, output, context));
    assertThat(appender.getMessages(), containsString("Invalid EventReference, missing uri"));
  }

  @ParameterizedTest(name = "shouldNotQueueResourcesThatAreNotPublications {0}")
  @ValueSource(
      strings = {
        "s3://persisted-resources-884807050265/tickets/123.gz",
        "https://example.com/someOtherThing/123"
      })
  void shouldNotQueueResourcesThatAreNotPublications(String uri) throws IOException {
    var ticketEvent = createS3Event(URI.create(uri));
    handler.handleRequest(ticketEvent, output, context);
    var sentMessages = sqsClient.getSentMessages();
    assertEquals(0, sentMessages.size());
  }

  @Test
  void shouldQueuePersistedResourceToEvaluatePublicationQueueWhenValidEventReferenceReceived()
      throws IOException {
    var fileUri = URI.create("s3://persisted-resources-884807050265/resources/123.gz");
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
