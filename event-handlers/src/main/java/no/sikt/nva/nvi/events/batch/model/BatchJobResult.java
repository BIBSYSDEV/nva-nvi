package no.sikt.nva.nvi.events.batch.model;

import static no.sikt.nva.nvi.common.utils.CollectionUtils.copyOfNullable;

import java.util.Collection;

public record BatchJobResult(
    Collection<BatchJobMessage> messages, Collection<StartBatchJobRequest> continuationEvents) {

  public BatchJobResult {
    messages = copyOfNullable(messages);
    continuationEvents = copyOfNullable(continuationEvents);
  }
}
