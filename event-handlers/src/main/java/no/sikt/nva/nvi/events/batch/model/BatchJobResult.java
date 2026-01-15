package no.sikt.nva.nvi.events.batch.model;

import static java.util.Collections.emptyList;
import static no.sikt.nva.nvi.common.utils.CollectionUtils.copyOfNullable;

import java.util.Collection;

public record BatchJobResult(
    Collection<BatchJobMessage> messages, Collection<StartBatchJobRequest> continuationEvents) {

  public BatchJobResult {
    messages = copyOfNullable(messages);
    continuationEvents = copyOfNullable(continuationEvents);
  }

  public static BatchJobResult createInitialBatchJobResult(
      Collection<StartBatchJobRequest> continuationEvents) {
    return new BatchJobResult(emptyList(), continuationEvents);
  }

  public static BatchJobResult createTerminalBatchJobResult() {
    return new BatchJobResult(emptyList(), emptyList());
  }
}
