package no.sikt.nva.nvi.events.batch;

import com.amazonaws.services.lambda.runtime.Context;
import com.amazonaws.services.lambda.runtime.RequestHandler;
import com.amazonaws.services.lambda.runtime.events.SQSBatchResponse;
import com.amazonaws.services.lambda.runtime.events.SQSEvent;
import com.fasterxml.jackson.core.JsonProcessingException;
import java.net.URI;
import java.util.ArrayList;
import no.sikt.nva.nvi.common.S3StorageReader;
import no.sikt.nva.nvi.common.StorageReader;
import no.sikt.nva.nvi.common.service.CandidateService;
import no.sikt.nva.nvi.common.service.NviPeriodService;
import no.sikt.nva.nvi.common.service.exception.CandidateNotFoundException;
import no.sikt.nva.nvi.events.batch.message.BackfillCreatorDataMessage;
import no.sikt.nva.nvi.events.batch.message.BatchJobMessage;
import no.sikt.nva.nvi.events.batch.message.RefreshCandidateMessage;
import no.sikt.nva.nvi.events.batch.message.RefreshPeriodMessage;
import no.sikt.nva.nvi.events.batch.message.ReportCandidateMessage;
import no.sikt.nva.nvi.migration.CreatorDataMigrationService;
import no.sikt.nva.nvi.migration.MigrationService;
import nva.commons.core.Environment;
import nva.commons.core.JacocoGenerated;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class ProcessBatchJobHandler implements RequestHandler<SQSEvent, SQSBatchResponse> {

  private static final Logger LOGGER = LoggerFactory.getLogger(ProcessBatchJobHandler.class);
  private static final String EXPANDED_RESOURCES_BUCKET = "EXPANDED_RESOURCES_BUCKET";
  private final CandidateService candidateService;
  private final NviPeriodService periodService;
  private final MigrationService creatorDataMigrationService;

  @JacocoGenerated
  public ProcessBatchJobHandler() {
    this(
        CandidateService.defaultCandidateService(),
        NviPeriodService.defaultNviPeriodService(),
        new S3StorageReader(new Environment().readEnv(EXPANDED_RESOURCES_BUCKET)));
  }

  public ProcessBatchJobHandler(
      CandidateService candidateService,
      NviPeriodService periodService,
      StorageReader<URI> storageReader) {
    this.candidateService = candidateService;
    this.periodService = periodService;
    this.creatorDataMigrationService =
        new CreatorDataMigrationService(candidateService, storageReader);
  }

  @Override
  public SQSBatchResponse handleRequest(SQSEvent event, Context context) {
    LOGGER.info("Processing event with {} messages", event.getRecords().size());
    var failedMessages = new ArrayList<SQSBatchResponse.BatchItemFailure>();

    for (var message : event.getRecords()) {
      try {
        var batchJobMessage = BatchJobMessage.fromJson(message.getBody());
        processMessage(batchJobMessage);
      } catch (JsonProcessingException | CandidateNotFoundException exception) {
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
      case BackfillCreatorDataMessage candidateMessage ->
          candidateMessage.execute(creatorDataMigrationService);
      case ReportCandidateMessage candidateMessage -> candidateMessage.execute(candidateService);
      case RefreshPeriodMessage periodMessage -> periodMessage.execute(periodService);
    }
  }
}
