package no.sikt.nva.nvi.events;

import static no.sikt.nva.nvi.common.EnvironmentFixtures.getDynamoDbEventToQueueHandlerEnvironment;
import static org.mockito.Mockito.mock;

import com.amazonaws.services.lambda.runtime.Context;
import com.amazonaws.services.lambda.runtime.events.DynamodbEvent;
import no.sikt.nva.nvi.common.queue.FakeSqsClient;
import no.sikt.nva.nvi.events.db.DynamoDbEventToQueueHandler;
import nva.commons.core.Environment;

public class DynamoDbToEventQueueHandlerContext {
  private static final Context CONTEXT = mock(Context.class);
  private static final Environment ENVIRONMENT = getDynamoDbEventToQueueHandlerEnvironment();
  private final DynamoDbEventToQueueHandler handler;
  private final FakeSqsClient queueClient;

  public DynamoDbToEventQueueHandlerContext() {
    queueClient = new FakeSqsClient();
    handler = new DynamoDbEventToQueueHandler(queueClient, ENVIRONMENT);
  }

  public DynamoDbToEventQueueHandlerContext(FakeSqsClient queueClient) {
    this.queueClient = queueClient;
    this.handler = new DynamoDbEventToQueueHandler(queueClient, ENVIRONMENT);
  }

  public DynamoDbEventToQueueHandler getHandler() {
    return handler;
  }

  public FakeSqsClient getQueueClient() {
    return queueClient;
  }

  public void handleEvent(DynamodbEvent event) {
    handler.handleRequest(event, CONTEXT);
  }
}
