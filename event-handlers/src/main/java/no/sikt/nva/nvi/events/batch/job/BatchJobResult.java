package no.sikt.nva.nvi.events.batch.job;

import static java.util.Collections.emptyList;
import static no.sikt.nva.nvi.common.utils.CollectionUtils.copyOfNullable;

import java.util.Collection;
import java.util.List;
import no.sikt.nva.nvi.events.batch.message.BatchJobMessage;
import no.sikt.nva.nvi.events.batch.request.BatchJobRequest;

public record BatchJobResult(
    Collection<BatchJobMessage> messages,
    Collection<? extends BatchJobRequest> continuationEvents) {

  public BatchJobResult {
    messages = copyOfNullable(messages);
    continuationEvents = copyOfNullable(continuationEvents);
  }

  public static BatchJobResult createInitialBatchJobResult(
      Collection<? extends BatchJobRequest> continuationEvents) {
    return new BatchJobResult(emptyList(), continuationEvents);
  }

  public static BatchJobResult createInitialBatchJobResult(BatchJobRequest continuationEvent) {
    return new BatchJobResult(emptyList(), List.of(continuationEvent));
  }

  public static BatchJobResult createTerminalBatchJobResult(Collection<BatchJobMessage> messages) {
    return new BatchJobResult(messages, emptyList());
  }
}
