package no.sikt.nva.nvi.events.batch;

import com.amazonaws.services.lambda.runtime.Context;
import com.amazonaws.services.lambda.runtime.RequestStreamHandler;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.List;
import java.util.UUID;
import no.sikt.nva.nvi.common.queue.NviQueueClient;
import no.sikt.nva.nvi.common.queue.NviReceiveMessage;
import no.sikt.nva.nvi.common.queue.QueueClient;
import no.sikt.nva.nvi.common.utils.BatchScanUtil;
import nva.commons.core.Environment;
import nva.commons.core.JacocoGenerated;

public class BatchScanRecoveryHandler implements RequestStreamHandler {

  protected static final String RECOVERY_BATCH_SCAN_QUEUE = "BATCH_SCAN_RECOVERY_QUEUE";
  protected static final String CANDIDATE_IDENTIFIER = "candidateIdentifier";
  private final QueueClient queueClient;
  private final Environment environment;
  private final BatchScanUtil batchScanUtil;

  @JacocoGenerated
  public BatchScanRecoveryHandler() {
    this(new NviQueueClient(), BatchScanUtil.defaultNviService(), new Environment());
  }

  public BatchScanRecoveryHandler(
      QueueClient queueClient, BatchScanUtil batchScanUtil, Environment environment) {
    this.queueClient = queueClient;
    this.batchScanUtil = batchScanUtil;
    this.environment = environment;
  }

  @Override
  public void handleRequest(InputStream inputStream, OutputStream outputStream, Context context)
      throws IOException {
    var request = RecoveryEvent.fromInputStream(inputStream);
    processRequest(request);
  }

  private static List<UUID> getCandidateIdentifiers(List<NviReceiveMessage> messages) {
    return messages.stream()
        .map(message -> message.messageAttributes().get(CANDIDATE_IDENTIFIER))
        .map(UUID::fromString)
        .toList();
  }

  private void processRequest(RecoveryEvent request) {
    int counter = 0;
    while (counter < request.numberOfMessageToProcess()) {
      var messages =
          queueClient
              .receiveMessage(
                  environment.readEnv(RECOVERY_BATCH_SCAN_QUEUE),
                  request.numberOfMessageToProcess())
              .messages();

      if (messages.isEmpty()) {
        break;
      }

      var candidateIdentifiers = getCandidateIdentifiers(messages);
      batchScanUtil.migrateAndUpdateVersion(candidateIdentifiers);
      messages.forEach(
          message ->
              queueClient.deleteMessage(
                  environment.readEnv(RECOVERY_BATCH_SCAN_QUEUE), message.receiptHandle()));
      counter += messages.size();
    }
  }
}
