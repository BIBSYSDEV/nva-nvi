package no.sikt.nva.nvi.events.evaluator;

import static no.unit.nva.commons.json.JsonUtils.dtoObjectMapper;
import static no.unit.nva.testutils.RandomDataGenerator.objectMapper;
import static nva.commons.core.attempt.Try.attempt;

import com.amazonaws.services.lambda.runtime.events.SQSEvent;
import com.amazonaws.services.lambda.runtime.events.SQSEvent.SQSMessage;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.util.List;
import no.sikt.nva.nvi.events.model.PersistedResourceMessage;
import no.unit.nva.events.models.AwsEventBridgeDetail;
import no.unit.nva.events.models.AwsEventBridgeEvent;
import no.unit.nva.events.models.EventReference;

public final class TestUtils {

  private TestUtils() {}

  public static InputStream createS3Event(URI uri) throws IOException {
    return createEventInputStream(new EventReference("", uri));
  }

  public static SQSEvent createEvent(PersistedResourceMessage persistedResourceMessage) {
    var sqsEvent = new SQSEvent();
    var message = new SQSMessage();
    var body =
        attempt(() -> objectMapper.writeValueAsString(persistedResourceMessage)).orElseThrow();
    message.setBody(body);
    sqsEvent.setRecords(List.of(message));
    return sqsEvent;
  }

  public static SQSEvent sqsEventWithSingleMessageWithBody(String body) {
    var sqsEvent = new SQSEvent();
    var message = new SQSMessage();
    message.setBody(body);
    sqsEvent.setRecords(List.of(message));
    return sqsEvent;
  }

  private static InputStream createEventInputStream(EventReference eventReference)
      throws IOException {
    var detail = new AwsEventBridgeDetail<EventReference>();
    detail.setResponsePayload(eventReference);
    var event = new AwsEventBridgeEvent<AwsEventBridgeDetail<EventReference>>();
    event.setDetail(detail);
    return new ByteArrayInputStream(dtoObjectMapper.writeValueAsBytes(event));
  }
}
