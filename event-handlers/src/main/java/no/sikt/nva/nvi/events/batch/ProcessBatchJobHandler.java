package no.sikt.nva.nvi.events.batch;

import com.amazonaws.services.lambda.runtime.Context;
import com.amazonaws.services.lambda.runtime.RequestHandler;
import com.amazonaws.services.lambda.runtime.events.SQSBatchResponse;
import com.amazonaws.services.lambda.runtime.events.SQSEvent;
import no.sikt.nva.nvi.common.CandidateMigrationService;
import no.sikt.nva.nvi.common.service.CandidateService;
import nva.commons.core.JacocoGenerated;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

// FIXME: Temporary suppression to split up PRs
@SuppressWarnings("PMD.UnusedPrivateField")
public class ProcessBatchJobHandler implements RequestHandler<SQSEvent, SQSBatchResponse> {

  private static final Logger LOGGER = LoggerFactory.getLogger(ProcessBatchJobHandler.class);
  private final CandidateService candidateService;
  private final CandidateMigrationService candidateMigrationService;

  @JacocoGenerated
  public ProcessBatchJobHandler() {
    this(
        CandidateService.defaultCandidateService(),
        CandidateMigrationService.defaultCandidateMigrationService());
  }

  public ProcessBatchJobHandler(
      CandidateService candidateService, CandidateMigrationService candidateMigrationService) {
    this.candidateService = candidateService;
    this.candidateMigrationService = candidateMigrationService;
  }

  @Override
  public SQSBatchResponse handleRequest(SQSEvent event, Context context) {
    LOGGER.info("Processing event {}", event);
    throw new UnsupportedOperationException("Not implemented yet");
  }
}
