package no.sikt.nva.nvi.events.batch;

import com.amazonaws.services.lambda.runtime.Context;
import com.amazonaws.services.lambda.runtime.RequestHandler;
import com.amazonaws.services.lambda.runtime.events.SQSBatchResponse;
import com.amazonaws.services.lambda.runtime.events.SQSEvent;
import com.fasterxml.jackson.core.JsonProcessingException;
import java.net.URI;
import java.time.Instant;
import java.util.ArrayList;
import java.util.UUID;
import no.sikt.nva.nvi.common.S3StorageReader;
import no.sikt.nva.nvi.common.StorageReader;
import no.sikt.nva.nvi.common.client.PublicationChannelRetriever;
import no.sikt.nva.nvi.common.service.CandidateService;
import no.sikt.nva.nvi.common.service.NviPeriodService;
import no.sikt.nva.nvi.common.service.exception.CandidateNotFoundException;
import no.sikt.nva.nvi.events.batch.message.BatchJobMessage;
import no.sikt.nva.nvi.events.batch.message.CandidateJobMessage;
import no.sikt.nva.nvi.events.batch.message.RefreshPeriodMessage;
import no.sikt.nva.nvi.migration.CreatorDataMigrationService;
import no.sikt.nva.nvi.migration.PublicationChannelMigrationService;
import no.unit.nva.auth.uriretriever.UriRetriever;
import nva.commons.core.Environment;
import nva.commons.core.JacocoGenerated;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class ProcessBatchJobHandler implements RequestHandler<SQSEvent, SQSBatchResponse> {

  private static final Logger LOGGER = LoggerFactory.getLogger(ProcessBatchJobHandler.class);
  private static final String EXPANDED_RESOURCES_BUCKET = "EXPANDED_RESOURCES_BUCKET";
  private final CandidateService candidateService;
  private final NviPeriodService periodService;
  private final StorageReader<URI> storageReader;
  private final PublicationChannelRetriever channelRetriever;

  @JacocoGenerated
  public ProcessBatchJobHandler() {
    this(
        CandidateService.defaultCandidateService(),
        NviPeriodService.defaultNviPeriodService(),
        new S3StorageReader(new Environment().readEnv(EXPANDED_RESOURCES_BUCKET)),
        new PublicationChannelRetriever(new UriRetriever()));
  }

  public ProcessBatchJobHandler(
      CandidateService candidateService,
      NviPeriodService periodService,
      StorageReader<URI> storageReader,
      PublicationChannelRetriever channelRetriever) {
    this.candidateService = candidateService;
    this.periodService = periodService;
    this.storageReader = storageReader;
    this.channelRetriever = channelRetriever;
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
      case CandidateJobMessage candidateMessage -> processCandidateJob(candidateMessage);
      case RefreshPeriodMessage periodMessage -> periodMessage.execute(periodService);
    }
  }

  private void processCandidateJob(CandidateJobMessage message) {
    var identifier = message.candidateIdentifier();
    switch (message.jobType()) {
      case REFRESH_CANDIDATES -> candidateService.refreshCandidate(identifier);
      case BACKFILL_CREATOR_DATA ->
          new CreatorDataMigrationService(candidateService, storageReader)
              .migrateCandidate(identifier);
      case BACKFILL_CHANNEL_METADATA ->
          new PublicationChannelMigrationService(candidateService, storageReader, channelRetriever)
              .migrateCandidate(identifier);
      case REPORT_APPROVED_CANDIDATES -> reportCandidateIfReportable(identifier);
      case REFRESH_PERIODS ->
          throw new IllegalArgumentException("Not a candidate job type: " + message.jobType());
    }
  }

  private void reportCandidateIfReportable(UUID identifier) {
    var candidate = candidateService.getCandidateByIdentifier(identifier);
    if (candidate.isReportable()) {
      candidateService.reportCandidate(identifier, Instant.now());
    }
  }
}
