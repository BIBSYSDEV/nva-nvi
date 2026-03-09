package no.sikt.nva.nvi.events;

import static no.sikt.nva.nvi.common.EnvironmentFixtures.getDataEntryUpdateHandlerEnvironment;

import com.amazonaws.services.lambda.runtime.Context;
import com.amazonaws.services.lambda.runtime.events.SQSEvent;
import java.util.List;
import no.sikt.nva.nvi.common.queue.FakeSqsClient;
import no.sikt.nva.nvi.events.db.DataEntryUpdateHandler;
import no.unit.nva.stubs.FakeContext;
import nva.commons.core.Environment;
import software.amazon.awssdk.services.sns.model.PublishRequest;

public class DataEntryUpdateHandlerContext {
  private static final Context CONTEXT = new FakeContext();
  private static final Environment ENVIRONMENT = getDataEntryUpdateHandlerEnvironment();
  private final DataEntryUpdateHandler handler;
  private final FakeSqsClient queueClient;
  private final FakeNotificationClient snsClient;

  public DataEntryUpdateHandlerContext() {
    queueClient = new FakeSqsClient();
    snsClient = new FakeNotificationClient();
    handler = new DataEntryUpdateHandler(snsClient, ENVIRONMENT, queueClient);
  }

  public DataEntryUpdateHandlerContext(
      FakeSqsClient queueClient, FakeNotificationClient snsClient) {
    this.queueClient = queueClient;
    this.snsClient = snsClient;
    this.handler = new DataEntryUpdateHandler(snsClient, ENVIRONMENT, queueClient);
  }

  public DataEntryUpdateHandler getHandler() {
    return handler;
  }

  public FakeSqsClient getQueueClient() {
    return queueClient;
  }

  public FakeNotificationClient getSnsClient() {
    return snsClient;
  }

  public List<PublishRequest> getPublishedMessages() {
    return snsClient.getPublishedMessages();
  }

  public void handleEvent(SQSEvent event) {
    handler.handleRequest(event, CONTEXT);
  }
}
