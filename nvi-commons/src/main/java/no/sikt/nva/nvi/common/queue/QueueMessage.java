package no.sikt.nva.nvi.common.queue;

import java.util.Map;
import no.unit.nva.commons.json.JsonSerializable;
import software.amazon.awssdk.services.sqs.model.MessageAttributeValue;

public record QueueMessage(JsonSerializable body, Map<String, MessageAttributeValue> attributes) {

  public QueueMessage(JsonSerializable body, QueueMessageAttributesBuilder attributes) {
    this(body, attributes.toMessageAttributeValues());
  }
}
