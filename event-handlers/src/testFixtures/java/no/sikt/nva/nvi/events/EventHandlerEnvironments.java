package no.sikt.nva.nvi.events;

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

import no.sikt.nva.nvi.common.FakeEnvironment;

public final class EventHandlerEnvironments {

  private EventHandlerEnvironments() {}

  public static FakeEnvironment getCristinNviReportEventConsumerEnvironment() {
    return getHandlerEnvironment(EXPANDED_RESOURCES_BUCKET);
  }

  public static FakeEnvironment getDataEntryUpdateHandlerEnvironment() {
    return getHandlerEnvironment(
        DB_EVENTS_QUEUE_URL,
        INDEX_DLQ,
        TOPIC_CANDIDATE_INSERT,
        TOPIC_CANDIDATE_APPLICABLE_UPDATE,
        TOPIC_CANDIDATE_NOT_APPLICABLE_UPDATE,
        TOPIC_CANDIDATE_REMOVE,
        TOPIC_APPROVAL_INSERT,
        TOPIC_APPROVAL_UPDATE,
        TOPIC_APPROVAL_REMOVE);
  }

  public static FakeEnvironment getDynamoDbEventToQueueHandlerEnvironment() {
    return getHandlerEnvironment(DB_EVENTS_QUEUE_URL, INDEX_DLQ);
  }

  public static FakeEnvironment getQueuePersistedResourceHandlerEnvironment() {
    return getHandlerEnvironment(PERSISTED_RESOURCE_QUEUE_URL);
  }

  public static FakeEnvironment getEvaluateNviCandidateHandlerEnvironment() {
    return getHandlerEnvironment(EXPANDED_RESOURCES_BUCKET);
  }

  public static FakeEnvironment getReEvaluateNviCandidateHandlerEnvironment() {
    return getHandlerEnvironment(
        EVENT_BUS_NAME,
        EXPANDED_RESOURCES_BUCKET,
        TOPIC_REEVALUATE_CANDIDATES,
        PERSISTED_RESOURCE_QUEUE_URL);
  }

  public static FakeEnvironment getStartBatchJobHandlerEnvironment() {
    return getHandlerEnvironment(BATCH_JOB_QUEUE_URL, EVENT_BUS_NAME, PROCESSING_ENABLED);
  }
}
