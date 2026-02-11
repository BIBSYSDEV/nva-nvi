package no.sikt.nva.nvi.events.batch;

import com.amazonaws.services.lambda.runtime.Context;
import com.amazonaws.services.lambda.runtime.RequestHandler;
import com.amazonaws.services.lambda.runtime.events.SQSBatchResponse;
import com.amazonaws.services.lambda.runtime.events.SQSEvent;
import java.util.ArrayList;
import no.sikt.nva.nvi.common.CandidateMigrationService;
import no.sikt.nva.nvi.common.service.CandidateService;
import no.sikt.nva.nvi.common.service.NviPeriodService;
import no.sikt.nva.nvi.common.validator.SectorCandidateMigrationService;
import no.sikt.nva.nvi.events.batch.message.BatchJobMessage;
import no.sikt.nva.nvi.events.batch.message.MigrateCandidateMessage;
import no.sikt.nva.nvi.events.batch.message.RefreshCandidateMessage;
import no.sikt.nva.nvi.events.batch.message.RefreshPeriodMessage;
import nva.commons.core.JacocoGenerated;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class ProcessBatchJobHandler implements RequestHandler<SQSEvent, SQSBatchResponse> {

  private static final Logger LOGGER = LoggerFactory.getLogger(ProcessBatchJobHandler.class);
  private final CandidateService candidateService;
  private final CandidateMigrationService candidateMigrationService;
  private final NviPeriodService periodService;

  @JacocoGenerated
  public ProcessBatchJobHandler() {
    this(
        CandidateService.defaultCandidateService(),
        SectorCandidateMigrationService.defaultService(),
        NviPeriodService.defaultNviPeriodService());
  }

  public ProcessBatchJobHandler(
      CandidateService candidateService,
      CandidateMigrationService candidateMigrationService,
      NviPeriodService periodService) {
    this.candidateService = candidateService;
    this.candidateMigrationService = candidateMigrationService;
    this.periodService = periodService;
  }

  @Override
  public SQSBatchResponse handleRequest(SQSEvent event, Context context) {
    LOGGER.info("Processing event with {} messages", event.getRecords().size());
    var failedMessages = new ArrayList<SQSBatchResponse.BatchItemFailure>();

    for (var message : event.getRecords()) {
      try {
        var batchJobMessage = BatchJobMessage.fromJson(message.getBody());
        processMessage(batchJobMessage);
      } catch (Exception exception) {
        LOGGER.error("Failed to process message {}", message, exception);
        failedMessages.add(new SQSBatchResponse.BatchItemFailure(message.getMessageId()));
      }
    }

    LOGGER.info("Event processed with {} failures", failedMessages.size());
    return new SQSBatchResponse(failedMessages);
  }

  private void processMessage(BatchJobMessage message) {
    switch (message) {
      case RefreshCandidateMessage candidateMessage -> candidateMessage.execute(candidateService);
      case MigrateCandidateMessage candidateMessage ->
          candidateMessage.execute(candidateMigrationService);
      case RefreshPeriodMessage periodMessage -> periodMessage.execute(periodService);
    }
  }
}
