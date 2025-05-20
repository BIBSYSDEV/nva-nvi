package no.sikt.nva.nvi.events;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import no.sikt.nva.nvi.common.notification.NotificationClient;
import no.sikt.nva.nvi.common.notification.NviPublishMessageResponse;
import software.amazon.awssdk.services.sns.model.PublishRequest;

public class FakeNotificationClient implements NotificationClient<NviPublishMessageResponse> {

  private final List<PublishRequest> publishedMessages = new ArrayList<>();

  public List<PublishRequest> getPublishedMessages() {
    return publishedMessages;
  }

  @Override
  public NviPublishMessageResponse publish(String message, String topic) {
    var request = createRequest(message, topic);
    publishedMessages.add(request);
    return new NviPublishMessageResponse(UUID.randomUUID().toString());
  }

  private static PublishRequest createRequest(String message, String topic) {
    return PublishRequest.builder().message(message).topicArn(topic).build();
  }
}
