package no.sikt.nva.nvi.events;

import static java.util.Objects.isNull;
import static no.sikt.nva.nvi.common.EnvironmentFixtures.BATCH_JOB_QUEUE_URL;
import static no.sikt.nva.nvi.common.EnvironmentFixtures.DB_EVENTS_QUEUE_URL;
import static no.sikt.nva.nvi.common.EnvironmentFixtures.EVENT_BUS_NAME;
import static no.sikt.nva.nvi.common.EnvironmentFixtures.EXPANDED_RESOURCES_BUCKET;
import static no.sikt.nva.nvi.common.EnvironmentFixtures.INDEX_DLQ;
import static no.sikt.nva.nvi.common.EnvironmentFixtures.PERSISTED_RESOURCE_QUEUE_URL;
import static no.sikt.nva.nvi.common.EnvironmentFixtures.PROCESSING_ENABLED;
import static no.sikt.nva.nvi.common.EnvironmentFixtures.TOPIC_APPROVAL_INSERT;
import static no.sikt.nva.nvi.common.EnvironmentFixtures.TOPIC_APPROVAL_REMOVE;
import static no.sikt.nva.nvi.common.EnvironmentFixtures.TOPIC_APPROVAL_UPDATE;
import static no.sikt.nva.nvi.common.EnvironmentFixtures.TOPIC_CANDIDATE_APPLICABLE_UPDATE;
import static no.sikt.nva.nvi.common.EnvironmentFixtures.TOPIC_CANDIDATE_INSERT;
import static no.sikt.nva.nvi.common.EnvironmentFixtures.TOPIC_CANDIDATE_NOT_APPLICABLE_UPDATE;
import static no.sikt.nva.nvi.common.EnvironmentFixtures.TOPIC_CANDIDATE_REMOVE;
import static no.sikt.nva.nvi.common.EnvironmentFixtures.TOPIC_REEVALUATE_CANDIDATES;
import static no.sikt.nva.nvi.common.EnvironmentFixtures.getHandlerEnvironment;

import java.util.Map;
import java.util.function.Supplier;
import no.sikt.nva.nvi.common.EnvironmentFixtures;
import no.sikt.nva.nvi.common.FakeEnvironment;
import no.sikt.nva.nvi.events.batch.ReEvaluateNviCandidatesHandler;
import no.sikt.nva.nvi.events.batch.StartBatchJobHandler;
import no.sikt.nva.nvi.events.cristin.CristinNviReportEventConsumer;
import no.sikt.nva.nvi.events.db.DataEntryUpdateHandler;
import no.sikt.nva.nvi.events.db.DynamoDbEventToQueueHandler;
import no.sikt.nva.nvi.events.evaluator.EvaluateNviCandidateHandler;

public final class EventHandlerEnvironments {

  private static final Map<Class<?>, Supplier<FakeEnvironment>> HANDLER_ENVIRONMENTS =
      Map.ofEntries(
          entry(CristinNviReportEventConsumer.class, EXPANDED_RESOURCES_BUCKET),
          entry(
              DataEntryUpdateHandler.class,
              DB_EVENTS_QUEUE_URL,
              INDEX_DLQ,
              TOPIC_CANDIDATE_INSERT,
              TOPIC_CANDIDATE_APPLICABLE_UPDATE,
              TOPIC_CANDIDATE_NOT_APPLICABLE_UPDATE,
              TOPIC_CANDIDATE_REMOVE,
              TOPIC_APPROVAL_INSERT,
              TOPIC_APPROVAL_UPDATE,
              TOPIC_APPROVAL_REMOVE),
          entry(DynamoDbEventToQueueHandler.class, DB_EVENTS_QUEUE_URL, INDEX_DLQ),
          entry(QueuePersistedResourceHandler.class, PERSISTED_RESOURCE_QUEUE_URL),
          entry(EvaluateNviCandidateHandler.class, EXPANDED_RESOURCES_BUCKET),
          entry(
              ReEvaluateNviCandidatesHandler.class,
              EVENT_BUS_NAME,
              EXPANDED_RESOURCES_BUCKET,
              TOPIC_REEVALUATE_CANDIDATES,
              PERSISTED_RESOURCE_QUEUE_URL),
          entry(
              StartBatchJobHandler.class, BATCH_JOB_QUEUE_URL, EVENT_BUS_NAME, PROCESSING_ENABLED));

  private EventHandlerEnvironments() {}

  public static FakeEnvironment forHandler(Class<?> handlerClass) {
    var environmentSupplier = HANDLER_ENVIRONMENTS.get(handlerClass);
    if (isNull(environmentSupplier)) {
      throw new IllegalArgumentException(
          "No test environment defined for " + handlerClass.getSimpleName());
    }
    return environmentSupplier.get();
  }

  private static Map.Entry<Class<?>, Supplier<FakeEnvironment>> entry(
      Class<?> handlerClass, EnvironmentFixtures... environmentVariables) {
    return Map.entry(handlerClass, () -> getHandlerEnvironment(environmentVariables));
  }
}
