package no.sikt.nva.nvi.events.batch.model;

import static no.sikt.nva.nvi.common.utils.CollectionUtils.copyOfNullable;

import java.util.List;

public record BatchJobResult(
    List<BatchJobMessage> messages, List<StartBatchJobRequest> continuationEvents) {

  public BatchJobResult {
    messages = copyOfNullable(messages);
    continuationEvents = copyOfNullable(continuationEvents);
  }
}
